# Venty and Volcano BLE protocol

How the Storz & Bickel Venty talks over Bluetooth Low Energy, written as a reference for the Android client.

**Status:** compiled 2026-10-11 from the official web app's JavaScript and two open-source libraries (sources at the bottom). Nothing here has been tested against a physical Venty by this project yet. Items marked **unverified** need checking on the device.

The Venty and the Veazy share one protocol. The vendor code calls it "qvap".

---

## 1. Summary

- One custom GATT service with **one characteristic**, used for both requests (write) and responses (notify).
- Every request is a small binary packet. **Byte 0 is the command.** The device answers with a notification whose byte 0 is the same command.
- There is **no pairing, bonding, auth or handshake** for normal control. The official web app connects through Web Bluetooth, which cannot bond, and starts writing right away. Encryption exists only inside the firmware-update flow, which is out of scope.
- The device does not push status on its own. The app **polls** with a status request every 500 ms.
- Multi-byte integers are **little-endian**. Temperatures are **tenths of a degree Celsius** (185.0 °C = `1850` = `3A 07`).

## 2. Discovery

| What | Value |
|---|---|
| Advertised name | `S&B VY…` for Venty (e.g. `S&B VY123456`), `S&B VZ…` for Veazy |
| Scan filter | service UUID `00000000-5354-4f52-5a26-4249434b454c` |

The vendor app scans with a service filter on the qvap service, then decides the model after connecting: a name containing `S&B VY` is a Venty, `S&B VZ` is a Veazy. Any other `S&B` / `STORZ&BICKEL` / `Storz&Bickel` name is a Volcano or Crafty-family device with a different protocol.

The suffix after the space in the name is the serial number, which works as a fallback when command `0x05` is not answered.

## 3. GATT layout

| Role | UUID | Properties |
|---|---|---|
| Qvap service | `00000000-5354-4f52-5a26-4249434b454c` | |
| **Control characteristic** | `00000001-5354-4f52-5a26-4249434b454c` | write, notify |
| Generic Access service | `00001800-0000-1000-8000-00805f9b34fb` | |
| Device Name | `00002a00-0000-1000-8000-00805f9b34fb` | read (contains serial) |

The UUIDs are the ASCII string `STORZ&BICKEL` packed into the low bytes (`53 54 4f 52 5a 26 42 49 43 4b 45 4c`).

> One library (lorek123/snb-ble) lists the service as `00000001-…`. The official app's `main.js` defines `serviceUuidQvap = "00000000-5354-4f52-5a26-4249434b454c"`, so the service is `…0000…` and only the characteristic is `…0001…`.

## 4. Connection sequence

What the official app does (`qvap.js`, `QvapConnect` → `QvapConnect2` → `QvapConnect3`):

1. Connect GATT, get the qvap service, get the control characteristic.
2. Enable notifications on it (on Android, write `01 00` to the CCCD descriptor `0x2902`).
3. Read Generic Access Device Name for the serial. Failure is tolerated.
4. Send, in order: `0x02` (firmware), `0x1D` (find-my state), `0x01` (status), `0x04` (usage), `0x23` (analysis/key info when the application firmware is running, otherwise `0x03`), `0x05` (identity), `0x06` (settings).
5. Start polling: every 500 ms send `0x01` with byte 1 = `0`. Every ~31 ticks (~15 s) send `0x04` instead.

The app also sends one write with byte 1 = `0x06` near the end of the sequence. Its purpose is unclear (**unverified**). For cmd `0x01`, byte 1 = `0x06` would be the temperature + boost write mask, so a client should **not** copy this blindly. Plain polling as in step 5 is enough; szv99/storz-bickel-ble works this way.

The app has no automatic reconnect; it shows an error and asks for a reload. The Android client should reconnect itself.

Request packets: the app sends 20-byte zero-filled packets for most commands and 7-byte packets for cmd `0x06`. Unused bytes are zero.

## 5. Commands

| Cmd | Name | Direction |
|---|---|---|
| `0x01` | Status / control | poll + write |
| `0x02` | Firmware version | read |
| `0x03` | Device analysis (bootloader context) | read |
| `0x04` | Usage statistics | read |
| `0x05` | Identity (serial, colour) | read |
| `0x06` | Device settings (brightness, vibration, boost timeout) | read + write |
| `0x0D` | Find my device (vibrate / flash) | write |
| `0x1D` | Find-my state | read |
| `0x23` | Analysis (application context) | read |
| `0x30` | Bootloader / firmware-update channel | out of scope |

