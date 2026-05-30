package com.shepherd.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList
import java.util.Queue

/** A single GATT operation queued for serial execution. */
private sealed class GattOp {
    data class Write(val charUuid: java.util.UUID, val data: ByteArray) : GattOp()
    data class EnableNotify(val charUuid: java.util.UUID) : GattOp()
    data class Read(val charUuid: java.util.UUID) : GattOp()
    data class RequestMtu(val mtu: Int) : GattOp()
}

data class Telemetry(
    val connectionState: ConnState = ConnState.DISCONNECTED,
    val deviceName: String? = null,
    val firmware: String? = null,
    val battery: Int? = null,
    val heartRate: Int? = null,
    val spo2: Int? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Int? = null,
    val calories: Int? = null,
    val lastLog: String = ""
)

enum class ConnState { DISCONNECTED, CONNECTING, DISCOVERING, READY }

@SuppressLint("MissingPermission")
class P58Manager(private val context: Context) {

    companion object { private const val TAG = "P58Manager" }

    private var gatt: BluetoothGatt? = null
    private var mtu = 23 // until negotiated
    private val reassembler = MoyoungPacket.Reassembler()

    private val opQueue: Queue<GattOp> = LinkedList()
    private var opInFlight = false
    private var lastWriteNoResponse = false

    private val _state = MutableStateFlow(Telemetry())
    val state: StateFlow<Telemetry> = _state.asStateFlow()

    private fun log(msg: String) {
        Log.i(TAG, msg)
        _state.value = _state.value.copy(lastLog = msg)
    }

    // --- Public API ----------------------------------------------------

    fun connect(device: BluetoothDevice) {
        _state.value = Telemetry(connectionState = ConnState.CONNECTING, deviceName = device.name)
        // IMPORTANT: do NOT call createBond() here. The P58 / MOY-DBT5 firmware
        // connects as a plain unbonded LE peripheral — exactly how gatttool does it
        // on Linux (`gatttool -t public --sec-level=medium`, no pairing). Forcing
        // Android bonding makes these watches refuse or silently fail to connect.
        // We connect directly; if the watch itself ever requests encryption mid-
        // session, Android negotiates it lazily, matching sec-level=medium behavior.
        openGatt(device)
    }

    private fun openGatt(device: BluetoothDevice) {
        log("Connecting (LE, no bonding)")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else
            device.connectGatt(context, false, callback)
    }

