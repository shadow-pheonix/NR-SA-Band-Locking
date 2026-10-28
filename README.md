# NR SA Band Locking — SA Band Lab

An open-source Android app for selecting **5G Standalone (SA) bands** on rooted
Qualcomm phones. It reads live modem preferences, saves the original values and
applies selected SA bands and an optional NR-only mode until reboot, with an
optional automatic reapply after reboot.

**Tested on a rooted Xiaomi 13 Pro (Snapdragon 8 Gen 2).** Other devices are
experimental; sharing the same SoC does not guarantee compatibility.

For development or a fresh agent session, read [context.md](context.md) and
[AGENTS.md](AGENTS.md) first. The context records the project history, current
phone evidence, implementation and remaining work; keep it updated at each
meaningful checkpoint.

## Download the APK

**[Download SA Band Lab 0.9 — ARM64 APK](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/download/v0.9.0/sa-band-lab-0.9-arm64.apk)**

[Release notes and checksums](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.9.0)
· [All releases](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases)

Download the `.apk` file to install the app. GitHub's **Source code (zip)** is for
developers and cannot be installed as an Android app.

### Version 0.9: reboot fix and optional reapply

Version 0.9 fixes an Apply failure after reboot: it uses freshly read
modem preferences instead of an obsolete saved n78 mask. The original backup
stays unchanged, and the helper still refuses a change if preferences move
between the fresh read and its own check.

It also adds optional **Reapply after reboot**, off by default. This feature is
covered by host-side transaction tests; boot delivery, background root access
and reapplication still need testing on the phone. This is an experimental
prerelease. A user-supplied 0.9 phone report confirms combined n78-only SA and
NR-only application with modem readback; that report had reboot reapply off.

To use reboot reapply:

1. Read preferences, select bands and choose **NR only** as usual.
2. Tick **Reapply after reboot**, then use the same **Apply SA bands until
   reboot** button. Only a successful, verified Apply saves a reboot plan.
3. Keep root permanently granted to the app. Allow the app to start and run in
   the background in your phone's app settings if needed.
4. After reboot, unlock the phone once. The app requests a background job after
   at least 30 seconds; Android may delay it. It reads the modem again, reapplies
   the saved bands and radio mode together, and verifies them. This is a
   temporary setting reapplied by the app, not a permanent modem-file change.
5. Open the app and read preferences to see **Saved reboot reapply** and the
   last boot attempt. Check the serving cell separately.

For a temporary lock, leave the option unticked and Apply. To cancel an existing
boot plan without changing the current lock, tap **Disable reboot reapply**.
**Restore captured SA preferences** also cancels that slot's boot plan. Each
plan belongs to its **SIM slot**, so disable it before changing the SIM in that
slot. Firmware/device mismatch disables automatic reapply. Root denial,
force-stopping the app or background restrictions can prevent it from running.
Backup imports do not enable automatic reapply.

## What the app does

- Reads separate live SA/NSA preferences and hardware-supported NR bands.
- Saves a device- and firmware-bound backup before changing preferences.
- Offers individual band checkboxes plus **Select all / Deselect all**.
- Stages the **NR only (5G)** checkbox alongside the band selection.
- Applies both choices with **Apply SA bands until reboot**, in one modem SET.
- Optionally reapplies the verified bands and radio mode after boot and unlock.
- Verifies preference readback and attempts rollback if verification fails.
- Restores the exact captured SA mask, without guessing factory defaults.
- Displays serving and nearby 5G cells when Android exposes them.

The application focuses on **SA**. It reads NSA and LTE preferences to check that
they stay unchanged; it does not offer NSA band locking. Band selection does not
add unsupported bands, force carrier aggregation or combine two SIMs' speeds.

## Tested device and current status

| Item | Tested configuration / evidence |
| --- | --- |
| Phone | **Xiaomi 13 Pro**, model **2210132G** |
| SoC / modem | **Snapdragon 8 Gen 2 / Snapdragon X70** |
| Android | **Android 16**, HyperOS **OS3.0.304.0.WMBINXM** |
| Root | Available and granted to the app; required for modem access |
| Baseband | MPSS.DE.3.0-GLB-855fda3454 |
| Network | Jio 5G SA; registered **n78** observed |
| Confirmed on phone | QMI NAS/DMS reads, capability detection, backup capture and serving-cell observations |
| User-reported working | SA band selection / locking |
| Version 0.8 combined Apply | Implemented and mock-tested; a phone test of this exact combined band + mode operation has not yet been reported |
| Version 0.8 post-reboot Apply | User reported `stale_snapshot`: modem returned to original bands while the app retained the previous n78 expectation. Fixed in 0.9; subsequent manual Apply from the broad original state succeeded on the phone. |
| Version 0.9 manual Apply | Phone report confirms one combined SET and verified n78-only SA plus NR-only mode; original backup and NSA preference preserved. Serving-cell refresh was not included. |
| Version 0.9 reboot reapply | Built and host-tested; Android boot delivery and unattended root access need phone validation. |
| Neighbor discovery | Android returned only the serving cell; the additional modem query was rejected as **DeviceUnsupported (0x0019)** |