> lorek123/snb-ble uses `0x13` for find-device. The official app sends `0x0D` (decimal 13) in `findMyQvap()`; the library looks like a decimal/hex mix-up. Use `0x0D`.

### 5.1 `0x01` status / control

**Request (poll):** `01 00 00 …` (20 bytes, byte 1 = 0 means "read only").

**Response:**

| Byte | Field | Notes |
|---|---|---|
| 0 | `0x01` | |
| 1 | mask / status | |
| 2–3 | current temperature | u16 LE, ×10 °C. `0x8000` = not available. Reportedly **always** `0x8000` on the Venty (see note below) |
| 4–5 | target temperature | u16 LE, ×10 °C. The base setpoint, without boost |
| 6 | boost offset | degrees added in boost mode, **in the device's display unit** (°F when the Fahrenheit flag is set) |
| 7 | superboost offset | degrees added on top of boost in superboost mode, same unit as byte 6 |
| 8 | battery | 0–100 % |
| 9–10 | auto-shutoff countdown | seconds. **The vendor app computes `b9 + b10`, not a u16**; its progress bar treats 120 s as 100 % |
| 11 | heater mode | 0 off, 1 on, 2 boost, 3 superboost |
| 12 | (unknown) | |
| 13 | charger connected | 0 / 1 |
| 14 | settings flags | see below |
| 15 | (settings write mask on writes) | |
| 16 | settings2 flags | bit 0 = BLE permanent (only when length ≥ 17) |
| 17 | (settings2 write mask on writes) | |

**Settings flags (byte 14):**

| Bit | Value | Meaning |
|---|---|---|
| 0 | `0x01` | unit: 1 = Fahrenheit, 0 = Celsius |
| 1 | `0x02` | setpoint reached (heater is at temperature) |
| 2 | `0x04` | factory reset (write only) |
| 3 | `0x08` | eco mode: charge optimisation (slower charging) |
| 4 | `0x10` | button changed filling chamber |
| 5 | `0x20` | eco mode: charge voltage limit (~90 % cap) |
| 6 | `0x40` | boost visualisation |

**Writes** use the same command. Byte 1 is a mask saying which fields to apply; everything else is ignored:

| Mask bit | Value | Applies |
|---|---|---|
| 1 | `0x02` | target temperature, bytes 4–5 |
| 2 | `0x04` | boost offset, byte 6 |
| 3 | `0x08` | superboost offset, byte 7 |
| 5 | `0x20` | heater mode, byte 11 |
| 7 | `0x80` | settings: byte 14 = new values, **byte 15 = which bits to change**; byte 16 = settings2 values, byte 17 = settings2 bits to change |

Examples (20-byte packets, trailing zeros omitted):

```
Set target 185.0 °C     01 02 00 00 3A 07
Set boost offset 15     01 04 00 00 00 00 0F
Set superboost off. 15  01 08 00 00 00 00 00 0F
Heater on               01 20 00 00 00 00 00 00 00 00 00 01
Heater off              01 20 00 00 00 00 00 00 00 00 00 00
Boost                   01 20 … byte 11 = 02   (unverified: the app only writes 0/1)
Superboost              01 20 … byte 11 = 03   (unverified, see below)
Fahrenheit              01 80 … byte 14 = 01, byte 15 = 01
Celsius                 01 80 … byte 14 = 00, byte 15 = 01
Eco charge on           01 80 … byte 14 = 08, byte 15 = 08
Factory reset           01 80 … byte 14 = 04, byte 15 = 04
```

Notes:

