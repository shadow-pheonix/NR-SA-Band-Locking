# SA Band Lab: session context and handoff

Last updated: **2026-10-29 UTC**.

This file preserves the project's decisions, research, implementation, device
feedback and operating details so a fresh session can continue without the
original conversation. It is a maintained handoff, not a verbatim transcript or
a collection of private phone reports. [AGENTS.md](AGENTS.md) requires reading
and updating it at meaningful checkpoints and before handoff.

**Required maintenance:** read this file at session start and update it
frequently without waiting for a user reminder: after meaningful decisions,
findings, changes, checks, phone feedback and releases, and before ending a
substantive task. Keep the current state accurate rather than only appending
history. Never publish credentials or private device identifiers here.

## Start here: current state

- Public repository: <https://github.com/shadow-pheonix/NR-SA-Band-Locking>.
- Canonical workspace: `/home/coder/NR-SA-Band-Locking`.
- App/package: **SA Band Lab**, `dev.saband.lab`.
- Latest published APK: **0.13**, version code **13**, name `0.13-sa-test`,
  ARM64, minimum Android 11 / API 30, target API 34. Signed, verified and public
  as an experimental prerelease. Previous releases remain unchanged.
- Public release: [v0.13.0](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.13.0).
- [0.13 APK download](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/download/v0.13.0/sa-band-lab-0.13-arm64.apk).
- **Latest modem mutation evidence remains the 0.9 phone report:** combined
  n78-only SA + NR-only Apply and modem readback succeeded, including the
  post-reboot fresh-baseline fix. The newer 0.11 report is a speed test only.
- **Automatic reboot reapply has not been phone-tested.** The latest report has
  `reapply_after_reboot_enabled: false`; it contains no boot reapply evidence.
- Latest 0.12 device report fails in **Latency** after roughly 6s, with no
  download/upload attempted and zero transferred payload. Fresh registered n78
  observations and Android NR RAT are present before and after the attempt.
  A separate Refresh network snapshot also succeeds. No root operation ran.
- Earlier 0.11 report remains the only speed evidence: about 266 Mbps download,
  HTTP latency about 79 ms, upload timeout. Neither report demonstrates a full
  successful speed test, nor a modem preference change in those sessions.
- Shipped in 0.13: 0.12's 3s latency socket timeout/6s fail-fast gate is
  replaced by an independent latency phase (5s socket, 8s total budget), followed
  by download and upload even when a measurement fails. Stop/route changes remain
  fatal. Each phase owns workers/connections, with asynchronous deadline cleanup
  so stuck ping work cannot starve throughput workers. Bounded operation/HTTP
  diagnostics, valid ping counts and Speeds measured · ping unavailable status
  added. Nine Java/report/real-HTTP suites, 52 native mocks and the full signed
  build/package checks pass. The user paused an earlier build to request a 5G
  tile, then explicitly authorized building after implementation without asking
  again. Both tile and speed fixes are now public in 0.13; phone validation of
  both is pending. The tile offers saved SIM/bands, combined NR-only on, mode-only
  LTE off, explicit LTE default during setup and live preference state. See below.
- Neighbor-band discovery remains unresolved: serving observations only on the
  tested firmware; its experimental QMI frequency request is unsupported.
- Session-continuity documentation is now provided by this file and the
  repository's `AGENTS.md`; README links to both. This documentation checkpoint
  also records the user's successful 0.9 manual Apply and permission questions.

## Current feature work: 5G Quick Settings tile (2026-10-29)

User paused the requested build and asked for a 5G icon tile. On: selected SA
bands and NR-only in the same verified SET. Off: LTE-only (QMI RAT mask 16),
which is the tile's default off mode. A separate saved tile profile chooses SIM
and bands; it does not change the manual Bands draft. Setup explicitly offers
Save & use LTE only to establish the default. No silent LTE SET on app install,
opening Quick Settings, or simply adding the tile. Tile actions clear boot
reapply for the controlled slot so an older NR plan cannot undo LTE-off.
Reuse the existing root helper and journal with live preflight, original backup,
compare-before-SET, verification/rollback, no NSA/LTE band writes. LTE-only uses
the existing mode-only helper path to preserve SA bands. Tile state comes from
live preferences rather than a persisted on/off flag. Busy/failed/unknown must
not claim success. Unlock before mutation; long press opens tile configuration.
User then explicitly authorized: once implemented, build the APK without asking
again. Finish source/checks, then build, verify and publish version 0.13.

Implemented: original monochrome 5G icon; protected TileService; Android 13+
add-tile request/manual fallback; long press opens Tools; per-tile SIM/bands
configuration with select all/clear and Save & use LTE only. Defaults select all
verified selectable bands. Setup preserves an existing manual band/radio draft.
Configuration is private and bound to the original backup/device/firmware.
On uses combined qmi-apply-plan (mode 64); off uses mode-only qmi-mode (16),
preserving SA/NSA/LTE band masks. Fresh journal preflight and uncertain intent
retention reuse existing backups; boot reapply is cleared on tile actions.
Global modem/speed/boot lock is now a ReentrantLock: tile tryLock fails promptly
while busy instead of queueing an unexpected later toggle. State is refreshed
from live reads whenever the panel opens; read failures show Unknown, not a
verified LTE state. NR-only with a different mask remains on and can be disabled.
Opening/adding tile never writes. Unlock is required before tile mutation.

Validation checkpoint: Android API 34 Java compilation and seven host suites
including QuickTileTest pass. Native transport + 15 EFS + 37 QMI mock cases pass,
including both-slot combined-on/mode-only-off packet checks and LTE-off rollback
to NR-only. Tile delivery, HyperOS rendering, background root and live LTE/NR
registration still require phone validation. The user authorized building after implementation, without asking again.
Version 0.13 / code 13 is built, verified and published.
Nine Java/report/real-HTTP suites pass. Native transport + 15 EFS + 37 QMI
(52 total native mock cases) pass. Phone validation is still pending.

## Approved 0.13 build checkpoint (2026-10-29)

User explicitly authorized implementation followed by build without asking again.
This APK includes both the 5G tile and the previously published phase-isolation
speed-test fix. No native protocol change; helper label is 0.13.

- APK `sa-band-lab-0.13-arm64.apk`: **157,237 bytes**.
- APK SHA-256: `2c95ae72a04a473254304390a6f8004ad8b4ffddfcb3b32e651422cf7a0a5605`.
- Helper: **76,712 bytes**; SHA-256
  `51f6454cc6688b0dd8e3312306f3888679489b256c688a5ddd19aded3c8b2f77`.
- Signature: v3 verified, same official certificate SHA-256
  `c9d0695602ec1ede8245f7bcb39d3cf89ec9721927879b36b23d94c78d766fc7`.
- Manifest: package unchanged, code 13/name 0.13-sa-test, API 30/34, ARM64;
  system-protected exported TileService, QS_TILE/TOGGLEABLE_TILE metadata,
  long-press preferences action and original monochrome 5G icon verified.
  Existing runtime permissions are unchanged.
- ZIP integrity/alignment, exact bundled README/license/transport notices,
  new tile and isolated speed-test DEX classes, exact ARM64 helper and four
  16 KiB ELF LOAD alignments checked. No native test mock switch/private key
  packaged. Earlier locally available APK hashes unchanged.
- Seven core Java suites (including QuickTileTest), report/history and real
  localhost HTTP suites pass; transport boundary checks and 52 native mocks
  pass. No emulator/phone execution was performed for the new tile or latency fix.
- Published [v0.13.0](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.13.0)
  with APK + `SHA256SUMS.txt`; tagged source commit
  `256553d0df35e3e88150232b5728551b044bd30d`.
