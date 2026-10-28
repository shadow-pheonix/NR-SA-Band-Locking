Vendored from the commit recorded in ORIGIN.json, under BSD-3-Clause.

Local changes in diag.c:

- Reject short transport writes.
- Require the complete HDLC terminator and QRTR wrapper length/terminator.
- Match generic DIAG errors to the echoed request command.
- When a full subsystem header is echoed in an error, also match its subsystem
  and operation so another EFS request's error is not accepted as our response.
- Accept QRTR replies only from the selected node and port.
- Use a monotonic clock and interrupt blocking diagchar reads at their request
  deadline using SIGALRM without SA_RESTART. The helper is single-threaded and
  owns no other timers.

The SA application's own EFS controller implements only fixed-path NR preference
GET/PUT operations. It does not include the upstream general EFS daemon.
The independent QMI NAS/DMS backend in ../../qmi_sa.c shares the string/hex
utilities but has its own protocol and transport implementation.
