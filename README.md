# krontv

A simple Android app to automatically turn your TV on and off based on a schedule using Bluetooth HID.

## Features

- **Simple Scheduling**: Set daily turn-on and turn-off times
- **Bluetooth HID Control**: Uses standard HID protocol to control TV power
- **Easy Connection**: Select your TV from paired Bluetooth devices
- **Persistent Schedules**: Schedules survive device reboots
- **Test Power Button**: Test the connection before setting schedules

## Download

You can download the latest debug APK from the [GitHub Actions artifacts](../../actions). Look for the most recent successful workflow run and download the `krontv-debug` artifact.

## Requirements

- Android 9.0 (API 28) or higher
- TV that supports Bluetooth HID and has Quick Start/Standby mode enabled

## Important Notes

**Your TV must be in standby mode (not fully powered off) for the turn-on schedule to work.**

Enable one of these settings on your TV:
- Samsung: "Quick Start"
- LG: "Quick Start+"
- Sony: "Fast TV Start"
- Other brands: Look for "Network Standby", "Remote Start", or similar

## Setup

1. Pair your TV with your Android device via Bluetooth (in Android Settings)
2. Open krontv app
3. Tap "Connect to TV" and select your TV from the list
4. Test the connection using "Test Power Button"
5. Set your desired turn-on and turn-off times
6. Enable the schedules you want

## How It Works

This app uses the Bluetooth HID (Human Interface Device) protocol to act as a remote control for your TV. It sends the standard HID Consumer Control "Power" command (0x30) which is the same command a physical remote would send.

The app uses Android's AlarmManager to trigger at scheduled times and sends the power command via Bluetooth.

## Permissions

- **Bluetooth**: To connect and send commands to TV
- **Exact Alarms**: To schedule power commands at precise times
- **Notifications**: For foreground service (required on Android 13+)
- **Boot Completed**: To reschedule alarms after device restart

## Building

This is a standard Android Gradle project. Open in Android Studio and build.

## License

This project reuses Bluetooth HID code from the [BtRemote](https://gitlab.com/Atharok/BtRemote) project.
