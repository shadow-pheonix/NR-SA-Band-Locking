# UI and navigation redesign

Version **0.10** packages this interface and the new launcher icon. Full Android
rendering and device interaction still need phone testing. Install over the
previous official APK to retain backups.

![UI design reference](assets/ui-overview.svg)

The image is an illustrative design reference with example data, **not Android
screenshots or evidence of phone behavior**. The Bands panel is shown at the
selection area; its read/backup card is above it in the scrollable page. The
real grid contains only verified selectable bands for the chosen phone/SIM.

## Three destinations

| Destination | Purpose and actions |
| --- | --- |
| **Overview** | Refresh Android cell observations; see the reported serving band, RSRP, SINR, NR cell count and nearby bands. Check modem preferences separately. Expand cell details when needed. |
| **Bands** | Read live preferences and capture/verify the backup. Select SA bands in a responsive grid, use Select all or Clear selection, and stage NR-only/reboot options. Review the selection count and use the one Apply action. |
| **Tools** | Export/import backups, restore captured SA preferences, disable boot reapply, copy diagnostics, inspect phone capabilities and open network settings. Optional modem-frequency and legacy EFS reads sit under Advanced modem checks. |

The header keeps a two-button SIM selector visible. The bottom navigation keeps
all three destinations one tap away. Pages retain their views and scroll state
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

## Clear observation and permission states

Before a refresh, unknown values display a dash rather than fabricated numbers.
An actual empty cell list can report zero. Neighbor cells are never promoted to
the serving-band hero. Cached results are labeled because they may contain
other SIMs; sample ages continue to advance locally, without background modem
queries. Samples older than 30 seconds become previous observations.

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

The signed 0.10 APK build passes, including Java/D8 and full AAPT2 resource
linking. Package inspection confirms the adaptive/themed icon and theme. The five Java
host suites cover the existing observation/radio/journal/boot logic plus the
new Apply gates, draft detection, mask labels, and fresh/stale/cached/neighbor
presentation. No native helper protocol code changed. These checks do not
execute Android views or prove runtime layout, focus order, launcher behavior,
font scaling or navigation on the phone. Review those using the 0.10 APK.

Android design references:
[navigation patterns](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns),
[window insets](https://developer.android.com/reference/android/view/WindowInsets),
[touch target guidance](https://support.google.com/accessibility/android/answer/7101858).
