package com.shepherd.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FoundDevice(val device: BluetoothDevice, val name: String, val rssi: Int)

@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

    private val _devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    val devices: StateFlow<List<FoundDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val seen = mutableMapOf<String, FoundDevice>()

    private val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val name = dev.name ?: result.scanRecord?.deviceName ?: return
            seen[dev.address] = FoundDevice(dev, name, result.rssi)
            _devices.value = seen.values.sortedByDescending { it.rssi }
        }
    }

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    fun start() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        seen.clear(); _devices.value = emptyList()
        _scanning.value = true
        // No service filter: P58 advertises name "P58" but many clones don't advertise
        // the feea service UUID, so we scan broadly and show everything by name.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, cb)
    }

    fun stop() {
        adapter?.bluetoothLeScanner?.stopScan(cb)
        _scanning.value = false
    }
}