    /**
     * Clears Android's cached GATT database for this connection. Cheap watches
     * like the P58 frequently fail to reconnect because Android serves a stale
     * service cache from a prior (possibly half-bonded) session. There's no public
     * API, so this uses the hidden BluetoothGatt.refresh() via reflection — a
     * well-known, widely-used workaround.
     */
    private fun refreshGattCache(g: BluetoothGatt) {
        runCatching {
            val m = g.javaClass.getMethod("refresh")
            m.invoke(g)
            log("GATT cache cleared")
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        opQueue.clear()
        opInFlight = false
        _state.value = Telemetry(connectionState = ConnState.DISCONNECTED)
    }

    fun sendCommand(command: Byte, payload: ByteArray = ByteArray(0)) {
        val packet = MoyoungPacket.build(mtu, command, payload)
        // Fragment to MTU-sized chunks (effective payload = mtu - 3 for ATT overhead).
        val chunk = maxOf(mtu - 3, 20)
        var pos = 0
        while (pos < packet.size) {
            val end = minOf(pos + chunk, packet.size)
            enqueue(GattOp.Write(Moyoung.CHAR_DATA_OUT, packet.copyOfRange(pos, end)))
            pos = end
        }
    }

    // Convenience wrappers
    // Trigger frames verified against the working driver:
    //   HR   = feea 2005 6d        (no payload)
    //   SpO2 = feea 2006 6b 00     (1-byte payload)
    //   BP   = feea 2008 69 000000 (3-byte payload)
    fun findWatch() = sendCommand(Moyoung.CMD_FIND_MY_WATCH)
    fun measureHeartRate(start: Boolean = true) =
        sendCommand(Moyoung.CMD_TRIGGER_MEASURE_HEARTRATE) // no payload
    fun measureSpo2(start: Boolean = true) =
        sendCommand(Moyoung.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN, byteArrayOf(0))
    fun measureBloodPressure(start: Boolean = true) =
        sendCommand(Moyoung.CMD_TRIGGER_MEASURE_BLOOD_PRESSURE, byteArrayOf(0, 0, 0))
    fun syncTime() = sendCommand(Moyoung.CMD_SYNC_TIME, MoyoungPacket.encodeSyncTime())
    fun shutdown() = sendCommand(Moyoung.CMD_SHUTDOWN, byteArrayOf(-1))

    fun sendMessage(type: Byte, sender: String, body: String) {
        // Watch splits on first ':' into sender / text; sanitize sender.
        val safeSender = sender.take(32).replace(":", ";")
        val text = "$safeSender:" + body.take(512)
        sendCommand(Moyoung.CMD_SEND_MESSAGE, MoyoungPacket.encodeMessage(type, text))
    }

    // --- GATT op queue (serial execution) ------------------------------

    @Synchronized
    private fun enqueue(op: GattOp) {
        opQueue.add(op)
        if (!opInFlight) runNext()
    }

    @Synchronized
    private fun runNext() {
        val g = gatt ?: return
        val op = opQueue.poll() ?: run { opInFlight = false; return }
        opInFlight = true
        when (op) {
            is GattOp.RequestMtu -> g.requestMtu(op.mtu)
            is GattOp.Read -> findChar(op.charUuid)?.let { g.readCharacteristic(it) } ?: opComplete()
            is GattOp.Write -> {
                val ch = findChar(op.charUuid)
                if (ch == null) { opComplete(); return }
                // The working driver uses char-write-cmd (write WITHOUT response)
                // for the vendor DATA_OUT characteristic. Match that.
                val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                lastWriteNoResponse = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(ch, op.data, writeType)
                } else {
                    @Suppress("DEPRECATION")
                    ch.writeType = writeType
                    @Suppress("DEPRECATION") run { ch.value = op.data; g.writeCharacteristic(ch) }
                }
                // Write-without-response completes immediately; don't wait for a callback
                // that may never come for NO_RESPONSE on some stacks.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ opComplete() }, 60)
            }
            is GattOp.EnableNotify -> {
                val ch = findChar(op.charUuid)
                if (ch == null) { opComplete(); return }
                g.setCharacteristicNotification(ch, true)
                val desc = ch.getDescriptor(Moyoung.CCC_DESCRIPTOR)
                if (desc == null) { opComplete(); return }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION") run {
                        desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(desc)
                    }
                }
            }
        }
    }

    @Synchronized private fun opComplete() { opInFlight = false; runNext() }

    private fun findChar(uuid: java.util.UUID): BluetoothGattCharacteristic? {
        val g = gatt ?: return null
        for (svc in g.services) svc.getCharacteristic(uuid)?.let { return it }
        return null
    }

    // --- GATT callback -------------------------------------------------

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected; settling before discovery")
                _state.value = _state.value.copy(connectionState = ConnState.DISCOVERING)
                // Cheap LE peripherals (incl. the P58) often drop service discovery if
                // it starts the instant the link comes up. A short settle delay — and
                // requesting a faster connection interval — makes discovery reliable.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    refreshGattCache(g)
                    runCatching {
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    log("Discovering services")
                    g.discoverServices()
                }, 600)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected (status $status)")
                _state.value = _state.value.copy(connectionState = ConnState.DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Service discovery failed (status $status); retrying")
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ runCatching { g.discoverServices() } }, 800)
                return
            }
            val hasMoy = g.getService(Moyoung.SERVICE)?.getCharacteristic(Moyoung.CHAR_DATA_OUT) != null
            log(if (hasMoy) "MOYOUNG service found; initializing" else "MOYOUNG service NOT found — wrong device?")
            // Init sequence mirrors the working gatttool driver: negotiate MTU,
            // enable the notify CCCDs (vendor DATA_IN, steps, standard HR, battery),
            // read device info, then send the first command (time sync).
            enqueue(GattOp.RequestMtu(247))
            enqueue(GattOp.EnableNotify(Moyoung.CHAR_DATA_IN))
            enqueue(GattOp.EnableNotify(Moyoung.CHAR_STEPS))
            enqueue(GattOp.EnableNotify(Moyoung.CHAR_HR_MEASUREMENT)) // standard HR; skipped cleanly if absent
            enqueue(GattOp.Read(Moyoung.CHAR_DEVICE_NAME))
            enqueue(GattOp.Read(Moyoung.CHAR_FIRMWARE_REV))
            enqueue(GattOp.Read(Moyoung.CHAR_BATTERY_LEVEL))
            enqueue(GattOp.EnableNotify(Moyoung.CHAR_BATTERY_LEVEL))
            sendCommand(Moyoung.CMD_SYNC_TIME, MoyoungPacket.encodeSyncTime())
            _state.value = _state.value.copy(connectionState = ConnState.READY)
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = newMtu
            log("MTU negotiated: $newMtu")
            opComplete()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            opComplete()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            // No-response writes are advanced by the posted delay; ignore the stray callback.
            if (lastWriteNoResponse) { lastWriteNoResponse = false; return }
            opComplete()
        }

        // API 33+
        override fun onCharacteristicRead(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int
        ) { handleRead(c.uuid, value); opComplete() }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") handleRead(c.uuid, c.value ?: ByteArray(0)); opComplete()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray
        ) { handleNotify(c.uuid, value) }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") handleNotify(c.uuid, c.value ?: ByteArray(0))
        }
    }

    // --- Inbound data --------------------------------------------------

    private fun handleRead(uuid: java.util.UUID, value: ByteArray) = when (uuid) {
        Moyoung.CHAR_DEVICE_NAME ->
            _state.value = _state.value.copy(deviceName = value.toString(Charsets.UTF_8).trimEnd('\u0000'))
        Moyoung.CHAR_FIRMWARE_REV ->
            _state.value = _state.value.copy(firmware = value.toString(Charsets.UTF_8).trimEnd('\u0000'))
        Moyoung.CHAR_BATTERY_LEVEL ->
            _state.value = _state.value.copy(battery = value.getOrNull(0)?.toInt()?.and(0xFF))
        else -> {}
    }

    private fun handleNotify(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            Moyoung.CHAR_BATTERY_LEVEL ->
                _state.value = _state.value.copy(battery = value.getOrNull(0)?.toInt()?.and(0xFF))
            Moyoung.CHAR_STEPS -> parseSteps(value)
            Moyoung.CHAR_HR_MEASUREMENT -> parseStandardHr(value)
            Moyoung.CHAR_DATA_IN -> {
                val complete = reassembler.put(value) ?: return
                val (cmd, payload) = MoyoungPacket.parse(complete) ?: return
                dispatch(cmd, payload)
            }
        }
    }

    /** Standard BLE Heart Rate Measurement (0x2a37): flags byte + uint8/uint16 BPM. */
    private fun parseStandardHr(data: ByteArray) {
        if (data.isEmpty()) return
        val flags = data[0].toInt()
        val bpm = if (flags and 0x01 != 0 && data.size >= 3)
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        else data.getOrNull(1)?.toInt()?.and(0xFF) ?: return
        if (bpm in 30..220) {
            _state.value = _state.value.copy(heartRate = bpm)
            log("Heart rate (HR service): $bpm BPM")
        }
    }

    /** Live steps characteristic: {steps:u24, distance:u24, calories:u24} LE (9 bytes). */
    private fun parseSteps(data: ByteArray) {
        if (data.size != 9) { log("steps payload != 9 bytes (${data.size})"); return }
        val steps = MoyoungPacket.u24le(data, 0)
        val distance = MoyoungPacket.u24le(data, 3)
        val calories = MoyoungPacket.u24le(data, 6)
        _state.value = _state.value.copy(steps = steps, distanceMeters = distance, calories = calories)
        log("steps=$steps distance=${distance}m cal=$calories")
    }

    /** Dispatch DATA_IN responses by command byte (verified against working driver). */
    private fun dispatch(cmd: Byte, payload: ByteArray) {
        when (cmd) {
            Moyoung.CMD_TRIGGER_MEASURE_HEARTRATE -> { // 0x6d
                val bpm = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return
                if (bpm in 30..220) {
                    _state.value = _state.value.copy(heartRate = bpm)
                    log("Heart rate: $bpm BPM")
                }
            }
            Moyoung.CMD_TRIGGER_MEASURE_BLOOD_OXYGEN -> { // 0x6b
                val pct = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: return
                if (pct in 50..100) {
                    _state.value = _state.value.copy(spo2 = pct)
                    log("SpO2: $pct%")
                }
            }
            Moyoung.CMD_TRIGGER_MEASURE_BLOOD_PRESSURE -> { // 0x69 -> {status, sys, dia}
                if (payload.size < 3) return
                val sys = payload[1].toInt() and 0xFF
                val dia = payload[2].toInt() and 0xFF
                if (sys in 60..250 && dia in 30..160) {
                    _state.value = _state.value.copy(systolic = sys, diastolic = dia)
                    log("Blood pressure: $sys/$dia")
                }
            }
            else -> log("Unhandled cmd 0x${(cmd.toInt() and 0xFF).toString(16)} (${payload.size}B)")
        }
    }
}