- Anonymous download of the direct public APK succeeded and matched the local
  verified APK byte-for-byte and by SHA-256. README links to this APK and gives
  tile setup instructions. Original 0.12 release remains unchanged.
- Install over the previous official app to retain original backups; do not
  uninstall as part of an update. Source/context publication and local sync are
  being completed in this handoff commit.

## User intent and standing decisions

The original request was to use **REA on GitHub** to reverse engineer a supplied
Cellular Pro APK and develop an independent rooted-phone band-locking app. The
APK and research were in the `Cellular Pro` folder under the coder profile.

The user initially wanted SA and NSA, with SA first. They later explicitly
dropped NSA: they use **Jio SA**, so the application scope is **SA only**. NSA is
still read to ensure it remains unchanged. Do not restart NSA development merely
because old notes mention it.

Requested product behavior:

- Choose SIM slot, read live preferences, capture original bytes and export or
  import a backup bound to the same phone and firmware.
- Select individual SA bands, with **Select all** and **Deselect all**.
- Stage **NR only (5G)** alongside the band choices. Both must take effect
  through **Apply SA bands until reboot**, not separate checkbox-triggered SETs.
- Show phone-supported bands separately from actual serving/nearby observations.
  The user asked to see available nearby bands, not just the connected band;
  this goal is only partially met because the modem exposes limited information.
- Offer optional persistence after reboot. Implemented as **Reapply after
  reboot**, default off, saved with a successful combined Apply. It reissues a
  temporary setting after boot/unlock; it does not make permanent NV writes.
- Make the project open source at the repository above, with APK assets in
  GitHub Releases, a direct README download link, beginner instructions, tested
  phone/root details, plausible SoC candidates and unsupported-device limits.
- Give the app a recognizable launcher icon. The 2026-10-29 source update adds
  an original mint padlock with radio-wave arcs, matching the navy/mint UI.
- Overhaul UI/navigation and refine UX. The subsequent source update introduces
  Overview, Bands and Tools, clear observation-versus-preference state, preserved
  drafts between tabs, a persistent SIM selector and organized recovery tools.

The user asked about combining both SIMs' download speed, then said **leave it**.
No bonding/multipath feature is planned. They also asked how much of the result
came from reverse engineering versus online research: the recovered Cellular Pro
route was EFS; the successful independent route is QMI based on public protocol
research plus device testing. Do not invent a percentage or claim the supplied
APK was shown to use the successful QMI route.

### Build and collaboration preference

The user explicitly said to make changes first and ask before building an APK.
They later approved specific builds, including 0.8 and then 0.9. Those approved
builds are complete; they are not unlimited approval for future APK builds.
For a new change, complete source and relevant checks, then ask about building
unless the current user request explicitly authorizes a build. Source/context
maintenance does not itself authorize a new binary or release.

The user prefers action and concise explanations over repeated confirmations.
Once a build/release is authorized, finish the build, verification, upload and
README update within that scope. Never ask again for the same authorization.

## Target phone and observed compatibility

| Item | Evidence from user reports |
| --- | --- |
| Phone | Xiaomi 13 Pro, model `2210132G` (`nuwa`) |
| SoC / modem | Snapdragon 8 Gen 2 / Qualcomm Snapdragon X70 |
| Root | Available; app invokes its helper through `su` |
| Android | 16 |
| Firmware | HyperOS `OS3.0.304.0.WMBINXM` |
| Fingerprint | `Xiaomi/nuwa_in/nuwa:16/BP2A.250605.031.A3/OS3.0.304.0.WMBINXM:user/release-keys` |
| Baseband | `MPSS.DE.3.0-GLB-855fda3454` (reported for both slots) |
| Tested subscription | SIM 1, slot 0, Jio SA |
| Actual discovered services | NAS QRTR node 0, port 83; DMS node 0, port 95; reported instance 1 |
| Legacy DIAG endpoint | QRTR node 0, port 29 |

Discover services at runtime; the above ports are observations, not constants.
Both slot bindings are mock-tested; the conversation does not establish a live
second-slot apply. Android subscription IDs are not interchangeable with slot
numbers.

The DMS capability list reported **n1, n3, n5, n7, n8, n20, n28, n38, n40,
n41, n66, n71, n75, n77, n78, n79**. All were included in the original captured
SA preference. This establishes reported NR capability, not local coverage or
carrier SA provisioning on each band. **n258 was not reported as supported**;
the app cannot add absent RF hardware support. n28 capability does not establish
that a nearby n28 cell exists or is usable by this SIM.

README compatibility candidates include Snapdragon 8 Gen 1 / 8+ Gen 1, 8 Gen 2,
8 Gen 3, 888/888+, and 865/865+/870, but only the specified Xiaomi was tested.
SoC similarity does not guarantee accessible QRTR/QMI or the required fields.
The current control backend does not support MediaTek, Exynos, Tensor, Kirin or
Unisoc modem platforms, unrooted access, iOS, Android below 11, or non-ARM64
build targets. Missing transport/fields must disable writes rather than guess.

## Research provenance: recovered EFS versus independent QMI

The supplied proprietary target was **Cellular Pro 1.7.6, version code 85,
ARM64**. Historical local evidence is under `/home/coder/Cellular Pro`.
**That directory is research/history, not the current source of truth.** Its
older status paragraphs predate later phone successes.

REA 6.8.0 / headless JADX 0.7.1 supplied package, class and native-registration
evidence. Much of the app is protected by a native Dalvik-style interpreter:
`NativeUtil` loads `libQualcommAdapter.so`, dispatching through `vmInterpret`
in `libkotlin.so`. Empty JADX bodies did not establish missing functionality.
Supplementary ELF/Capstone, relocation, string and partial VM analysis recovered
the relevant native paths. The exploratory VM decoder was partial; do not claim
the entire protected application was reconstructed or dynamically exercised.

Recovered chain, condensed:

```text
FragmentLock.doAction -> protected Java band handler -> TraceInfo.invoke
  -> libd008.so NR dispatch -> local command 9 / inner command 0x0b
  -> privileged dcidaemon -> 64-byte NR mask -> Qualcomm DIAG EFS PUT
```

The application's 256-byte internal request stores little-endian u32 command
`0x105`, band count, band numbers and SIM slot. **It is not a raw DIAG/QMI
packet and must not be written directly to `/dev/diag`.**

The recovered daemon's ordinary NR operation writes both the SA and NSA EFS
preferences. Slot 0 paths are:

```text
/nv/item_files/modem/mmode/nr_band_pref
/nv/item_files/modem/mmode/nr_nsa_band_pref
```

Slot 1 uses `_Subscription01`. These are modem EFS paths, not Android files
that shell `echo` or `cp` can modify. The recovered PUT body uses subsystem
`4b 13`, operation `0x0026`, u32 data length 64, u32 flags `0x00080040`, u16
sequence, 64 mask bytes, then a NUL-terminated path. Transport framing is a
separate layer. Send success alone is not evidence of modem acceptance.

This informed an independent EFS experiment. The tested firmware reports that
SA override path absent. The working app therefore uses a **separate,
independently implemented QMI NAS route**, researched from libqmi NAS/DMS
definitions, qcom-band-menu and Linux QRTR. Do not describe QMI as a recovered
Cellular Pro mechanism or infer missing EFS defaults.

Historical local references, useful only if that workspace still exists:

- `/home/coder/Cellular Pro/SA-RESEARCH.md`
- `/home/coder/Cellular Pro/analysis/native-findings.md`
- `/home/coder/Cellular Pro/analysis/GET-PACKET-REPAIR.md`
- `/home/coder/Cellular Pro/analysis/QMI-SA-BACKEND.md`
- `/home/coder/Cellular Pro/analysis/NR-NEIGHBOR-READ.md`
- `/home/coder/Cellular Pro/analysis/jni-metadata/`

