# UI and navigation redesign

Version **0.10** packages the original three-tab redesign and launcher icon.
Version **0.11** adds **Speedtest between Bands and Tools**. Android rendering and device interaction still need
phone testing. Install official APK updates over the previous app to retain
backups.

Version **0.12** adds a native speedometer, three result tiles,
a duration selector, collapsed explanations and compact tap-to-open reports.
[Speedtest preview](assets/speedtest-overview.svg) is illustrative, not an Android
screenshot. Full source behavior is described in [SPEEDTEST.md](SPEEDTEST.md).

![UI design reference](assets/ui-overview.svg)

The image illustrates the earlier 0.10 three-tab layout with example data, **not Android
screenshots or evidence of phone behavior**. The Bands panel is shown at the
selection area; its read/backup card is above it in the scrollable page. The
real grid contains only verified selectable bands for the chosen phone/SIM.

## Four destinations in current source

| Destination | Purpose and actions |
| --- | --- |
| **Overview** | Refresh Android cell observations; see the reported serving band, RSRP, SINR, NR cell count and nearby bands. Check modem preferences separately. Expand cell details when needed. |
| **Bands** | Read live preferences and capture/verify the backup. Select SA bands in a responsive grid, use Select all or Clear selection, and stage NR-only/reboot options. Review the selection count and use the one Apply action. |
| **Speedtest** | Run a confirmed cellular-only download/upload/HTTP-ping test. Stop a run, compare local reports for this SIM, copy reports or clear history. Every result carries serving-band observations from during the run. |
| **Tools** | Export/import backups, restore captured SA preferences, disable boot reapply, copy diagnostics, inspect phone capabilities and open network settings. Optional modem-frequency and legacy EFS reads sit under Advanced modem checks. |

The header keeps a two-button SIM selector visible. The bottom navigation keeps
all four destinations one tap away. Pages retain their views and scroll state
when switching tabs, so tab changes do not reset checkbox choices or initiate
root/network operations. Back from Bands or Tools returns to Overview.

## Band selection and recovery

- A live read and matching backup are required before selecting/applying bands.
  Missing capabilities, invalid mode, busy state or zero selection disable Apply.
- Selection count and **UNAPPLIED** indicate staged changes. NR-only and reboot
  switches only edit the draft; the existing Apply confirmation and combined
  SA/mode helper request perform the change.
- The Apply footer stays visible in normal portrait layouts. On short windows
  it moves into the scrollable content so it cannot consume the entire page.
- Changing SIM or leaving the app with an unapplied draft asks whether to
  discard it. Merely switching tabs preserves it.
- An explicit reread preserves unapplied choices that remain selectable. After
  activity recreation, the page/slot/draft are remembered, but a fresh read is
  required before re-enabling writes and restoring the choices for review.
- Failed Apply retains choices for review after a successful reread; it does
  not automatically retry a write. Native compare-before-SET, verification,
  rollback, original backups and boot-plan rules remain in effect.
- Restore keeps the captured-byte behavior and confirmation. It lives in Tools
  with an explanation that the current radio mode is preserved.

## Speedtest and history

See [SPEEDTEST.md](SPEEDTEST.md) for the beginner flow and measurement details.
The source adds a timed 20s/60s four-stream cellular benchmark, explicit data-use confirmation,
a Stop action, measured-average dial, result tiles and compact local history.
Successful prior phases remain visible as Partial results after an upload failure.
Long timed tests have no fixed MB cutoff and may consume several GB; the
confirmation and expandable details explain this. Unapplied band choices block testing.
The selected SIM must be both the default and active data SIM; each HTTP request
is pinned to the same cellular Network. Route/SIM changes fail the run.
Results are labeled incomplete when cancelled or failed, retaining any completed
measurements for diagnosis. The modem helper and backup protocol are unchanged.

## Clear observation and permission states

Before a refresh, unknown values display a dash rather than fabricated numbers.
An actual empty cell list can report zero. Neighbor cells are never promoted to
the serving-band hero. Cached results are labeled because they may contain
other SIMs; sample ages continue to advance locally, without background modem
queries. Samples older than 30 seconds become previous observations.

On Refresh network or Start speed test, a disabled system Location switch
triggers a dialog with **Open Location settings**. It opens Location settings;
return and tap the action again. If Location is already enabled, no Location
switch dialog appears. Permission prompts are separate and only shown if needed.

Before requesting Phone/Precise location, the app explains Android's cell-info
requirement and that the app does not request GPS coordinates or upload location.
Root preference control remains separate from Android cell-observation access.

The operation status stays visible on every page, with a progress indicator
while work runs. Tap it for the full message and a Copy report action. Optional
legacy EFS errors explain that they do not establish a failed QMI band lock.

## Visual and accessibility details

- Midnight navy surfaces, mint primary actions, restrained borders and grouped
  cards match the launcher icon. Restore uses an amber text accent.
- Buttons, SIM targets, switches, status details and tab targets have generous
  touch areas. Native checked/selected state and accessibility announcements
  are retained; status messages use a polite live region.
- Insets protect content from system bars and cutouts. The band grid grows to
  four columns on wider windows. Metrics stack for large font scaling, labels
  can wrap, and short windows do not reserve a fixed Apply panel.
- Native ripple feedback and a brief tab fade make actions visible without
  requiring a new UI framework or build dependency.

Implementation: [LabLayout.java](../android/src/dev/saband/lab/LabLayout.java)
contains views/navigation, [UiPresentation.java](../android/src/dev/saband/lab/UiPresentation.java)
contains host-testable UI decisions, and
[MainActivity.java](../android/src/dev/saband/lab/MainActivity.java) retains the
existing operation/controller boundary. The Android theme is in
`android/res/values/styles.xml`.

## Validation and remaining checks

The signed 0.12 APK build passes, including Java/D8 and full AAPT2 resource
linking. Package inspection confirms the adaptive/themed icon and theme. The original five Java
host suites cover the existing observation/radio/journal/boot logic plus the
new Apply gates, draft detection, mask labels, and fresh/stale/cached/neighbor
presentation. No native helper protocol code changed. These checks do not
execute Android views or prove runtime layout, focus order, launcher behavior,
font scaling or navigation on the phone. Review those using the 0.12 APK.

The speed-test source additionally passes transfer-loop and report/history host
tests and Android Java compilation. A tiny live 1 KB download/upload smoke check
passed against Cloudflare. Android cellular routing, Location settings prompts,
serving updates, actual speed accuracy and lifecycle behavior still require a
phone test; host mocks do not establish those results. Speedtest was introduced
in 0.11; the meter and sustained-test revisions ship in 0.12.

Android design references:
[navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns),
[window insets](https://developer.android.com/reference/android/view/WindowInsets),
[touch target guidance](https://support.google.com/accessibility/android/answer/7101858).

The unreleased phase-isolation follow-up retains this layout. Its shared outcome
label reads **Speeds measured · ping unavailable** if both rate phases finish
without latency, and keeps missing ping as a dash. Full reports add per-phase
HTTP-operation diagnostics. This source fix is not in the 0.12 APK yet.
