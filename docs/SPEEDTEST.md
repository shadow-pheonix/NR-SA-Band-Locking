# Compare bands with Speedtest

This feature is included in **SA Band Lab 0.11**. Phone validation is still
needed; host/package checks do not prove Android cellular routing or accuracy.

## Run a test

1. Select your SIM in the app header. Make that SIM the phone's **mobile-data
   SIM** in Android settings, enable mobile data, and turn Wi-Fi and VPN off.
2. In **Bands**, read live preferences, select bands and NR-only if wanted,
   then use **Apply SA bands until reboot**. Wait for verification and usable
   service. Unapplied choices block testing; a speed test never applies them.
3. Open **Speedtest**, between Bands and Tools, and tap **Start speed test**.
4. If the phone's Location switch is off, choose **Open Location settings**,
   turn Location on, return to the app and tap Start again. This prompt appears
   only when Location is disabled. Grant Phone and **Precise** location permission
   if requested. Refresh network uses the same Location-switch check.
5. Read the data-use confirmation and tap **Start test**. Keep the app open.
   Each run may transfer up to **100 MB download + 20 MB upload payload**, plus
   protocol overhead. Your carrier's data accounting can be higher.
6. Watch latency, download and upload progress. Tap **Stop test** to cancel.
   Leaving the app or rotating it also requests cancellation. A blocked network
   operation can take a few seconds to close; no background test is scheduled.
7. Review the saved history. Repeat with another applied selection in the same
   place, close other downloads and compare several runs at similar times.

The chosen SIM must be both the default and currently active data subscription.
The test refuses Wi-Fi/VPN routes and pins HTTP connections to the original
cellular Network. A detected route or SIM change fails the test rather than
silently measuring another connection. OEM routing still needs phone testing.

## What each history entry includes

- Date/time, SIM slot and Complete / Stopped / Failed status.
- **Serving bands (sampled)** during that run, including observed changes or
  unknown samples. This is independent of checked bands and modem preferences.
- Download/upload in **Mbps**, **Ping (HTTP)** in milliseconds, and payload usage.
- Individual timestamped serving-band/RAT observations at phase boundaries.
  Copy reports also includes JSON with cell age, NR-ARFCN, PCI and SS-RSRP when
  available, plus measurement durations and the last-read SA preference label.

The latest **20 runs across both slots** remain in private app storage after
restart. The page and Copy reports filter by the selected SIM. Failed/stopped
runs are saved too; missing measurements display a dash. Earlier completed
phases can remain visible in an incomplete run. **Clear all speed test history**
deletes both slots' speed reports and keeps modem settings and backups. Copy
reports before clearing or before older runs roll out of the latest 20.

## Serving-band limits

The app requests subscription-specific Android cell updates before latency,
before download, before upload and after transfers. Only **registered NR cells
with a reported age of at most five seconds** contribute to the serving label.
Stale, undated, missing or rejected observations are **unknown**. No cached
fallback, checkbox value, NR-ARFCN guess or preference mask supplies that label.

Location being enabled and permission being granted do not guarantee that the
firmware supplies a fresh cell. Android can throttle updates. The report may
show some samples as unknown while the speed measurement still completes.
LTE fallback is labeled by its RAT; an NR band is not invented for an LTE-only
sample. This release reports NR serving bands rather than LTE band numbers.

These are boundary samples. A band could change between them, and Android can
omit extra aggregated carriers. The report does not prove that one band served
the entire transfer or that a preference lock is enforced over the air.

## Measurement and privacy

The app independently implements HTTP requests against Cloudflare's published
speed-test endpoints. It does not bundle Cloudflare's SDK or implement its full
browser benchmark methodology. It is not affiliated with Cloudflare or Ookla.

- **Ping (HTTP)** is the median of three HTTP response-latency measurements to
  the download endpoint with a zero-byte body. It includes connection/TLS/DNS
  setup when needed; it is not ICMP ping or radio latency alone.
- Download and upload use up to four concurrent streams, each direction ending
  after ten seconds or its payload cap. Download counts received bytes; upload
  speed counts only complete payloads with an HTTP 200 response. Upload bodies
  use generated test bytes, not files from the phone.
- Speeds are end-to-end payload averages over the phase wall time, including
  setup and ramp-up overhead. A run shorter than two seconds is labeled **short
  sample**. Fast 5G connections can hit the data cap early, so these readings can
  underestimate sustained speed. They are comparative estimates, not peak-radio
  measurements, an ISP dispute benchmark or a universal best-band ranking.
- HTTP errors, compressed/unexpected downloads, truncated bodies and route
  changes produce failures. A failed phase is not presented as a valid rate.
  Very slow connections and timeouts can make a run longer than the usual
  roughly 30–60 seconds including cell sampling and latency probes.

**Cloudflare receives your IP address, ordinary HTTP metadata and generated test
traffic.** Its service/privacy terms apply. The app sends no cell observations,
band selection, GPS coordinates, backups or report JSON to those endpoints and
does not call analytics/results APIs. Reports stay local unless you copy/share
them yourself. No internet request is made just by opening the tab, reading
modem preferences or applying bands.

The source adds Android INTERNET and ACCESS_NETWORK_STATE permissions for this
feature. It uses Android's existing Phone/Precise location permissions to attach
serving-cell observations. Root band control remains independent of Location.

## Primary references and checks

Endpoint definitions and methodology:
[Cloudflare speed-test component](https://github.com/cloudflare/speedtest),
[endpoint configuration](https://github.com/cloudflare/speedtest/blob/main/src/config/defaultConfig.ts),
[Cloudflare service and privacy information](https://speed.cloudflare.com/about).

Android API contracts:
[Network.openConnection](https://developer.android.com/reference/android/net/Network#openConnection(java.net.URL)),
[active data subscription](https://developer.android.com/reference/android/telephony/SubscriptionManager#getActiveDataSubscriptionId()),
[TelephonyManager cell updates](https://developer.android.com/reference/android/telephony/TelephonyManager#requestCellInfoUpdate(java.util.concurrent.Executor,android.telephony.TelephonyManager.CellInfoCallback)),
[LocationManager.isLocationEnabled](https://developer.android.com/reference/android/location/LocationManager#isLocationEnabled()).

Source checks cover HTTP transfer loops, byte limits, status/length/encoding
errors, acknowledged uploads, deadlines, cancellation, fresh registered-band
filtering, report formatting, missing values, SIM filtering and latest-20
retention. Android source compilation and a tiny 1 KB live endpoint smoke check
pass. These do not execute Android connectivity services, AtomicFile storage,
Location settings, lifecycle interactions or phone radio sampling. Those remain
phone-validation work. The feature is packaged in the 0.11 APK.
