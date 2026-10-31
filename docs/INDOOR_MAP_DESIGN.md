# Indoor Signal survey and return guidance — implementation design

Status: design/research only, **not implemented or included in 0.15**.
The user wants room-scale recording, both new strongest and weakest session
records, persistent session maps and guidance back to the original phone pose.
The user has since requested research into accurate **camera-free** methods;
see [CAMERA_FREE_POSITIONING.md](CAMERA_FREE_POSITIONING.md). Camera/reference
tracking below is one alternative, not a mandatory prerequisite or the selected
implementation. No APK build is authorized for this extension yet.

## Accuracy boundary

The current Mark spot stores an RF reading in memory without coordinates.
GPS and motion sensors alone cannot reliably distinguish positions within a
room. Integrating acceleration introduces drift; pressure is affected by weather
and is not an absolute indoor height reference. Do not implement a GPS grid
and describe it as accurate room positioning.

One possible solution is camera-based visual/inertial tracking with a fixed,
measured visual reference. It estimates a phone pose in the reference's local
coordinate frame. This can provide useful room-scale guidance, but neither
exact return to a point nor a universal centimetre-level accuracy is promised.
ARCore tracking state is not an accuracy radius: do not fabricate a numeric
error estimate from it.

## Proposed flow

1. Create a named room survey for the selected SIM. Register a detailed,
   distinctive picture that stays fixed in the room and enter its measured
   physical width. A stationary existing poster can be used; a printed reference
   is another option. The reference file and dimension stay with the survey.
2. Start the camera and scan that reference. Verify AR availability, camera
   permission, reference detection and active tracking before recording poses.
   Other app features remain usable without AR support or camera permission.
3. Hunt slowly with the camera able to see textured surroundings. Use camera
   and IMU tracking together; retain relative height and orientation. Do not
   double-integrate accelerometer readings into an independent position.
4. For each fresh registered NR observation, identify whether it is a new
   strongest or weakest SS-RSRP record. Keep its band/cell/channel context and
   observation time, even if its position cannot be determined. The first valid
   reading can be one initial marker representing both extremes. Ties do not
   create duplicate records. These are observed session extremes, not claims
   that every location in the room was sampled or that a record is noise-free.
5. Attach a pose only when timing, reference identity and tracking continuity
   support that association. Otherwise show **position unavailable**. Track
   device motion and rotation so delayed RF observations are not assigned to
   where the phone moved afterward. Do not assume AR frame timestamps and
   Android modem elapsed timestamps have identical clock origins.
6. Save a bounded session locally in private app storage: reference identity,
   measured width, reference-relative positions/orientations, RF readings,
   tracking/timing provenance and timestamps. Do not export private room
   images, coordinates or sensor traces in the diagnostic report. Provide
   explicit delete/session limits and distinguish disk saves from memory only.
7. Reopen the recorded session, then scan **the same unmoved reference** to
   align its coordinate frame. Saved AR world coordinates alone are insufficient
   to align a newly created tracking session. Refuse guidance when the reference
   is missing, wrong or tracking is lost.
8. Select a strongest/weakest record on the map or an accessible list. Show
   estimated horizontal displacement, height difference and phone rotation
   difference, with arrows in the reference frame and tracking status. A small
   displayed residual is not proof of exact arrival. Show a camera overlay or
   local map target, not an invented room floor plan or route through walls.
9. Pause camera/sensor work when leaving the screen or backgrounding. Returning
   requires tracking/re-alignment as appropriate. Do not keep stale guidance
   live after interruption, reboot, moving the reference or a tracking reset.

## Technical constraints still to resolve

- ARCore is an optional new dependency; the current app has no AR SDK, camera
  permission or rendering lifecycle. Integrate its SDK/notices, availability
  check, install/update flow and device compatibility without breaking the
  existing non-AR app or source build.
- Ordinary ARCore local anchors are valid for the current app instance.
  Re-detecting a persistent physical reference is the proposed way to align
  separate sessions locally. Cloud Anchors are a different design requiring
  service configuration and spatial-data upload; they are not authorized as
  an implicit fallback.
- ARCore Frame timestamp origin is explicitly undefined in its Java API.
  Investigate reliable temporal association rather than comparing that raw
  timestamp directly to `CellInfo.getTimestampMillis()`.
- ARCore already fuses camera and inertial observations. Additional pressure,
  gravity, accelerometer and gyro data can document movement/holding conditions;
  uncalibrated redundant sensor fusion must not be advertised as improved
  absolute accuracy. Missing sensors remain unavailable.
- RF readings themselves are delayed/modem-sampled and vary with hand/body
  placement, traffic and cell changes. Map point precision does not establish
  precise timing or repeatable RF strength. Label records with their cell/band.
- Host tests must cover record extrema, bounded persistence/schema validation,
  pose transforms across reference re-detection, timing mismatch, duplicate
  observations, tracking loss and refusal to guide before alignment. Actual
  room-scale accuracy and return repeatability require physical phone tests
  against measured reference points before any accuracy claim.

## Primary references

- [Android Location](https://developer.android.com/reference/android/location/Location)
  exposes estimated horizontal and vertical accuracy separately.
- [Android motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)
  describe acceleration, angular motion and rotation-vector data.
- [ARCore Augmented Images](https://developers.google.com/ar/develop/augmented-images)
  supports fixed reference images and supplying a measured physical size.
- [ARCore anchor scopes](https://developers.google.com/ar/develop/anchors)
  distinguishes local, Cloud and Geospatial anchors.
- [ARCore Frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame)
  documents the undefined origin of its capture timestamp.
- [ARCore supported devices](https://developers.google.com/ar/devices)
  lists Xiaomi 13 Pro with Depth API support. That listing does not verify this
  rooted device's installed AR runtime or the proposed feature.