Public provenance, licenses and primary-source links are preserved in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Original app/helper code is MIT.
The vendored qcom-efs transport is BSD-3-Clause from
`catxeon/qcom-efs-browser`, commit `9fde4f92a0cc49fc645cb70d19419f553108cb53`,
with origin hashes and modification ledger retained. musl and Zig runtime
notices are packaged. **No proprietary Cellular Pro APK, daemon, library or
decompiled implementation is shipped in the repository or application.**

## Version history and why earlier approaches failed

| Version | Change and device evidence |
| --- | --- |
| 0.1 | Initial EFS read rejected as `bad length (0x15)`. The request omitted the modern ten-byte size/path-length/sequence prefix; the data parser was also two bytes early. Original mocks shared the mistake. |
| 0.2 | Correct modern EFS GET and strict response framing. Phone returned a well-formed errno-2 response for the SA path: absent file, no write. |
| 0.3 | Added independent QMI NAS/DMS backend. Service discovery worked, but the subscription bind send failed locally. |
| 0.4 | Removed QRTR-incompatible `MSG_NOSIGNAL`, using send flags 0, with send return/errno diagnostics and a stricter test wrapper. Phone confirmed binding, live SA/NSA reads and DMS capability detection. |
| 0.5 | Added all Android-reported NR cells, including unregistered cells when present, signal/age information and separate supported-band display. Phone exposed one serving n78 cell and no neighbors. |
| 0.6 | Added separate read-only QMI frequency query `0x00d2`. Phone rejected it as `DeviceUnsupported (25)`. An optional EFS result appearing in the report did not establish that the main QMI backend had regressed. |
| 0.7 | Added bulk band selection, mutation trace retention and an intermediate separately-applied NR-only control. User rejected the separate application behavior. |
| 0.8 | Combined staged NR-only and selected bands in one Apply SET with verification/rollback. Source opened under MIT and APK published. A later phone report exposed stale expected SA after reboot; no SET was sent in that failure. |
| 0.9 | Refreshes live expected state without replacing originals; cleans refused pending intent; adds optional boot reapply. Built, signed and released. Latest user report confirms n78 + NR-only combined manual Apply and readback. Boot reapply remains untested on phone. |

### The 0.8 stale-snapshot bug and 0.9 fix

After reboot, the modem returned to its original broad SA mask. Private app
storage still held the previous n78-only `expected_sa`. Although Apply ran a
fresh QMI probe, 0.8 passed that old saved SA value to the helper. The helper
correctly refused with `stale_snapshot` and `write_attempted: false`. Re-reading
also failed to reconcile an original/default mask that differed from both the
saved expected/pending values. Failed pre-write checks could leave pending
targets and a dirty-mode flag despite sending no SET.

In 0.9, `PreferenceJournal` reconciles a successful explicit live read, preserving
the original SA/NSA and captured earlier mode. Apply reads fresh state and uses
its SA/mode as the expected baseline. The helper independently reads and compares
again before SET. True concurrent changes still refuse; there is no automatic
write retry to override them. Explicit no-write results cancel staged intent;
uncertain writes retain pending targets until a successful live read resolves
the outcome. The last mutation trace remains available after later reads.

### Launcher icon source update (2026-10-29)

The user asked for a good app icon. The app previously declared no launcher
icon and had no `android/res` resources. The new artwork is an original vector
radio-lock mark: mint lock body, pale shackle and outer waves, mint inner waves,
and a dark navy gradient background. It remains clear at launcher thumbnail
size and is not derived from Cellular Pro or an external icon pack.

Source files:

- `android/res/drawable/ic_launcher_background.xml`
- `android/res/drawable/ic_launcher_foreground.xml`
- `android/res/drawable/ic_launcher_monochrome.xml`
- `android/res/mipmap-anydpi-v26/ic_launcher.xml`
- `android/res/mipmap-anydpi-v33/ic_launcher.xml`
- `docs/assets/app-icon.svg` (review/README preview using the foreground paths)

The manifest's application icon and roundIcon both reference
`@mipmap/ic_launcher`. Android 11/12 use the adaptive foreground/background;
API 33+ can also use the monochrome layer for themed icons. Vector layers use
a 108 dp viewport, with central artwork kept within the adaptive safe region.
`build.sh` now runs AAPT2 compile on `android/res` and passes the resource archive
to AAPT2 link. No Java resource constants are needed by the current UI.

Validation completed: all five icon XML resources compiled with the installed
AAPT2, manifest/resource references and vector path consistency were checked,
and squircle, circular, themed and 48 px previews were visually reviewed.
Build-script syntax checks passed. No APK was linked, signed or released for
this change, and no phone/launcher result is claimed. The local preview is
`build/icon-preview/launcher-preview.png` (ignored); the SVG is committed.
Future artwork edits should update both Android vectors and the SVG preview.

The subsequent approved build uses 0.10 / code 10, preserving the prior signing
identity and leaving the existing 0.9 APK/tag intact.

### UI/navigation source overhaul (2026-10-29)

The user then asked to overhaul the UI and navigation and refine UI/UX, rather
than approving the icon APK build. Both initially remained
source-only; the subsequent explicit build request now includes them in 0.10.

`LabLayout.java` now owns a native three-destination interface with an inset-safe
header, launcher mark, two-button SIM selector, status/progress area and bottom
navigation. The navy/mint theme is declared in `android/res/values/styles.xml`.
No Gradle, AndroidX or other runtime dependency was added.

- **Overview:** serving-at-refresh band, Android RAT label, SS-RSRP/SS-SINR,
  reported cell count, nearby bands, a separate modem-preference card, and
  expandable full cell details. Unknown values are dashes, not fabricated zeros.
- **Bands:** read/backup card, a responsive two-to-four-column band grid, bulk
  selection, staged NR-only/reboot switches, and a selection count/UNAPPLIED
  summary with the existing one Apply action. The footer remains visible on
  normal portrait windows and scrolls with the page on short windows.
- **Tools:** backup import/export, captured-SA restore, explicit boot-plan
  disable, report copying, device/capability information and network settings.
  Optional frequency and legacy EFS checks are under an advanced disclosure.

Page views stay allocated when switching tabs, retaining staged choices and
scroll positions. Tab navigation, SIM selection and switch changes send no
modem requests. Unapplied choices require confirmation before switching SIMs or
leaving Overview; Back from another destination returns to Overview. Explicit
rereads preserve still-selectable draft choices. Activity state saves slot,
page, scroll and draft values, but requires a fresh modem read before restoring
choices and re-enabling writes after recreation. Failed Apply also retains
choices for review after a successful reread; it never retries the write.

`UiPresentation.java` supplies pure host-testable Apply gates, draft comparison,
radio/mask labels and network summary decisions. Samples age locally every
15 seconds while the activity is resumed; this issues no modem/network queries.
Older and undated samples are labeled, cached data warns about other SIMs, and
neighbors are never promoted to the serving hero. A fresh registered sample is
preferred to an older stronger sample. Full original observations and mutation
responses remain in diagnostics.

The app explains Phone/Precise location before requesting observation permissions.
Operation details can be expanded from the always-visible status, with report
copying available. Controls reflect busy/disabled states; native accessibility
checked/selected state, live status announcements, generous touch targets,
wrapping text, large-font metric stacking and system-bar/cutout insets are used.
Actual Android rendering, focus order, font scale, rotation and phone gestures
still need device/emulator validation after build approval.

The root helper, QMI wire protocol, original backup schema, compare/verification/
rollback behavior and boot transaction classes were not modified. MainActivity's
view/controller wiring changed; the combined Apply and SA-only Restore command
arguments and conservative `write_attempted` default were reviewed and preserved.

