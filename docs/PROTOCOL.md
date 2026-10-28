# Protocol and implementation notes

The application package is `dev.saband.lab`. It contains Java UI code and a
root-executed ARM64 helper. The helper is packaged as
`lib/arm64-v8a/libsahelper.so` so Android extracts it to the native library path.

## QMI backend

- QRTR family 42, QMI NAS service 3 and DMS service 2.
- Discover version 1 / instance 0; reject ambiguous endpoints.
- NAS Bind Subscription 0x0045, TLV 1 = selected slot 0 or 1.
- NAS Get System Selection Preference 0x0034: SA TLV 0x2c, NSA TLV 0x2d,
  each exactly 64 bytes; RAT preference TLV 0x11, two bytes.
- DMS Get Band Capabilities 0x0045: NR TLV 0x13, counted u16 band numbers.
- Combined Apply: NAS Set System Selection Preference 0x0033, only duration
  TLV 0x17=0 (until power cycle), SA TLV 0x2f and RAT-mode TLV 0x11.
- NR-only mode is RAT bit 6, value 0x0040. Leaving NR-only restores the captured
  earlier mode; no automatic-mode constant is guessed.
- Strict framing, result/transaction/message matching, duplicate TLV rejection,
  bounded traces and receive deadlines. QRTR sends use flags 0 because the
  kernel rejects MSG_NOSIGNAL for this socket family.

Apply compares expected SA/NSA and mode before SET. Both target values must match
readback, while reported NSA/LTE/legacy and other guarded values remain unchanged.
On failure the helper reopens/binds and attempts to restore both prior values,
then verifies them. A failed rollback is reported as an uncertain outcome.
One combined request does not establish atomic modem execution.

## Backups and reports

Private schema-2 backup files retain original and expected SA values, the
original NSA value, pending targets and optional radio-mode restoration fields.
They are atomically written before mutation and bound to device/firmware/baseband
and Android ID. No captured phone backups are included in this repository.

The most recent preference-change response is retained independently of later
reads. EFS diagnostic results and modem frequency results use separate report
fields, so a failed optional read does not replace an earlier apply trace.

## Neighbor limitations

Android CellInfo can expose registered and unregistered NR cells. The tested
firmware exposed only its registered n78 cell. The optional QMI GET_ARFCN_LIST
0x00d2 experiment expects optional serving 0x1c / neighbor 0x1d lists, each a
u8 count followed by u32 NR-ARFCNs. The phone rejected that query with result 1,
error 25 (`DeviceUnsupported`); this is not an empty neighbor list.

Frequency candidates preserve overlapping bands rather than picking one.
Their table currently covers the tested phone's 16 reported bands. A frequency
list can contain configured measurement targets; it does not prove usable
coverage or provide neighbor signal strength. No full RF scan or Qualcomm NR
DIAG measurement backend is implemented.

## Legacy EFS research

The UI's optional EFS check is read-only. The helper retains fixed-path EFS
research commands, but normal Apply/Restore uses QMI. The tested phone returned
errno 2 for `/nv/item_files/modem/mmode/nr_band_pref`; the app does not create a
guessed replacement or use that failure as an all-band backup. The legacy CLI's
EFS mutation commands are not part of the ordinary app usage instructions.

References and preserved licenses are listed in
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
