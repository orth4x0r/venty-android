# venty-android

A native Android app for the Storz & Bickel Venty, for personal use. The official app is a Web Bluetooth page; this replaces it with a proper Kotlin + Jetpack Compose app.

## Status

Research stage. The BLE protocol is documented in [docs/PROTOCOL.md](docs/PROTOCOL.md). No app code yet.

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