Validation: Android javac compilation passed, all six resource XML files compiled
with AAPT2, and five Java host suites passed including `UiPresentationTest`.
The existing 50 native tests remain prior protocol evidence; they were not rerun
for this view-only change. `docs/UI.md` describes the navigation and limits.
`docs/assets/ui-overview.svg` is an **illustrative layout reference with example
data, not Android screenshots**. Its locally rendered PNG is ignored under
`build/ui-preview/`. README points to the unreleased UI guide. No new APK, DEX,
signing or release operation was performed, and the 0.9 APK checksum is unchanged.

## Latest user-supplied 0.9 evidence

The report supplied after installing the released 0.9 APK contained:

```text
helper_version: 0.9
state: applied_verified
ok: true
write_attempted: true
SA preference: n78 only
rat_mode_preference: 64 (0x40, NR only)
NSA preference: unchanged from captured original
pending_sa: empty
pending_radio_mode: -1
original_radio_mode: 28 (0x1c)
expected_radio_mode: 64
radio_mode_dirty: true
reapply_after_reboot_enabled: false
serving observation: not refreshed
```

The trace starts with the original broad SA mask and RAT mask 28. One combined
SET is acknowledged. The first immediate GET still reports the old state; a
subsequent GET reports n78 and mode 64. The existing bounded convergence loop
handles this delay. This is real-device evidence for the combined request and
preference verification, not a reason to shorten verification to one read.

`radio_mode_dirty: true` is expected here: current NR-only mode differs from the
captured earlier mode. `rollback_verified: false` on this successful operation
does not indicate an error; rollback was unnecessary. Original SA bytes remain
preserved. No automatic reboot plan was enabled or demonstrated by this report.

Do not publish the user's Android ID/device binding, private backup timestamps,
raw report or SIM identifiers. This file intentionally records only the facts
needed to reproduce and interpret the protocol behavior.

## Current architecture and protocol contract

### Native QMI backend

`native/qmi_sa.c` implements narrow QMI over QRTR family 42. It discovers NAS
service 3 and DMS service 2 using the current version/instance convention and
rejects ambiguous endpoints. QMI framing is seven bytes: flags, little-endian
u16 transaction, message and payload length. QRTR sends use flags **0**;
`MSG_NOSIGNAL` is rejected by the relevant kernel path. Unix socket mocks alone
did not originally catch this, so the tests wrap send to enforce the restriction.

| Operation | Message / TLVs |
| --- | --- |
| NAS Bind Subscription | `0x0045`, TLV `0x01`: one-byte slot 0 or 1 |
| NAS Get System Selection Preference | `0x0034`; SA `0x2c` and NSA `0x2d`, exactly 64 bytes each; RAT mode `0x11`, u16 |
| DMS Get Band Capabilities | `0x0045`; NR `0x13`, counted u16 band list |
| Combined Apply | NAS `0x0033`; duration `0x17 = 0` (until power cycle), SA SET TLV `0x2f` (64 bytes), RAT mode `0x11` (u16) |
| Neighbor-frequency experiment | NAS `0x00d2`, empty payload; optional serving `0x1c` and neighbor `0x1d` lists |

Masks are 512 bits / eight little-endian u64 words. Band n is bit n-1; n78 is
byte index 9, value `0x20`, all other bytes zero for an n78-only selection.
GET SA TLV `0x2c` is not the SET SA TLV `0x2f`. NR-only RAT mode is 64; do not
confuse QMI mode flags with Android network-mode enumeration values.

Normal Apply requires a nonempty target subset of both the captured original SA
mask and DMS-reported NR support. It checks live SA/NSA and expected mode before
SET, sends only the intended duration/SA/mode fields, then verifies requested
SA/mode and unchanged reported NSA/LTE/legacy/other guarded preferences. It uses
up to five bounded readbacks with 150 ms convergence waits. If necessary it
reopens/binds and attempts one combined rollback to the immediately preceding
SA/mode, verifying recovery. An unverified outcome is explicit, not success.
One combined request does not establish atomic modem execution.

Current helper commands include:

```text
qmi-probe SLOT
qmi-apply-plan SLOT ORIGINAL_SA EXPECTED_SA ORIGINAL_NSA TARGET_SA EXPECTED_MODE TARGET_MODE
qmi-restore SLOT ORIGINAL_SA EXPECTED_SA ORIGINAL_NSA
qmi-neighbors SLOT
```

Older narrow `qmi-apply`, `qmi-mode` and EFS research commands remain in the CLI;
the Android NR-only checkbox does not invoke a separate mode mutation. Mock
transport hooks are compiled out of the production ARM64 helper.

### Android/UI and state files

| File | Role |
| --- | --- |
| `android/src/dev/saband/lab/MainActivity.java` | SIM selection, UI, staged controls, confirmation, reads, mutation, backup I/O and diagnostics |
| `RootHelper.java` | Shared bounded `su` invocation, concurrent output draining, 32 KiB output bound, 75-second process timeout |
| `PreferenceJournal.java` | Pure Java reconciliation of expected/pending state, immutable original SA/NSA and captured-mode handling |
| `PreferenceStore.java` | Atomic JSON persistence, device binding, journal adapter and shared process-wide transaction lock |
| `RadioModeSelection.java` | Resolve NR-only only when Apply is pressed; restore a known earlier mode when leaving NR-only |
| `LabLayout.java` | Native Overview/Bands/Speedtest/Tools view hierarchy, navigation, responsive cards/grid and staged-control presentation |
| `SpeedometerView.java`, `SpeedometerScale.java` | Unreleased native animated measured-average dial and host-tested scale |
| `UiPresentation.java` | Pure Apply/draft/network display decisions, including stale/cached/unknown data handling |
| `FiveGBands.java` | Render Android serving/unregistered NR observations without inventing detections |
| `NrFrequencyPlan.java` | Supported-band-constrained frequency candidates, preserving overlaps |
| `BootPlans.java`, `BootPolicy.java`, `BootReapply.java` | Opt-in per-slot plans, bounded retry policy and testable boot transaction |
| `BootReceiver.java`, `BootLockJobService.java` | Schedule and execute background reapply after normal boot/unlock |

The native executable is packaged as `lib/arm64-v8a/libsahelper.so` and extracted
by Android to the native library directory. The suffix does not make it a JNI
shared-library interface; root executes it as a helper.

Private per-slot files include `qmi-sa-backup-slotN.json`,
`qmi-last-change-slotN.json`, `boot-lock-slotN.json` and `boot-result-slotN.json`.
Backups use schema 2 / `qmi-nas-sa-v1`, bound to slot, fingerprint, baseband and
signing-key-scoped Android ID. They retain original SA/NSA, expected SA, pending
SA, and optional original/expected/pending radio mode. Use AtomicFile writes.
Do not overwrite captured original band bytes with a lock or guessed default.

Checkbox choices are the intersection of the original captured SA preference
and reported NR capability. If capture happened while already restricted,
selectable bands may remain narrowed. Restore uses the exact captured SA mask,
keeps the current radio mode, and cancels the slot's boot plan. It is not a
factory-reset command or a combined mode restore.

Unchecking NR-only and applying restores the known captured earlier mode if
currently NR-only; otherwise it preserves the current non-NR-only mode. If the
earlier fallback mode is unknown, refuse to guess. The latest phone's captured
earlier value is 28, not an invented automatic-mode mask such as 92.

Diagnostics keep `helper_result`, `last_preference_change`, `efs_diagnostic`,
`five_g_observation`, `modem_frequencies`, saved reboot plan and last boot result
separate. A failed optional EFS/frequency read must not replace the last actual
preference-change evidence.

### Reboot reapply behavior and limits

The option is off by default. Only a successful Apply whose reported SA/mode
match the target saves a per-slot boot plan. Replacing a lock cancels the old
plan before attempting the replacement; failed or uncertain Apply cannot arm a
replacement. **Disable reboot reapply** cancels future reapply without changing
current modem preferences. Restore also cancels the selected slot's plan.

