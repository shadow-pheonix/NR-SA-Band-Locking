# Signal hunt — Gauge

**Included in SA Band Lab 0.15.**
The Signal tab sits between Bands and Speedtest. It reads Android's serving
NR measurements for the selected SIM without changing modem preferences.

## Find a better spot

1. Select the SIM you want to measure. Make sure it has usable 5G service;
   a SIM in LTE-only mode will not provide a serving NR reading.
2. Open **Signal → Start hunt**. Enable Location if prompted and grant Phone
   and Precise location access. The app opens Location settings only when the
   system switch is off; return and press Start again after enabling it.
3. Move slowly and hold the phone in a consistent position. The large number is
   **SS-RSRP in dBm**: −85 is stronger than −105. The segmented gauge spans
   −140 to −60 dBm; a valid reading outside that scale still shows its actual
   numeric value.
4. Compare **Best this cell**, the change from the first reading for this cell,
   and the last **60 seconds** of strength. A different serving cell, band or
   channel starts a new comparison. Missing identity information disables
   best/delta/mark comparisons instead of assuming two cells are the same.
5. Tap **Mark spot** to keep a fresh reading for comparison. Up to 20 marks
   stay in this session, newest first, with band/PCI and available quality
   readings. They have no GPS coordinates. Rotation retains the paused
   session; switching SIMs or starting a new app process clears it.
6. Optionally turn **Sound on**. New fresh strength readings produce short
   chirps, with higher pitch for stronger signal. Sound starts off, respects
   the phone's silent/vibrate setting and stops when hunting pauses.
7. Use **Pause hunt** or leave the tab to stop sampling. Returning to the tab
   or app requires **Resume hunt**. The screen stays awake only while hunting
   is active in the foreground. Run a separate Speedtest at promising spots.

## Read the meter honestly

- **SS-SINR** and **SS-RSRQ** are separate quality measurements, not values
  inferred from strength. Stronger SS-RSRP alone does not guarantee faster data.
- Every sample uses its modem observation timestamp. More than five seconds
  old means stale: current values become dashes, with the last cell context
  labeled **Last**. Duplicate callbacks do not create trend points, refresh
  the observation time or replay sound. The age remains visible.
- The graph uses actual observation times and leaves gaps when measurements
  are missing or paused. It does not invent a smooth one-second data stream.
- Android can delay or rate-limit updates. A continuous selected-subscription
  listener is supplemented with requests at least five seconds apart; an
  eight-second request timeout does not discard a still-fresh listener reading.
- Only a unique fresh registered primary NR cell, or a sole fresh registered
  NR cell, is selected. Ambiguous/neighbor-only results are unavailable rather
  than choosing the strongest unrelated cell. This is not a nearby-band scan,
  tower-direction finder or complete carrier-aggregation display.
- No root operation or modem write is needed for this screen. The app's band
  controls still need their existing root access and compatible Qualcomm modem.

## Validation

Host tests cover freshness, duplicate/out-of-order callbacks, missing identity
and measurements, cell changes, graph gaps and limits, marks, gauge scale and
saved-tab migration. Full Android Java and minimum-API-30 DEX compilation are
checked separately. These checks do not execute Android callbacks, drawing,
sound or HyperOS lifecycle behavior. Phone validation remains pending.

Android references: [cell timestamps](https://developer.android.com/reference/android/telephony/CellInfo),
[NR signal measurements](https://developer.android.com/reference/android/telephony/CellSignalStrengthNr),
[subscription-bound cell updates](https://developer.android.com/reference/android/telephony/TelephonyManager).