Successful preference readback proves that the modem reports the requested
preference. It does not independently prove RF enforcement, registration on every
selected band, voice availability or behavior after a reboot. Re-read after a
reboot. Settings use a temporary modem lifetime. With **Reapply after reboot**
enabled, the app requests those settings again after boot and unlock.

## Devices that might work

These are **candidates for testing, not a supported-device list**. The inference
is based on Qualcomm NR-SA modem families and the QMI protocol, not tests of the
listed SoCs. Root, OEM firmware and modem service access matter as much as SoC.

| Qualcomm SoC family | Status |
| --- | --- |
| **Snapdragon 8 Gen 2** | Best starting point: the Xiaomi 13 Pro above was tested. Other 8 Gen 2 phones remain untested. |
| **Snapdragon 8 Gen 1 / 8+ Gen 1** | Potential candidates; compatible separate SA/NSA preference fields must be present. |
| **Snapdragon 8 Gen 3** | Potential candidate; newer firmware may expose different services or restrictions. |
| **Snapdragon 888 / 888+** | Experimental older candidates; exact masks and capability fields may be absent. |
| **Snapdragon 865 / 865+ / 870** | Experimental older candidates with NR-SA-capable modem configurations; no compatibility claim. |
| Other Qualcomm 5G Snapdragon devices | Require investigation; not automatically supported by being Snapdragon-based. |

For the current backend to work, the phone needs:

1. **Android 11 or later** and **ARM64 (`arm64-v8a`)** support.
2. Working **root access** through `su`.
3. An NR-SA-capable **Qualcomm modem** and usable **QRTR** transport.
4. Accessible QMI NAS service 3 and DMS service 2, version 1 / instance 0.
5. Separate exact 64-byte SA and NSA preference masks and a valid NR capability
   list. Missing values disable Apply instead of being guessed.

Samsung, Xiaomi, OnePlus and other brand names alone do not determine support.
For example, an Exynos Galaxy variant and a Snapdragon Galaxy variant need
different consideration. Some Snapdragon phones remain unusable because their
firmware or access policy blocks the required modem services.