Plans are separate from backups; imports do not arm them. They bind to the
original backup and its device/firmware identity. A mismatch disables automatic
reapply. Plans belong to SIM **slots**, not an immutable SIM identity: disable
before swapping SIMs. No permanent modem-file/NV setting is written.

An unexported receiver handles normal `BOOT_COMPLETED`. A JobService protected
by `BIND_JOB_SERVICE` runs a background worker. Credential-protected plans are
used after first unlock, not locked boot. Requested minimum delays are 30, 60
and 120 seconds, with at most three attempts. Android/OEM scheduling may delay
or prevent execution; root must remain granted and autostart/background settings
may matter. Force-stop can prevent startup. Do not promise immediate lock from
power-on or that the app continuously enforces a setting throughout the boot.

Only preflight failures can be retried automatically. Retry extras include only
the affected slots; another SIM's readiness failure must not requeue a slot
whose mutation failed or is uncertain. Job IDs differ by attempt. UI and boot
work serialize through the same process-wide lock. Cancellation is checked
before SET; an in-flight helper is allowed to finish bounded verification and
rollback instead of being interrupted. Boot results are retained separately.

## EFS and location questions already answered

**EFS GET refused, errno 2** means the old diagnostic could not find the modem
EFS path. The tested device supplied a correctly framed 14-byte negative-length,
errno-2 reply. This is not evidence of a failed QMI lock or a missing root grant.
Do not create that absent path or infer a default mask. Its `helper_version: 0.2`
is the legacy EFS diagnostic's label; the actual successful QMI backend is 0.9.
The optional **Check original EFS path** button is read-only in the normal UI.

**Location permission for detection:** the Android CellInfo API used by
`requestCellInfoUpdate` / `getAllCellInfo` requires precise location permission
because cell identities can reveal location. The UI also asks for Phone
permission. Enable system Location for this detection path if the firmware
requires it. SA Band Lab does not request GPS coordinates or upload cell/location
observations. Released 0.10 had no Internet permission; the new speed-test source
adds INTERNET and ACCESS_NETWORK_STATE for explicit test traffic to Cloudflare.
QMI preference reads and band Apply
through root do not require this Android location permission. Denying it blocks
Android cell observations, not the root-based lock operation.

Primary API reference:
<https://developer.android.com/reference/android/telephony/TelephonyManager>.

## Neighbor-band investigation: unresolved

The prior fresh Android snapshot exposed one registered n78 cell, NR-ARFCN
634080, and no NR neighbors. Individual historical PCI/signal/operator values
are not stable properties and should not be hardcoded. Read all reported
`CellInfoNr` entries, including unregistered ones, with operator and sample age.
Cached results may contain other SIMs; fresh subscription-specific updates are
preferred, and stale/unknown ages must remain explicit.

The separate NAS `0x00d2` experiment was rejected with result 1 / error
`0x0019` (25, DeviceUnsupported). That is not an empty neighbor list or an
Android location-permission failure. Its optional list decoder expects a u8
count and little-endian u32 NR-ARFCNs; missing versus empty lists stay distinct.
Frequency candidates preserve overlaps such as n77/n78. Configured measurement
frequencies do not prove measured cells, signal strength or usable coverage.

There is no full RF scan, active band-by-band registration sweep, or implemented
Qualcomm NR DIAG ML1 measurement backend. A separate DIAG measurement backend is
a possible research direction, not a working fallback. Do not display n28/n258
as locally detected merely because the operator might deploy them or a mask
contains their bits. This requested feature remains an open limitation.

## Speed-test feature (2026-10-29, approved for 0.11)

User requests: add a speed test after band selection, put **Speedtest between
Bands and Tools**, include the actual serving band in every report and save
band/download/upload/ping results to history. Prompt for system Location only
if disabled, with an action opening Location settings, on both Refresh network
and starting a speed test. These are implemented in source; the subsequent
explicit build request authorizes packaging them in 0.11.

- Four tabs: Overview 0, Bands 1, Speedtest 2, Tools 3; PAGE_COUNT drives retained
  views and saved scroll state. Added gauge glyph, progress, Stop, copy/clear
  report controls and a compare-speeds shortcut.
- `SpeedTestEngine.java`: independent Java HTTP implementation against Cloudflare
  `/__down` and `/__up`; four streams, 10s per direction or 100,000,000 down /
  20,000,000 up payload bytes. Three HTTP response-latency samples, median shown
  as **Ping (HTTP)**, including DNS/connect/TLS where needed. Not ICMP or the
  official Cloudflare browser methodology. No SDK or third-party code copied.
- Speeds are average acknowledged upload / received download payload over phase
  wall time, including connection/ramp overhead. Responses/status/encoding and
  download length checked; redirects rejected. Upload bytes count towards speed
  only after an OK response. Sub-2s samples explicitly labeled short. Approximate
  path-to-server performance, not peak radio capability or proof a band is best.
- `SpeedTestSession.java`: requires chosen subscription to be default and active
  data SIM; active Network must be validated cellular and not Wi-Fi/VPN. All
  requests use that Network's openConnection. Route checked before connections,
  periodically during transfer and at the end; no process-wide bind or automatic
  switch/fallback. No default URL.openConnection route. Phone testing pending.
- Serving NR observations requested fresh for that subscription before latency,
  before download, before upload and after transfer. Only registered NR entries
  aged at most 5s populate the serving label. Each report stores timestamps,
  per-phase samples/age/ARFCN/PCI/RSRP/RAT, sampled band union and observed changes.
  Missing/stale/failed readings remain unknown, never inferred from selections
  or stored preferences. This is sampling, not continuous enforcement monitoring
  or a complete aggregation list. No IMSI/phone/operator identity is recorded.
- `SpeedTestStore.java`: AtomicFile local history, latest 20 runs globally,
  filtered by SIM in the UI. Saves completed, failed and stopped runs, preserving
  completed metrics but marking incomplete outcomes. Copy contains readable text
  and JSON; clear explicitly removes both slots' speed history only. History
  persists app restart. General diagnostic report includes latest slot speed run.
- MainActivity stages no modem changes for speed tests. Unapplied drafts block
  Start; Apply/restore/SIM controls disabled during tests. Test and modem work
  serialize on PreferenceStore.LOCK. Stop is nonblocking, cancellation closes
  active sockets on worker; leaving/rotating/destroying activity requests stop.
- System Location checked via LocationManager.isLocationEnabled. If off, dialog
  opens ACTION_LOCATION_SOURCE_SETTINGS; after enabling, return and tap again.
  Already-on switch triggers no such dialog. Phone/fine/coarse permission request
  is separate and only requested when missing. Firmware can still omit bands;
  never promise location enables fail-free cell reads. Denial blocks test startup
  with serving reporting, not root band control.
- Manifest adds INTERNET and ACCESS_NETWORK_STATE. Every run requires data-use
  confirmation: up to 120 MB payload plus overhead. Cloudflare gets IP and test
  traffic. Cell observations, backups, selections and reports are never posted;
  no analytics/results endpoint, background testing or location coordinate calls.
- Checks: Android javac passes; six pure Java suites pass (new transfer/band test),
  additional JSON roundtrip/history-format tests pass. Native protocol, backup
  journal and boot transaction code were unchanged at the source-only checkpoint.
  The subsequent 0.11 build additionally reran all 50 native tests successfully.
  A tiny real endpoint smoke check returned HTTP 200 for 1,024-byte download and
  upload, not a speed/phone validation. Framework routing, location UI, persistent
  storage, accuracy and serving-cell updates still need a real phone test.
