# Contributing

Bug fixes, protocol validation and carefully documented device reports are
welcome. Please keep changes narrow and preserve the separation between SA band
control, RAT-mode control and optional observation sources.

Before submitting changes, run:

```sh
bash tests/run_tests.sh
bash tests/test_five_g_bands.sh
```

For device compatibility reports, provide the phone model, SoC/modem if known,
Android/ROM version, root availability, app version and the operation tested.
Distinguish successful reads, verified preference changes, restore results and
observed live RF behavior. A device working for reads is not automatically
verified for all writes or for NR-only voice service.

Diagnostic reports may contain device-binding identifiers, cell/operator data
and firmware information. Redact private identifiers before posting an issue.
Never upload signing keys, credentials, personal backups, proprietary APKs or
vendor firmware dumps. Synthetic regression fixtures are preferred.

Preserve BSD notices in the vendored transport and document any modifications
to those files in its PATCHES.md. Original contributions use the project's MIT
license; protocol reference material is not permission to copy unrelated code.