SoC/modem family references: [Qualcomm 8 Gen 2](https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-2-mobile-platform),
[8 Gen 1](https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/snapdragon-8-gen-1-mobile-platform-product-brief.pdf),
[8 Gen 3](https://www.qualcomm.com/smartphones/products/8-series/snapdragon-8-gen-3-mobile-platform),
[888](https://www.qualcomm.com/smartphones/products/8-series/snapdragon-888-5g-mobile-platform),
[Qualcomm product catalogue](https://www.qualcomm.com/smartphones/products).
These sources describe silicon, not compatibility with this app.

## Devices that will not work with the current band-control backend

| Device / condition | Reason |
| --- | --- |
| **Unrooted phones**, or root denied to this app | The root helper cannot access the modem. Android-only cell observations may still work. |
| **MediaTek Dimensity / Helio**, **Samsung Exynos**, **Google Tensor**, **HiSilicon Kirin**, **Unisoc** modem platforms | No backend for these modem interfaces is implemented. |
| iPhone / iPad | This is an Android application. |
| Android 10 or earlier | The APK requires Android 11 / API 30 or later. |
| 32-bit-only ARM or x86 Android devices | The APK contains only an ARM64 native helper. |
| LTE-only phones or devices without usable 5G SA | There is no applicable SA connection to control. |
| Qualcomm devices where QRTR/NAS/DMS access or required fields are missing | The current helper cannot perform a verified apply. Root alone is not sufficient. |

The app does not root a phone or unlock its bootloader. It also cannot make an
unsupported RF band work; for example, the tested Xiaomi 13 Pro reports n28 and
n78 support but does not report n258 support.

## Beginner guide: install and read first

1. Open the **APK download link above** on your phone and download the `.apk`.
2. Open the download. If Android asks, allow **Install unknown apps** for the
   browser or file manager you used. Install **SA Band Lab**.
3. If updating an existing installation, **install over it** so its backups stay
   available. Official APK updates use the same signing certificate.
4. Open the app and choose the SIM containing your 5G SA subscription. **SIM 1 ·
   slot 0** means the first SIM slot; SIM 2 is slot 1.
5. Tap **Read live SA preferences + backup**. When your root manager prompts,
   grant root to SA Band Lab. This read does not change band preferences.
6. Wait for the successful read. Tap **Export backups** and save the JSON file
   somewhere you can find it later. Keep it on the same device/firmware.

If reading fails, stop at that stage and use **Copy diagnostic report** to report
the failure. Do not replace a missing backup with values from another phone.

### Select bands and apply NR-only together

1. Ensure the chosen SIM has mobile data enabled and usable 5G SA coverage.
2. Choose bands from the checkboxes. Use **Deselect all bands**, then tick the
   band(s) you want, such as n78 when you know n78 is available locally.
3. Leave **NR only (5G)** unticked to keep the current non-NR-only mode. Tick it
   if you want to request 5G only, with no LTE fallback.
4. Leave **Reapply after reboot** unticked for a temporary lock, or tick it to
   save the verified selection for boot-time reapply. Tap **Apply SA bands until
   reboot**, review the confirmation and tap Apply.
   **Ticking or unticking NR only does nothing until this Apply button is used.**
5. Wait for verification. The app checks both the SA mask and radio mode and
   compares other guarded preferences. Copy the report if anything fails.

At least one band must be selected. **Select all** means all currently selectable
bands, not factory defaults. Checkboxes show bands supported by the phone and
allowed by the original captured mask; they do not prove local network coverage.
NR-only removes LTE fallback, so calls and service depend on network support.

### Check the current band

Tap **Refresh detected 5G bands**. Grant **Phone** and **Precise location**
permissions and enable the phone's Location service if prompted.

- **Serving** identifies a registered cell reported by Android.
- **Nearby · not registered** identifies an additional cell Android exposes.
- **n78** is the band number; **NR-ARFCN** is a channel number.
- **SS-RSRP** is signal power in dBm; a less-negative value is generally stronger.
- Check the observation's age and whether the app used cached data.

**Not observed does not mean unavailable.** The tested firmware returned only
the serving n78 cell. The experimental **Read modem neighbor frequencies · root**
query also failed on this phone with **DeviceUnsupported (0x0019)**. This release
does not provide an exhaustive scan of all nearby 5G bands.

### Undo changes

- To leave NR-only mode: **untick NR only**, choose the desired bands and press
  **Apply SA bands until reboot**. The app restores the earlier radio mode it
  captured. If no earlier mode is known, it refuses to guess one.
- To restore the original SA band mask: tap **Restore captured SA preferences**.
  This disables reboot reapply for that SIM slot and keeps the current radio
  mode; it does not apply a pending checkbox choice.
- Before uninstalling, restore the settings you want and retain your exported
  backup. Import only your own backup from the same device and firmware.
- If a change loses service, use **Disable reboot reapply**, then restore the
  captured settings if possible. A reboot ends the requested temporary lifetime,
  but an enabled boot plan can request the lock again after unlock. This is not
  a factory reset or a guarantee against an OEM reapplying other preferences.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| Root prompt is denied or helper does not run | Check the app's permission in your root manager. |
| Apply is disabled | Read preferences successfully first. Valid backup and hardware capabilities are required. |
| No nearby bands appear | The firmware may expose only the serving cell. Supported bands are not a coverage scan. |
| `DeviceUnsupported (0x0019)` on neighbor read | The tested firmware rejects that specific optional query. It does not imply SA band selection is unsupported. |
| EFS `nr_band_pref: errno 2` | The optional old EFS path is absent. Use the main live QMI read; no replacement file is created. |
| `stale_snapshot` or `stale_mode` | Read again. Another app, the OS or the modem may have changed preferences. |
| Rollback cannot be verified | Keep the backup and report. Re-read live state before making another change. |
| Android refuses an update | Verify it is an official APK signed by the same key. A self-built APK uses a different key unless you supply your own matching keystore. |

## Build, tests and contributing

See **[Build from source](docs/BUILDING.md)** for the Android SDK, JDK 17 and
Zig 0.13.0 setup. Source is provided for the app and root helper; signing keys are
not included.

Host checks cover **48 DIAG/QMI mock test methods**, native framing/deadline
checks and Java observation/selection checks. They verify protocol behavior and
failure recovery, not compatibility with every phone.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a device report. Remove
device-binding identifiers and private network data from diagnostic reports.

The current band control uses an independently implemented **QMI NAS/DMS** client
over QRTR. Cellular Pro research informed an earlier EFS experiment; the working
backend uses public QMI protocol research. See [protocol details](docs/PROTOCOL.md).

## License

Original project code is **[MIT licensed](LICENSE)**. The vendored qcom-efs
transport/utilities remain **BSD-3-Clause**, with notices preserved. The APK also
includes third-party and static-runtime notices. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