- JVM JSON test jar is existing workspace org.json 20240303, not bundled; run
  `tests/test_speed_reports.sh` with SA_ANDROID_JAR and SA_JSON_JAR. Detailed
  method, privacy, limitations and primary sources: [docs/SPEEDTEST.md](docs/SPEEDTEST.md).

## 0.11 phone feedback and revisions shipped in 0.12 (2026-10-29)

User reported a failed speed test, then requested longer time-based tests and a
less congested Speedtest screen with a meter like a conventional speed-test app.

Evidence from that report: 100,000,000 download bytes in about 3.007s, roughly
266 Mbps, median HTTP response latency about 79 ms. All four fresh registered
serving samples were n78 with Android RAT NR. Upload sent about 6 MB, then timed
out roughly 3s into the phase; upload rate is null, complete false, cancelled
false. No root preference read or Apply occurred in that report. No raw private
report, timestamps, subscriber identity or device binding is committed here.

Cause assessment: source uses a 3s socket read timeout and four concurrent 1 MB
upload requests. Failure timing matches that timeout, but the report does not
identify whether DNS/connect/response/server/network caused the delay. It is not
evidence that Cloudflare/Jio universally blocks uploads. Sent bytes do not prove
acknowledged upload speed. The failed label hid useful successful download/ping.

Follow-up implementation:

- Time-driven public Config: 20s Standard or 60s Extended **per direction**,
  four streams and no fixed payload cutoff. Download repeats at most 25 MB per
  request until the phase deadline. Upload starts at 250 KB, grows up to 4 MB
  for fast acknowledgements and shrinks to 62.5 KB for slow ones. No payload-sized
  memory allocation. Internal finite budgets remain only for host test fixtures.
- Response timeout up to 15s (shorter for shorter test fixtures), with phase
  controller enforcing deadline and Stop. Transfer connection timeout is now
  up to 10s; short latency probes keep their 3s timeout.
  No silent retry of failed payloads or switch to another test service.
- Partial download/ping retained, shared Complete / Partial results / Stopped /
  Failed state in banner/history, failed_phase/error_type, acknowledged upload
  bytes and chosen duration in reports. Historical 0.11 reports remain readable.
- Native original SpeedometerView uses actual live phase averages, a smooth
  arc/pointer and numerical Mbps, not simulated speed or copied Ookla artwork.
  SpeedometerScale supplies a monotonic logarithmic 0–5000 dial; the number is
  not clamped at the dial maximum. Accessibility description exposes phase/value.
- Screen focuses on the meter, elapsed time/data amount, duration selector,
  Start/Stop and three download/upload/HTTP-ping tiles. Explanations/provider
  link are collapsed. Recent results initially show three compact entries,
  expandable older history and tap-to-open/copy individual full reports.
- The app remembers the last chosen duration, disables changes while testing,
  and keeps the screen awake only while an in-app run is active. Backgrounding/
  rotation still requests stop. Empty/different-SIM histories reset the meter.
- Data warning updated everywhere for current source: no fixed MB cutoff; at
  1 Gbps, 20s is about 2.5 GB and 60s about 7.5 GB **per direction**, plus overhead.
  Confirmation before each run; server sees IP/generated traffic, not reports.
- Modem protocol, backup journal, boot plans and original backups unchanged.
  Location-disabled-only settings prompt and serving-band sampling retained.
- Checks passed: Android javac, existing six Java suites plus report/history
  tests, new slow upload response (>3s), timeout partials, no-ack handling, repeated
  transfers exceeding the old 100/20 MB cutoffs, bounded cancellation and meter
  scale checks. The design reference is docs/assets/speedtest-overview.svg
  (illustrative, not an Android screenshot). Phone runtime/accuracy checks
  remain pending. Reviewed a rendered SVG design reference; it is not Android
  rendering evidence. The subsequent explicit build request now authorizes
  packaging these follow-ups in version 0.12 / code 12.

## 0.12 phone failure and new investigation (2026-10-29)

The user reports another failed test. The new report has a Latency-stage outer
request deadline after roughly 6s, null HTTP latency/download/upload, zero sent,
received and acknowledged payload, and no Before download/Before upload sample.
Fresh registered n78 samples appear before latency and after the failed attempt;
a separate Android network refresh also reports NR/n78. Root helper never ran.
Do not publish the raw report or identifying/private timestamps/cell values.

The report identifies the app's outer 6s latency deadline, not the specific
DNS/TLS/server/transport cause. Increasing transfer timeouts in 0.12 did not
change the latency probe's fail-fast control flow. It is incorrect to claim
that the user's phone or Cloudflare universally cannot upload from this trace:
no throughput phase was attempted.

Source fix implemented and host-tested:

- Latency, download and upload now handle ordinary endpoint/measurement failures
  independently. A missing ping does not block throughput; an HTTP download
  failure does not block upload. All successful measurements remain available.
- Stop, interrupted controller and typed AbortException from subscription/route
  checks remain fatal, with no later phase or alternate network. The Android
  per-request route check now explicitly throws the fatal type; throttled checks
  still run during data transfer. Original cellular Network pinning preserved.
- Latency tries at most three probes within one 8s total budget, with 5s socket
  timeout. Median uses only returned samples; attempts/valid count recorded.
  Missing ping is null, never zero. If both rates complete without ping, the
  shared UI/history outcome is **Speeds measured · ping unavailable**, while full
  complete remains false and warnings remain in the report.
- Each phase owns its executor and active request set. A hung probe cannot hold
  workers needed by download/upload. Deadline/Stop cleanup and normal latency
  connection cleanup run on daemon closers; connection registration checks ended
  scopes so a delayed factory cannot start work after the phase ends. Normal
  throughput requests keep bounded connect/read timeouts. OS I/O can take time
  to close; do not claim an absolute network-stack interruption guarantee.
- Per failed phase, snapshot at most eight recent requests: method, fixed
  endpoint path, requested payload, current operation, elapsed time, HTTP status
  when received, and exception type. Operations distinguish connection factory,
  waiting for response headers (includes connection/TLS setup), upload stream,
  writing payload, body read and connection close. These do not separately time
  DNS versus TLS or prove the carrier/server caused a stall.
- New JSON fields: latency_budget_ms, latency_socket_timeout_ms, latency_attempts,
  latency_valid_samples, throughput_complete, aborted, phase_failures. Existing
  fields retained for old reports. No headers/tokens/URL nonce/private identifiers
  included in request diagnostics. No new provider or fallback network contacted.
- Host regressions pass for timeout/TLS failures, a probe ignoring interruption,
  blocked probe disconnect, absent ping with both rates, multiple phase failures,
  Stop during a hung probe/upload, typed route abort and existing budgets/rates.
- **Real HTTP integration** (`tests/test_speed_http.sh`, JDK17 jdk.httpserver,
  localhost only) passed: delayed zero-byte headers, real download/upload body
  transfer despite failed ping, HTTP 503 download with working upload, and Stop
  during an in-flight ping preventing both data phases. No carrier route or TLS
  measurement is inferred from loopback. Existing six Java suites plus report
  suite and new real HTTP suite (eight in total) and Android javac pass.
- Modem helper, SA/NR-only transaction, backup journal, boot plans, permissions,
  duration/data-use policy and meter unchanged. New source only; no APK, DEX,
  signing or release authorized. Published 0.12 APK stays unchanged.


## Approved 0.12 build checkpoint (2026-10-29)

The user explicitly authorized the latest APK after the timed-test, timeout and
meter source changes. App/report/QMI helper labels are 0.12, code 12; no modem
protocol behavior changed. README and guides now describe the packaged behavior.

- APK `sa-band-lab-0.12-arm64.apk`: **140,780 bytes**.
- APK SHA-256: `0db72d0e31c3d65a98817ad918a62dd4c47cd2d9cc7d42ba26d6112576738a83`.
- Helper SHA-256: `637696c6797fc48d2462ea561b7fb7cd6610d50555ee2315be18c052f962c4c0`.
- Signature scheme v3 and same signing certificate as prior official builds
  verified. Install over the prior app to preserve backups and device binding.
