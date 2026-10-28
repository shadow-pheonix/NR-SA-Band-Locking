# Instructions for work in this repository

## Required session context

1. **Read [context.md](context.md) before investigating, changing code, building,
   or preparing a release.** It contains the user's decisions, device evidence,
   current release and unresolved work. Also inspect Git status and current
   source; a handoff document is not proof that the workspace has not changed.
2. **Keep `context.md` updated frequently without waiting for the user to ask.**
   Update it after a meaningful requirement change, investigation finding,
   implementation milestone, test result, device report, build or release.
   During long work, update it at each major checkpoint. Always reconcile it
   before ending a substantive task or handing work to a fresh session.
3. Edit the current-state sections in place, retain useful history, and record
   the update date, version, evidence, remaining work and relevant commands.
   Distinguish implemented, host-tested, phone-confirmed and still unverified.
   Include context updates with the related source commit when publishing.
4. Never copy credentials, keystore contents/passwords, Android IDs, SIM
   identifiers, private phone reports or proprietary APK/code into this public
   context. Summarize diagnostic evidence without identifying the device owner.
5. Updating documentation does **not** authorize building or releasing an APK.
   The user prefers reviewing source changes before a new build: ask after the
   changes and checks are ready unless the current request already explicitly
   authorizes that build. Earlier approvals covered their specific builds.

## Project requirements to preserve

- This app controls **5G SA only**. Read NSA/LTE to verify preservation; do not
  add NSA writes or restart NSA development without a new user request.
- NR-only and band selection are staged together and applied by the same Apply
  button. Do not restore the old immediately-applied NR-only checkbox behavior.
- Preserve original backups. Refresh expected state from live reads, and retain
  the helper's compare-before-SET check, verification and rollback behavior.
- Reboot reapply is explicit opt-in, off by default, with bounded boot-time
  execution. Do not silently make permanent modem/NV writes.
- Do not describe supported bands or configured frequencies as detected local
  coverage. Neighbor discovery remains unresolved on the tested firmware.
- Retain third-party licenses. No Cellular Pro proprietary component belongs in
  this repository or its APKs.