- **Target temperature is the base setpoint.** When the device is in boost or superboost, the vendor app shows target + offsets, and before writing a new target it subtracts the active offsets again. Do the same: keep the UI's "effective" temperature separate from the value written.
- **No live temperature on the Venty.** firsttris/reactive-volcano-app reports that the Venty never fills bytes 2–3 (always `0x8000`). Show the effective target with a heating / reached state (settings flag bit 1) instead of a live reading. **Unverified** by this project.
- **Boost / superboost from the app.** The official app only ever writes heater mode 0 or 1 (power toggle); boost and superboost are normally entered with the device button and then reported in byte 11. szv99/storz-bickel-ble and firsttris/reactive-volcano-app both write 2 and 3 directly, which suggests the device accepts it. Still **unverified** here.
- **Ranges.** The app does not clamp target or offsets. The Venty's published range is 40–210 °C; clamp in the client. Boost and superboost offsets reportedly range 1–99 °C (reactive-volcano-app); read them back after writing.
- **Leaving boost.** Tested on a real Venty (2026-10-11): boost can be entered by writing mode 2, but writing mode 1 (alone, or with the target) does **not** end it. The official app (v3.4.1) never writes 1 while heating; its power button writes 0 in any heating mode. Ending boost from a client therefore means writing 0 and then 1, or waiting for the boost timeout. Superboost (mode 3) is boost plus superboost: the offsets stack.
- **Boost timeout.** Boost ends after about 90 s unless "permanent boost" is on, which is the boost-timeout-disabled flag in cmd `0x06` byte 6.
- The factory-reset flow in the app also sets the setpoint to 180 °C.
- BLE permanent (settings2 bit 0) is exposed in the app for Veazy only (toggle `bluetoothVeazyVenty`). It's written as `01 80`, byte 16 = value, byte 17 = `01` (bytes 14–15 zero), and read back from byte 16 of any status packet of 17+ bytes. Whether a Venty honours it is **unverified**; this app offers it as an experimental "Keep Bluetooth on" switch.

### 5.2 `0x02` firmware

Response: byte 1 = application-firmware flags (`0x01` application running, `0x10` invalid application, `0x20` invalid bootloader); bytes 2–7 = firmware version, ASCII (e.g. `V01.11`); bytes 11–16 = bootloader version, ASCII (per reactive-volcano-app).

### 5.3 `0x04` usage statistics

Response: bytes 1–3 = heater runtime in minutes (u24 LE); bytes 4–6 = charging time in minutes (u24 LE).

### 5.4 `0x05` identity

Response: serial = ASCII bytes 15–16 (model prefix) followed by bytes 9–14. Byte 18 = colour index of the device: the official app reads it on the Veazy only (packet longer than 18 bytes) and maps 2 = blue, 3 = pink, 4 = orange, anything else = black. What a Venty sends here is **unverified**.

### 5.5 `0x06` device settings

Request (read): 7 bytes, `06 00 00 00 00 00 00`.

| Byte | Field |
|---|---|
| 1 | write mask (writes only) |
| 2 | display brightness, 1–9 (mask `0x01`) |
| 5 | vibration, 0 / 1 (mask `0x08`) |
| 6 | boost / superboost timeout **disabled**, 0 / 1 (mask `0x10`) |

```
Brightness 9         06 01 09 00 00 00 00
Vibration on         06 08 00 00 00 01 00
Boost timeout off    06 10 00 00 00 00 01
```

Re-read with a bare `0x06` after writing; the device answers with the new values.

Vibration is on/off only: the official app (checked 2026-10-13) writes byte 5 as 0 or 1 and reads it as zero / non-zero, with a single toggle in its UI and no strength setting. Masks `0x02` and `0x04` (bytes 3–4) are never used by the official app. It only offers the boost-timeout toggle on a Venty whose firmware byte 2 is 8 or higher.

### 5.6 `0x0D` find my device

reactive-volcano-app notes this may not work on the Venty (**unverified**).

Request: `0D 01` (20 bytes). The device vibrates / flashes. The `0x1D` response has bit 4 of byte 1 set while find-my is active.

### 5.7 `0x03` / `0x23` analysis

The device returns an error code (byte 1) and category (byte 2). The official app uploads a 512-byte analysis blob to Storz & Bickel for decoding on their servers. There is no local decoder; not planned for this app.

## 6. Puff detection (derived, not a protocol field)

The device has no puff counter, but one can be derived (szv99/storz-bickel-ble, verified by its author on firmware V01.11 at 500 ms polling): once the setpoint is reached, the auto-shutoff counter (bytes 9–10) counts down once per second, and **jumps back to its maximum and holds there while you inhale**. A counter increase marks a puff starting; the first decrease marks its end. Ignore increases within ~2 s of a target or mode change (button presses reset the counter too), and merge resets within ~2 s of a puff ending.

## 7. Android implementation notes