- Full build, AAPT2 linking, D8, zipalign, package/code/name/API/ABI, permissions,
  launcher/theme resources, boot components, ZIP integrity, SpeedometerView and
  SpeedometerScale plus speed-test classes in DEX, exact production ARM64 helper
  and 16 KiB ELF load alignment, no mock transport, README/notices/licenses pass.
- Seven Java/report host suites, all 50 native mock methods and transport checks
  passed. Earlier 0.11 and 0.10 APK hashes unchanged.
- Includes 20s/60s per direction without fixed MB cutoff; adaptive upload chunks;
  response/connect timeout improvements; partial results; original measured-
  average dial, result tiles, collapsed explanations and compact report dialogs.
- No new phone/emulator accuracy, upload, layout/rotation, lifecycle or routing
  evidence. The previous partial 0.11 run is still the latest real-device report.
- Published [v0.12.0](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.12.0),
  prerelease, not draft, target source `52b9a16010076a74b2418f870d7859eabb4e2fe9`.
  APK and SHA256SUMS.txt public; anonymous download exactly matches signed local
  bytes. README direct link and guides updated. Context completion follows the
  tagged source without changing its APK.
- Long timed tests may consume several GB; do not repeat the historical 0.11
  statement that every test is capped at 120 MB.

## Approved 0.11 build checkpoint (2026-10-29)

The user explicitly requested the APK after reviewing the speed-test feature.
Version code 11, Android report and QMI helper labels now say 0.11; no native
protocol behavior changed. README and UI/speed-test guides now describe 0.11
as the packaged version, with phone validation still pending.

- APK `sa-band-lab-0.11-arm64.apk`: **132,588 bytes**.
- APK SHA-256: `15c0efc46a920f9cbc286393b830b4496ffef3a828894451a7ba7ce226893c3d`.
- ARM64 helper SHA-256: `aab4c593116672df0789bafe43fd1d55d27fc303fe319f8252be5b38ea5b3814`.
- Same signing certificate as 0.10/0.9 (SHA-256 in signing record below);
  install over the old official app to retain backups and device binding.
- Complete Java/D8/AAPT2/zipalign/sign build passed, v3 signature verified.
  Package/code/name/API/ABI, new INTERNET/ACCESS_NETWORK_STATE permissions,
  launcher/theme resources, boot declarations, ZIP integrity, all four speed
  classes in DEX, exact production ARM64 helper and 16 KiB load alignment,
  no test transport, bundled README and licenses/notices checked.
- Seven Java/report host suites passed (six in test_five_g_bands.sh plus
  test_speed_reports.sh). All 50 native mock methods and transport checks passed.
  Existing 0.10 and 0.9 APK hashes remain unchanged.
- No phone/emulator speed, routing, Location prompt, serving sampling or runtime
  UI validation is claimed. Prior tiny Cloudflare endpoint smoke check is not
  proof of speed accuracy or selected-SIM routing on Android.
- Published [v0.11.0](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.11.0),
  prerelease, not draft; target source `844a87021b9fea43dddf270a751b068ff06e72bc`.
  APK and SHA256SUMS.txt uploaded. Anonymous public APK download matched the
  local signed bytes exactly; README download updated. This context completion
  follows the tagged build source without changing the released APK.

## Approved 0.10 build checkpoint (2026-10-29)

The user explicitly authorized building the completed icon and UI. Version/code,
report and QMI helper labels are now 0.10 / 10; the wire protocol is unchanged.
Beginner README instructions now follow Overview / Bands / Tools. Official build
uses the same certificate as 0.9; install over the old app to retain backups.

- APK: `sa-band-lab-0.10-arm64.apk`, **120,300 bytes**.
- APK SHA-256: `191ff3cd6f9dc889e811cf08e205ec05913510d82d6ec7c769d2ce6865b49afb`.
- Helper SHA-256: `9ac787f81dcaf1d34ef98fc706dc7543003d2c5da9ad1ee72dad87c6a9aeb545`.
- AAPT2 full linking, Java compilation, D8, zipalign, v3 signing and signature
  verification passed; code 10, API 30/34 and ARM64 metadata checked.
- Certificate matches the historical SHA-256 in the signing record below.
- All five Java host suites, 50 native mock methods and transport checks pass.
- APK ZIP integrity, exact production ARM64 helper, no mock-transport switch,
  new UI/boot classes in DEX, adaptive v26/v33 resources, theme references,
  packaged README and notices/licenses checked. The old 0.9 APK hash is unchanged.
- Android native-view rendering, navigation gestures, launcher appearance and
  font scaling are not runtime-tested; no phone/emulator was available.
- Published [v0.10.0](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.10.0),
  prerelease (not draft), target source `b266f3ec21f19317abb791b1f4a09de4e0bd1618`.
  APK and `SHA256SUMS.txt` uploaded; anonymous public download matches exactly.
  README download and beginner navigation instructions updated. This context
  publication checkpoint follows the tagged source without altering its APK.

## Release and signing record

| Item | Value |
| --- | --- |
| 0.9 release tag | `v0.9.0` |
| Release source commit | `3f11f776465f07342f7296a2da3875d0c721021e` |
| Implementation commit | `bc7108bc91b33c95b2cb60e55c3a2e05ce7d4ace` |
| Initial open-source implementation commit | `8b88557f58509f50708e9f051e291dba95b982a3` |
| APK | `sa-band-lab-0.9-arm64.apk`, 99,399 bytes |
| APK SHA-256 | `77afc643ca7717ca001ae39ad7e8cad22d4436d658a9930ea9b057d0178e2a44` |
| ARM64 helper SHA-256 | `19baf7f02a837b84c64a890efae620e17adb4d0e1eee92a0ac9f8ae548dc66b4` |
| Signing certificate SHA-256 | `c9d0695602ec1ede8245f7bcb39d3cf89ec9721927879b36b23d94c78d766fc7` |

