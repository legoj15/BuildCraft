# Handoff — 2026-11-15 (2026.4.0, the first public Fabric release)

The previous handoff (2026-11-05, #98 review round) is in git history; that work is merged.

## Done
- `mod_version=2026.4.0` on `main` (`8672bea1a`, not pushed).
- The release tool in the gitignored `release/` folder now ships all 12 nodes (6 NeoForge, 6 `-fabric`):
  - Each loader has its own CurseForge loader name and relations, and its own Modrinth loaders and dependencies (config `release/config/release.json`).
  - NeoForge: optional jei, rei, guideme. Fabric: required fabric-api and forge-config-api-port; optional jei, rei, modmenu.
  - Modrinth version numbers are `<ver>+mc<mc>-fabric` for Fabric; Fabric store titles say "(Fabric)".
  - The in-game manifest gets NeoForge MC versions only. Mod Menu checks Modrinth by jar hash, so Fabric needs no manifest.
  - CurseForge changelogs are per MC version (`Get-BcStoreChangelogForMc`); Modrinth keeps one unified text.
  - Review fixes: malformed `{tag}` lines throw, a header-only changelog no longer crashes, PostFlight resume works, `#requires -Version 7`.
  - Details are in `docs/delegation-log.md` (2026-11-15 line) and `release/README.md`.
- A dry run (`./release/Invoke-Release.ps1 -SkipTests`) lists 12 nodes and stops only on missing 2026.4.0 jars.

## Next
1. **Build the jars** (user asked for this; not started because of the curfew): `./gradlew buildAndCollect` into `build/libs/2026.4.0/`. Expect 12 jars. Do it in a worktree, or check nobody else is using the main checkout (see the shared-checkout memory).
2. Dry-run the release again with the jars present and read the CurseForge and Modrinth payloads.
3. **Wait for the user to say when** before running the suite (`fullTestSuite`, the release Test stage).
4. Publish only on the user's word: `./release/Invoke-Release.ps1 -Execute`.

## Open questions and risks
- The CurseForge slugs `forge-config-api-port`, `guideme`, `modmenu` and `roughly-enough-items` are unverified (CurseForge is Cloudflare-blocked for scripts). A wrong slug fails at upload, so the first `-Execute` may need a resume with `-Only CurseForge,Modrinth,PostFlight`.
- The Modrinth dependency entries send `version_id: null` and `file_name: null`. This is unverified but matches the old JEI entry that already uploaded fine.
- The final round of fixes to the release tool had no third-party re-review (glm-flash was at its weekly quota; shop26 was busy).

## Uncommitted changes
None in the tracked tree. `release/` is gitignored on purpose.
