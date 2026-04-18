package com.krontv

// HID Constants for Bluetooth TV Remote
const val REMOTE_REPORT_ID = 0x02

// Power command: 0x30 is the HID Consumer Control "Power" usage
val POWER_COMMAND = byteArrayOf(0x30, 0x00)
val REMOTE_INPUT_NONE = byteArrayOf(0x00, 0x00)

// HID Descriptor for Consumer Control (Remote Control)
val bluetoothHidDescriptor = byteArrayOf(
    // Remote Control
    0x05.toByte(), 0x0C.toByte(),                    // Usage Page (Consumer Devices)
    0x09.toByte(), 0x01.toByte(),                    // Usage (Consumer Control)
    0xA1.toByte(), 0x01.toByte(),                    // Collection (Application)
    0x85.toByte(), REMOTE_REPORT_ID.toByte(),        //   Report ID (2)
    0x19.toByte(), 0x00.toByte(),                    //   Usage Minimum (Unassigned)
    0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(),     //   Usage Maximum (1023)
    0x75.toByte(), 0x10.toByte(),                    //   Report Size (16)
    0x95.toByte(), 0x01.toByte(),                    //   Report Count (1)
    0x15.toByte(), 0x00.toByte(),                    //   Logical Minimum (0)
    0x26.toByte(), 0xFF.toByte(), 0x03.toByte(),     //   Logical Maximum (1023)
    0x81.toByte(), 0x00.toByte(),                    //   Input (Data,Array,Absolute)
    0xC0.toByte()                                    // End Collection
)
