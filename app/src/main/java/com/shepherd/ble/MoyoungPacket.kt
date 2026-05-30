package com.shepherd.ble

import java.util.Date

/**
 * Frame format (verified against MoyoungPacketOut.buildPacket):
 *
 *   packet[0] = 0xFE
 *   packet[1] = 0xEA
 *   packet[2] = (mtu == 20) ? 16 : (0x20 + ((len >> 8) & 0xFF))   // len = payload.length + 5
 *   packet[3] = len & 0xFF
 *   packet[4] = command
 *   packet[5:] = payload
 *
 * For the P58 the negotiated ATT MTU is 247, so we are always on the
 * non-20 branch and packet[2] is 0x20 for any payload < 251 bytes.
 */
object MoyoungPacket {

    fun build(mtu: Int, command: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val len = payload.size + 5
        val packet = ByteArray(len)
        packet[0] = 0xFE.toByte()
        packet[1] = 0xEA.toByte()
        packet[2] = if (mtu == 20) 16 else (0x20 + ((len shr 8) and 0xFF)).toByte()
        packet[3] = (len and 0xFF).toByte()
        packet[4] = command
        System.arraycopy(payload, 0, packet, 5, payload.size)
        return packet
    }

    /** Returns Pair(command, payload) or null if header/length invalid. */
    fun parse(packet: ByteArray): Pair<Byte, ByteArray>? {
        if (packet.size < 5) return null
        if (packet[0] != 0xFE.toByte() || packet[1] != 0xEA.toByte()) return null
        val command = packet[4]
        val payload = packet.copyOfRange(5, packet.size)
        return command to payload
    }

    private fun expectedLength(header: ByteArray): Int {
        if (header.size < 4) return -1
        if (header[0] != 0xFE.toByte() || header[1] != 0xEA.toByte()) return -1
        var lenH = 0
        if (header[2] != 16.toByte()) {
            val b2 = header[2].toInt() and 0xFF
            if (b2 < 32) return -1
            lenH = b2 - 32
        }
        val lenL = header[3].toInt() and 0xFF
        return (lenH shl 8) or lenL
    }

    /**
     * Reassembles fragmented notifications arriving on DATA_IN.
     * Many short responses arrive in a single notification; longer ones span several.
     */
    class Reassembler {
        private var buffer: ByteArray? = null
        private var expected = -1
        private var filled = 0

        /** Feed one notification fragment; returns a complete packet when ready, else null. */
        fun put(fragment: ByteArray): ByteArray? {
            if (buffer == null) {
                expected = expectedLength(fragment)
                if (expected <= 0) return null
                buffer = ByteArray(expected)
                filled = 0
            }
            val buf = buffer!!
            val toCopy = minOf(fragment.size, buf.size - filled)
            System.arraycopy(fragment, 0, buf, filled, toCopy)
            filled += fragment.size
            return if (filled >= expected) {
                val out = buf
                buffer = null; expected = -1; filled = 0
                out
            } else null
        }
    }

    // --- Payload encoders (verified against MoyoungDeviceSupport) -------

    /** CMD_SYNC_TIME: {watchTime:int32 BE, 0x08}. Watch is hardcoded GMT+8. */
    fun encodeSyncTime(now: Date = Date()): ByteArray {
        val watchTime = localToWatchTime(now)
        return byteArrayOf(
            (watchTime ushr 24 and 0xFF).toByte(),
            (watchTime ushr 16 and 0xFF).toByte(),
            (watchTime ushr 8 and 0xFF).toByte(),
            (watchTime and 0xFF).toByte(),
            8
        )
    }

    /**
     * Reinterprets local wall-clock time as if it were in GMT+8, matching
     * MoyoungConstants.LocalTimeToWatchTime. i.e. take the local broken-down
     * time and emit the epoch seconds it would correspond to in GMT+8.
     */
    private fun localToWatchTime(date: Date): Int {
        val localOffsetSec = java.util.TimeZone.getDefault().getOffset(date.time) / 1000
        val utcSeconds = date.time / 1000
        // local wall clock (seconds) = utc + localOffset; reinterpret in GMT+8:
        val wallClock = utcSeconds + localOffsetSec
        return (wallClock - Moyoung.WATCH_TZ_OFFSET_SECONDS).toInt()
    }

    /** CMD_SEND_MESSAGE: {type, utf8 text...}. Caller pre-formats "sender:body". */
    fun encodeMessage(type: Byte, text: String): ByteArray {
        val str = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(str.size + 1)
        payload[0] = type
        System.arraycopy(str, 0, payload, 1, str.size)
        return payload
    }

    fun u24le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16)
}