The preceding [0.8 release](https://github.com/shadow-pheonix/NR-SA-Band-Locking/releases/tag/v0.8.0)
remains available. Its APK SHA-256 is
`342243e0582f5e3ef46368b6498bca856b25dc9fa6ec6873cf9bb523b2cb732a`.
Official 0.8/0.9 builds use the same private development signing certificate, so
the user installs over the existing app to retain private backups and the
signing-key-scoped device binding. Do not ask the user to uninstall merely to
work around an update issue without understanding the backup consequences.

Local 0.9 artifacts are in `dist/`, including APK, ARM64 helper,
`artifacts.json` and `SHA256SUMS.txt`. These outputs are ignored by Git. The
artifact manifest's `device_tested: false` describes its build-time validation;
later user feedback now confirms manual preference application. Do not rewrite
the already-released APK, checksum or tag to change this historical metadata.
Record subsequent evidence here and, when appropriate, in README/release notes.

Release validation completed: APK signature scheme v3 and expected certificate,
package/version/min SDK/ABI, boot receiver/service declarations, new boot classes
in DEX, exact ARM64 helper 0.9 bytes, no test-only mock transport, packaged
documentation/licenses, and public anonymous APK download matching the local
signed bytes. Source-only context updates after the release do not change its
tagged source or bundled README.

## Build, checks and environment details

[docs/BUILDING.md](docs/BUILDING.md) is the portable build guide. This project
uses Bash, GCC, Python, JDK 17, Android SDK platform 34 / Build Tools 35.0.0 and
Zig 0.13.0; it has no Gradle project. `build.sh` builds a static ARM64 helper,
compiles Java, runs D8/aapt2, packages notices, zipaligns, signs and verifies.
The ARM64 helper has 16 KiB load alignment. Preserve the existing signing key
for official upgrades; self-generated keys cannot update the official app.
The icon update compiles and links `android/res`; the 0.10 package now includes
the adaptive and themed launcher resources and native UI theme.

### Last completed checks

- **50 native mock test methods** passed: 15 DIAG and 35 QMI, plus native
  transport/framing/deadline checks. These run actual helper protocol code.
- Java host suites passed: `FiveGBandsTest`, `RadioModeSelectionTest`,
  `PreferenceJournalTest`, and `BootReapplyTest`.
- The UI update adds `UiPresentationTest`; all five host suites pass with the
  redesigned source. This covers Apply availability, staged changes, mask/mode
  labels, unknown versus empty observations, cached data and sample aging.
- Regressions cover post-reboot fresh baseline, original-backup preservation,
  real SA/NSA/mode races refusing SET, refused/uncertain journals, combined
  verification/rollback, boot cancellation and bounded preflight-only retries.
- Android source compilation and the full 0.9 APK build passed. Deprecated API
  compiler notes are expected; there was no compile error.
- The new 0.9 APK was signature/content-checked and downloaded from its public
  release URL with an identical hash. Phone evidence is described separately.
- 2026-10-29 icon-only source checks: AAPT2 compilation of all five icon XML
  resources, build-script syntax, resource references, vector/SVG consistency
  and visual previews. The released 0.9 APK hash is unchanged. These were source-only checks; full package linking passed in the approved
  0.10 build. Launcher rendering still needs a phone check.
- The subsequent UI source check compiles all six XML resources (icons plus
  theme), compiles the Android Java sources and reviews an illustrative design
  board. Full APK linking subsequently passed in 0.10; Android runtime/view tests remain pending.

Run relevant checks after changes; do not rerun everything or build an APK just
because a handoff document changed. Host mock tests may need permission to open
local Unix sockets in the managed environment.

Portable commands:

```sh
bash tests/run_tests.sh
bash tests/test_five_g_bands.sh
# Only when the user has authorized that build:
bash build.sh
```

### Existing managed workspace tool locations

These are conveniences for this environment, not portable requirements. Verify
they still exist in a fresh session; temporary paths can disappear.

| Tool | Local path |
| --- | --- |
| JDK 17 | `/tmp/dietlog-java` |
| Zig 0.13.0 | `/tmp/sa-build-tools/zig-linux-x86_64-0.13.0/zig` |
| Zig cache | `/tmp/sa-build-tools/zig-cache` |
| SDK-layout symlink fixture | `/tmp/sa-public-sdk` |
| Extracted SDK jars/tools | `/tmp/sa-build-tools` |

The SDK fixture supplies `platforms/android-34/android.jar` and
`build-tools/35.0.0` with aapt2, zipalign, D8, apksigner and runtime libraries.
Use it rather than assuming all tools are on PATH. The JDK installation has the
needed java/javac/keytool commands but is trimmed; do not assume `jar` exists.
`build.sh` packages its class archive with Python.

Examples for this environment:

```sh
SA_JAVA_HOME=/tmp/dietlog-java bash tests/test_five_g_bands.sh
/tmp/dietlog-java/bin/javac -source 8 -target 8 -Xlint:-options \
  -classpath /tmp/sa-build-tools/android.jar -d build/source-check \
  android/src/dev/saband/lab/*.java

# Only after build authorization. Reuse the private official keystore if present.
ANDROID_HOME=/tmp/sa-public-sdk \
SA_JAVA_HOME=/tmp/dietlog-java \
SA_ZIG=/tmp/sa-build-tools/zig-linux-x86_64-0.13.0/zig \
SA_ZIG_CACHE=/tmp/sa-build-tools/zig-cache \
SA_KEYSTORE='/home/coder/Cellular Pro/sa-app/.signing/debug.p12' \
bash build.sh
```

The keystore remains outside this public checkout in the historical private
workspace. Its path is not its contents. Never publish it, dump credentials or
put passwords in this context. An explicitly selected missing key must cause
a failure rather than silently replacing the signing identity. See the build
guide for `SA_KEYSTORE`, `SA_KEY_ALIAS` and `SA_KEYSTORE_PASS` handling.

### GitHub and environment workflow

Read applicable environment/AGENTS instructions at session start. Use available
GitHub connector tools for repository reads and source updates. The connected
account previously had push access, but verify actual operations rather than
assuming credentials or permissions remain current.

Source publication has used GitHub tree/commit/update-ref tools with an expected
parent SHA and no force-push. Inspect a changed branch head before retrying a
lease failure. A local fetch plus mixed reset was used only after verifying that
working files matched the published tree; never reset away unrelated edits.
The canonical checkout is a normal Git repository now, not the older untracked
source export from the initial publication.

The connector lacked binary release-upload tools, so `gh release create/edit`
was used for draft upload, verification and public prerelease publication.
Write multiline release notes to a file and pass `--notes-file`. No private key
or phone report is a release asset. Upload APK plus checksum, verify source/tag
and signing identity, publish, update README links and verify public download.

CLI networking and local mock sockets previously required execution outside the
default sandbox via the available approval mechanism. A sandboxed `gh auth
status` reported an invalid token while approved `gh api` operations succeeded;
do not dump tokens or start a new login just on that misleading result. Preserve
managed proxy/CA settings. Consult the cloud runtime skill if available; the
environment-status tool and `/etc/codex/network-policy.json` were unavailable
here, so do not invent their status or use them as evidence of access.

## Remaining work and next useful actions

The authorized 0.13 implementation, build, verification and public prerelease
are complete. Next: install over 0.12; select the intended SIM and read live
preferences; Tools → Configure 5G tile → choose bands → Save & use LTE only.
Add 5G to Quick Settings. Verify off reads mode 16 without changing band masks,
on reads mode 64 plus the chosen SA mask, and the other SIM/NSA/LTE masks remain
unchanged. Test lock-screen unlock, rapid taps, busy/speed-test refusal,
long press, root denial, reboot behavior and HyperOS display. Preference state
is separate from actual LTE/NR registration; refresh network after settling.
No tile/0.13 phone evidence has been received yet.

For speed-test validation, establish that a
failed/missing ping still reaches Before download/Before upload, and inspect
phase_failures/operations if a throughput phase also fails. Do not describe host
HTTP success as proof of Jio/Cloudflare/Android TLS behavior. The latest phone
evidence is the 0.12 latency-only failure; no full phone speed test has succeeded.

Also test native rendering/navigation, launcher appearance, font scale and
existing manual Apply/restore on the phone. Host checks cannot establish these
results. Do not overwrite existing releases or restart NSA development. Future
source changes need a new explicit build request.

If the user wants a reboot reapply test, have them enable **Reapply after
reboot** before a successful Apply, keep root granted, reboot/unlock, allow
time for the scheduled job, then read preferences and share a new report.
Inspect the saved boot plan, `last_boot_reapply`, operation
`boot_sa_and_radio_mode_apply`, SA mask and mode. Distinguish a historical
manual Apply record from an actual boot attempt and verify serving state
separately. Do not claim this test already happened.

Other unresolved validation includes live serving/registration after the latest
0.9 Apply, exact RF enforcement beyond reported preference state, restoration
on the phone, second-slot behavior and other SoC/OEM compatibility. Nearby-band
discovery needs a genuinely new evidence-backed measurement route; retrying the
same rejected frequency command is not progress.

When a new phone report, requirement, fix, test or release arrives, update the
current-state, evidence and remaining-work sections here as part of that work.
Keep the useful historical explanation while removing superseded instructions.
Fresh-session prompt: **"Read AGENTS.md and context.md in NR-SA-Band-Locking,
inspect the current workspace, and continue from the current state."**
