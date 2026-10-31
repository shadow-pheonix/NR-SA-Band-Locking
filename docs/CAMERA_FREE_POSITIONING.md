# Camera-free indoor positioning: feasibility research

Researched **2026-10-31** for a rooted Xiaomi 13 Pro. This is research, not an
implemented minimap, measured phone performance or a new APK release.

The user wants automatic strongest/weakest RF records, saved room-scale maps
and guidance back to the original phone position after reopening a session.
Camera-free methods exist. The earlier suggestion that a camera was necessary
was too broad: acoustic sensing and installed ranging systems are alternatives.
No reviewed result establishes exact, repeatable 3D hand-position recovery on
this phone without setup or external references.

## Findings from primary sources

| Method | Evidence and setup | Relevance to this app |
| --- | --- | --- |
| Learned inertial odometry | [TLIO](https://cathias.github.io/TLIO/) combines learned displacement/uncertainty with an EKF estimating biases. Its authors show failures for motion unlike training. | Useful motion prediction, not an absolute saved-room reference. Hand movements and shuffling need validation. |
| Neural pedestrian tracking | [RoNIN](https://ronin.cs.sfu.ca/) benchmarks walking trajectories; its target is the body rather than the freely handled phone. Data/pretrained models are marked research-only. | Do not assume its reported pedestrian accuracy measures the held phone's antenna position, or bundle models under this app's MIT license without resolving usage terms. |
| Acoustic place recognition | [EchoTag](https://www.sigmobile.org/mobicom/2015/papers/p525-tungA.pdf) reports 98% classification of 11 trained tags separated by 1 cm. Its home-environment result degraded to 56% after a week without adaptation. | Recognising previously trained spots is not continuous metric navigation or a universal 1 cm positioning guarantee. Nearby objects, holding conditions and map age matter. |
| Acoustic/inertial SLAM | [ELF-SLAM](https://arxiv.org/html/2210.08493v1) uses echoes and IMU. Living-room one-shot error: 0.10 m median, 0.29 m mean; office median 0.54 m. It needs environment-specific learning; mapping uses a server. Orientation/device changes affect results. | Promising phone-only sensing candidate; reported mostly horizontal navigation does not validate exact height/pose recovery. Not yet reproduced on Xiaomi 13 Pro. |
| Newer acoustic SLAM | [ZA-SLAM, MobiSys Companion 2026](https://scholars.cityu.edu.hk/en/publications/demo-zero-shot-acoustic-slam-on-smartphones/) demonstrates unseen-environment operation: mean mapping error 0.64 m, localisation 0.80 m, with server-side processing. | Removes some calibration burden but is a research demonstration, not evidence of centimetre return precision or a ready offline Android component. |
| Recent acoustic landmark/PDR fusion | [Wang and Ma, 2026](https://www.frontiersin.org/journals/computer-science/articles/10.3389/fcomp.2026.1834280/full) explicitly reports posture/obstacle limitations, substantial computation and lack of real-time navigation. | A new paper or sophisticated model alone does not establish an implementable live minimap. |
| Wi-Fi RTT/FTM | [Android Wi-Fi RTT](https://developer.android.com/develop/connectivity/wifi/wifi-rtt) describes typically 1–2 m positioning using at least three suitable APs. Requires supported device/firmware and responders with known positions. Android 15+ can expose 802.11az if hardware supports it. | Worth a capability check, not an exact-spot solution by default. Wi-Fi version, Android version and root do not prove ranging support. Ranging does not require joining the AP or using Wi-Fi for internet. |
| UWB ranging | [Android UWB](https://developer.android.com/develop/connectivity/uwb) describes approximately 10 cm distance measurement and requires UWB hardware. [Qorvo's evaluation kit](https://www.qorvo.com/products/ek/DWM3001CDK) supports constructing a ranging/location system. | Strong candidate with installed anchors and a supported phone or external tag. Distance accuracy is not automatically 3D position accuracy; anchor geometry/obstructions matter. |
| Bluetooth fine ranging | [Bluetooth SIG](https://www.bluetooth.com/learn-about-bluetooth/feature-enhancements/channel-sounding/) describes early Channel Sounding implementations around ±20 cm. [Core 6.0 overview](https://www.bluetooth.com/core-specification-6-feature-overview/) identifies the new optional feature. | Both peers need compatible hardware/software; ordinary BLE RSSI cannot substitute. Version labels alone do not establish optional-feature support. |

## Xiaomi 13 Pro: known versus unverified

[Xiaomi's specifications](https://www.mi.com/global/product/xiaomi-13-pro/specs/)
list accelerometer, gyro, compass, barometer, FastConnect 7800 and Bluetooth 5.3.
They do not establish UWB or RTT API availability. Do not infer Channel Sounding
or UWB support from Snapdragon 8 Gen 2. Runtime capability checks on the actual
HyperOS build are still needed. An external UWB tag could send its measured
positions to the app, avoiding a requirement for built-in phone UWB; mounting
offset and orientation would then need calibration. This is a proposed design.

Wi-Fi CSI is another research route, but root alone does not expose it.
[Nexmon CSI's supported chip list](https://github.com/seemoo-lab/nexmon_csi)
targets specific Broadcom/Cypress devices, not an established solution for the
Qualcomm connectivity system here. No compatible extractor was verified.

## What sensor fusion can and cannot add

An EKF, particle filter or factor graph can combine real observations and
estimate uncertainty. It cannot supply information absent from its inputs.
For example, an uncompensated acceleration bias of 0.01 m/s² produces a
0.5 m position error after 10 s of double integration: `error = bias × t² / 2`.
This is an illustrative calculation, not a measured bias of this phone.

- Accelerometer/gyro: motion and rotation, stationary checks and bias updates.
  Being stationary can constrain velocity; it does not reveal room coordinates.
- Compass: orientation/context; map local magnetic signatures rather than
  blindly trusting disturbed indoor north readings.
- Barometer: relative height context, not horizontal position or an exact hand
  height. [Android's altitude helper](https://developer.android.com/reference/android/hardware/SensorManager#getAltitude(float,%20float))
  depends on reference pressure.
- Acoustic echoes: an environmental observation that may recognise revisited
  places and correct drift. Need microphone access and measured audio behavior.
- Wi-Fi/magnetic fingerprints: possible coarse context or relocalisation aids.
  Do not infer centimetre position from signal-strength averaging.

Saved coordinates alone are insufficient. A reopened session must recognise an
environmental map, range against fixed anchors, or have the user return to a
known physical origin and alignment. Without one of these, a fresh inertial
trajectory cannot determine where it lies in yesterday's coordinate system.

## Recommended experiment before a minimap implementation

The following is engineering judgment, not a reproduced research result.

1. **Define success:** separately test 2D target return, height, phone orientation,
   false recognition and performance after restarting the app. A smooth map is
   not a positioning benchmark. Ask for a tolerance before promising accuracy.
2. **Probe capabilities:** enumerate actual sensors/rates, RTT/UWB features and
   raw audio support. Check RTT responders if available; make no modem changes.
3. **Test the audio path:** low-volume, bounded foreground chirps; measure
   speaker/microphone response, clipping, synchronisation and repeatability.
   [Android audio guidance](https://developer.android.com/media/platform/mediarecorder)
   warns that unprocessed capture is not universally supported. Disable the
   existing hunt sound while sampling so it cannot contaminate measurements.
   Do not call high-frequency chirps universally inaudible or force full volume.
4. **Use a measured room grid:** record known positions and phone poses, including
   small offsets near a target. Repeat with different grips, days, door/furniture
   states and app restarts. Keep overlapping windows from one recording/visit
   in the same partition; reserve separate later visits and days for testing.
   Known target spots must be revisited in the test set to measure recognition;
   holding out an entire target is a different, unseen-location experiment.
5. **Start with recognition:** test whether an echo fingerprint can reliably
   distinguish a saved target from its neighbours. Include unknown locations and
   measure false accepts. Similarity scores are not calibrated probabilities or
   distance. A recogniser alone cannot tell the user which direction to walk.
6. **Only then add mapping:** combine validated echo constraints with inertial
   estimates, loop-closure rejection, persistent reference frames and uncertainty.
   If this fails the return tolerance, present approximate guidance or move to
   anchors. Do not label a simple echo correlator as a reproduction of ELF-SLAM.
7. **Protect RF association:** timestamp match sensor/position observations to
   actual modem sample times. Avoid tagging the later position where the phone
   arrived while an old RF report was delivered. Keep bands/cell context and
   missing-position states with every strongest/weakest record.

Raw recordings may include surrounding speech: prototype collection must be
explicit, local and bounded, with deletion and no diagnostic/audio uploads.
Existing research servers are not an implied permission to send user data away.

## Code availability and delivery status

[LibAcousticSensing](https://github.com/yctung/LibAcousticSensing) is an MIT-licensed
research framework with platform examples, not a ready indoor positioning SDK.
Its main-branch documentation distinguishes remote and standalone development.
No verified production-ready ELF/ZA-SLAM Android package or model license was
found in this research. Search failure is not proof that code does not exist.

No app code, manifest permissions, SDKs, APKs or releases were changed. The next
useful implementation is an opt-in sensor/audio feasibility test, not an app
claiming exact room positioning. Reliable fine positioning with external
anchors is a separate hardware-supported option; no equipment purchase is
recommended before compatibility and actual return accuracy are checked.
