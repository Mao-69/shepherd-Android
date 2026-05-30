package com.shepherd.ble

import java.util.UUID

/**
 * MOYOUNG-V2 protocol constants for the P58 (and other DaFit/Colmi watches).
 *
 * Opcodes, UUIDs and payload formats verified against Gadgetbridge's
 * MoyoungConstants.java / MoyoungPacketOut.java / MoyoungDeviceSupport.java
 * (AGPL-3.0). This is a clean reimplementation of the wire format.
 */
object Moyoung {

    private fun uuid(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    // --- Service & characteristics -------------------------------------
    val SERVICE: UUID            = uuid("feea")
    val CHAR_STEPS: UUID         = uuid("fee1") // READ/NOTIFY {steps:u24, distance:u24, calories:u24}
    val CHAR_DATA_OUT: UUID      = uuid("fee2") // WRITE  (phone -> watch)
    val CHAR_DATA_IN: UUID       = uuid("fee3") // NOTIFY (watch -> phone)
    val CCC_DESCRIPTOR: UUID     = uuid("2902")

    // Standard services we also read
    val SERVICE_BATTERY: UUID    = uuid("180f")
    val CHAR_BATTERY_LEVEL: UUID = uuid("2a19")
    val SERVICE_DEVINFO: UUID    = uuid("180a")
    val CHAR_FIRMWARE_REV: UUID  = uuid("2a26")
    val CHAR_MANUFACTURER: UUID  = uuid("2a29")
    val SERVICE_GAP: UUID        = uuid("1800")
    val CHAR_DEVICE_NAME: UUID   = uuid("2a00")

    // --- Commands (packetType byte) ------------------------------------
    const val CMD_SYNC_TIME: Byte                   = 49
    const val CMD_FIND_MY_WATCH: Byte               = 97
    const val CMD_SHUTDOWN: Byte                    = 81
    const val CMD_SEND_MESSAGE: Byte                = 65
    const val CMD_SET_MUSIC_INFO: Byte              = 68
    const val CMD_SET_MUSIC_STATE: Byte             = 123.toByte()
    const val CMD_QUERY_ALARM_CLOCK: Byte           = 33
    const val CMD_QUERY_GOAL_STEP: Byte             = 38

    // Command bytes confirmed against a working gatttool-based driver for this
    // exact firmware family (P58 / MOY-DBT5):
    //   HR    : feea 2005 6d            (cmd 0x6d, NO payload)        -> resp 0x6d {bpm}
    //   SpO2  : feea 2006 6b 00         (cmd 0x6b, 1-byte payload)    -> resp 0x6b {pct}
    //   BP    : feea 2008 69 00 00 00   (cmd 0x69, 3-byte payload)    -> resp 0x69 {status, sys, dia}
    const val CMD_TRIGGER_MEASURE_HEARTRATE: Byte     = 0x6d        // 109
    const val CMD_TRIGGER_MEASURE_BLOOD_OXYGEN: Byte  = 0x6b        // 107
    const val CMD_TRIGGER_MEASURE_BLOOD_PRESSURE: Byte = 0x69       // 105 (this firmware uses 0x69)

    // Standard BLE Heart Rate Service — this firmware actually streams usable
    // HR here, so we enable it as the primary HR source.
    val SERVICE_HEART_RATE: UUID = uuid("180d")
    val CHAR_HR_MEASUREMENT: UUID = uuid("2a37")

    const val CMD_ADVANCED_QUERY: Byte              = 0xb9.toByte()
    const val ARG_ADVANCED_STRESS_PACKET: Byte      = 0x11

    // Notification type tags (first payload byte of CMD_SEND_MESSAGE)
    const val NOTIFY_CALL: Byte         = 0
    const val NOTIFY_CALL_OFF_HOOK: Byte = -1
    const val NOTIFY_SMS: Byte          = 1
    const val NOTIFY_WECHAT: Byte       = 2
    const val NOTIFY_QQ: Byte           = 3
    const val NOTIFY_FACEBOOK: Byte     = 4
    const val NOTIFY_TWITTER: Byte      = 5
    const val NOTIFY_INSTAGRAM: Byte    = 6
    const val NOTIFY_SKYPE: Byte        = 7
    const val NOTIFY_WHATSAPP: Byte     = 8
    const val NOTIFY_LINE: Byte         = 9
    const val NOTIFY_KAKAO: Byte        = 10
    const val NOTIFY_OTHER: Byte        = 11

    // The watch keeps time hardcoded to GMT+8 internally.
    const val WATCH_TZ_OFFSET_SECONDS = 8 * 3600
}