- Permissions: `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT` on API 31+; `ACCESS_FINE_LOCATION` for older versions.
- Scan with a `ScanFilter` on the qvap service UUID, then confirm the name contains `S&B VY`.
- Android's GATT stack allows **one outstanding operation at a time**. Put every write, read and descriptor write through a single serial queue, and match responses to requests by byte 0 with a timeout (~1.5 s).
- Write with response (`WRITE_TYPE_DEFAULT`) and fall back to no-response on failure, as szv99 does.
- Treat ~4 consecutive unanswered polls as a disconnect and reconnect with backoff.
- A foreground service keeps the connection alive when the app is backgrounded (needed for session notifications, e.g. "at temperature").

## 8. Volcano Hybrid

**Not yet tested on a real Volcano.** Facts from firsttris/reactive-volcano-app's Volcano driver and its unit tests (AGPL; facts only, no code borrowed). The Mighty+ has no Bluetooth at all, per Storz & Bickel's own device comparison, so it can't be supported.

Unlike the Venty, every value has its own characteristic. All values are little-endian, temperatures in tenths of °C. No pairing or auth. The device advertises as `S&B VOLCANO…`; the app matches `VOLCANO` in the name or the state service UUID in the advertisement.

| Service | UUID |
|---|---|
| State | `10100000-5354-4f52-5a26-4249434b454c` |
| Control | `10110000-5354-4f52-5a26-4249434b454c` |

Characteristics share the `-5354-4f52-5a26-4249434b454c` suffix:

| Prefix | Service | Value | Notes |
|---|---|---|---|
| `10100003` | State | Firmware | ASCII, first 8 chars |
| `10100004` | State | BLE firmware | ASCII |
| `10100008` | State | Serial number | ASCII, first 8 chars |
| `1010000c` | State | Register 1 (activity) | u16, notifies. `0x0020` heater on, `0x0200` auto-shutdown, `0x2000` pump on |
| `1010000d` | State | Register 2 (display) | u16, notifies. `0x0200` °F, `0x1000` display off while cooling |
| `1010000e` | State | Register 3 (vibration) | u16, **no notify**. `0x0400` vibration off |
| `10110001` | Control | Current temperature | u16 tenths, notifies. Junk values above ~300 °C during start-up |
| `10110003` | Control | Target temperature | reads u16, **write u32** tenths, notifies. 40–230 °C |
| `10110005` | Control | LED brightness | u16, 0–100 |
| `1011000c` | Control | Auto-off remaining | u16 seconds, notifies |
| `1011000d` | Control | Auto-off time | u16 seconds |
| `1011000f` / `10110010` | Control | Heater on / off | write one `00` byte |
| `10110013` / `10110014` | Control | Pump on / off | write one `00` byte |
| `10110015` / `10110016` | Control | Heater hours / minutes | u16, notify |

Register writes are a u32: the low 16 bits pick the bit, and `0x10000` set means "set" (clear otherwise). E.g. °F on is `00 02 01 00`, off is `00 02 00 00`.

There is no "setpoint reached" flag. The app treats the Volcano as ready once the live temperature is within 1 °C of the target, and only drops back to heating 5 °C below it, so the pump's dip doesn't flicker the state.

In the app, `VolcanoLink` accepts the same request packets the controller sends a Venty and turns them into these reads and writes, so the controller, sessions, notification and widget work unchanged. It reports a decoded `VolcanoState` instead of status packets.

## 9. Sources

- Official web app, `https://app.storz-bickel.com/js/qvap.js` and `/js/main.js` (fetched 2026-10-11). Primary source for UUIDs, command bytes, masks and packet layouts.
- [szv99/storz-bickel-ble](https://github.com/szv99/storz-bickel-ble) (MIT), `protocols/qvap.py`, `puff.py`, `discovery.py`. Matches the official app on every point checked.
- [lorek123/snb-ble](https://github.com/lorek123/snb-ble) (MIT), `venty.py`, `protocol.py`, `s&b_procol.md`. Useful, but has the service UUID and find-device byte wrong and omits the settings write mask in byte 15.
- [firsttris/reactive-volcano-app](https://github.com/firsttris/reactive-volcano-app) (AGPL; facts only, no code borrowed). Source of the no-live-temperature, offset range, boost timeout and bootloader-version notes, and of the whole Volcano Hybrid section.
- [Storz & Bickel device comparison](https://support.storz-bickel.com/hc/en-us/articles/36311732152977-Device-Comparison): app control is listed for the Volcano Hybrid, Venty, Crafty+ and Veazy only, not the Mighty+.
- [nicandris/ha-snb](https://github.com/nicandris/ha-snb), Home Assistant integration built on lorek123's library.
