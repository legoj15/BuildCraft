###### Changes since 2026.1.0-rc1:
- **Fixed a crash that stopped dedicated servers from starting.** A client-only texture lookup was being reached on the server thread moments after "Done", hard-crashing any real (non-dev) dedicated server. Servers now boot normally. A development `runServer` never hit this because it bundles the client classes a true server lacks.
- Stopped the five dev-only "decorated" blocks from spamming the server log with missing-item loot-table errors at every world load.
