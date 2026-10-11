# venty-android

A native Android app for the Storz & Bickel Venty, for personal use. The official app is a Web Bluetooth page; this replaces it with a proper Kotlin + Jetpack Compose app.

## Status

First pass: scan and connect, live temperature / target / battery / heater state, set target, heater on/off, automatic reconnect, and a demo mode that simulates a Venty. **Not yet tested against a real device.** The BLE protocol is documented in [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Getting the APK

Every push builds in GitHub Actions. Open the latest run of the **Build** workflow and download the `venty-apk` artifact. `app-release.apk` is the smaller, optimised build; both are signed with a debug key, so allow installs from unknown sources.

To build locally: `./gradlew assembleDebug` (needs the Android SDK, e.g. from Android Studio).

## Code map

- `ble/VentyProtocol.kt`: packet encoding and decoding, unit tested in `app/src/test`
- `ble/GattLink.kt`: the BLE connection with a serial GATT operation queue
- `ble/DemoLink.kt`: a simulated Venty speaking the same packets
- `ble/VentyController.kt`: scanning, the 500 ms poll, reconnect with backoff, commands
- `ui/`: Compose screens (connect, dashboard with the temperature dial)

## Planned stack

- Kotlin, Jetpack Compose, Material 3
- Android BLE (`BluetoothLeScanner` / `BluetoothGatt`) behind a single serial GATT queue
- Kotlin coroutines + `StateFlow` for device state
- Foreground service to keep the connection alive in the background

## Planned features

- Scan and connect to the Venty, auto-reconnect
- Live current / target temperature, battery, charging state
- Heater on/off, target temperature, boost and superboost offsets
- Settings: °C/°F, brightness, vibration, eco charge modes, boost timeout
- Find my device
- Puff counter and "at temperature" notification

## Credits

Protocol details come from the official web app's JavaScript and from the open-source projects [szv99/storz-bickel-ble](https://github.com/szv99/storz-bickel-ble) and [lorek123/snb-ble](https://github.com/lorek123/snb-ble). Not affiliated with Storz & Bickel.
