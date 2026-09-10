---
name: neoforge-bump
description: Bumping NeoForge on any Stonecutter node in BuildCraft — use when the SessionStart version-check hook reports a node behind upstream, or the user asks to update/bump NeoForge or minecraft_version. Covers the changelog fetch, the neutral/beneficial/cautionary classification against todos.md, the per-node pin edit, the .neoforge-ref sources re-sync, post-bump testing, and the fresh-clone settings.json hook wiring.
---

# Bumping NeoForge in BuildCraft

NeoForge for Minecraft is constantly updating. `neo_version` is the pin, and it is **per-node**
(`versions/<node>/gradle.properties`) — each node tracks its own line independently. The
version-check hook reads every node's pin directly (it does not depend on any root
gradle.properties mirror).

## Awareness — the SessionStart hook

`.claude/settings.json` registers a `SessionStart` hook that runs
`scripts/neoforge-version-check.sh`: it enumerates every `versions/<node>/gradle.properties`,
derives that node's NeoForge line from its pinned `neo_version` by stripping the trailing
`.<build>` (deriving the line from `minecraft_version` would only line up for the 26.1.2 node —
the MC→NeoForge mapping is non-uniform across the CalVer cliff: MC `1.21.1`→NeoForge `21.1.x`,
MC `26.2`→NeoForge `26.2.0.x`), fetches NeoForge's `maven-metadata.xml` once, and — only when one
or more nodes are behind — injects a notice listing each behind node into the session. Silent
when all nodes are current; fails silently when offline. Check manually anytime with
`bash scripts/neoforge-version-check.sh --plain`.

## When behind — review, classify, offer the bump

Every build publishes a *cumulative* changelog at
`…/neoforge/<version>/neoforge-<version>-changelog.txt`. Fetch the **latest** version's
changelog, read the entries above the pinned `neo_version`, cross-reference `todos.md`, then
classify the delta for the user:

- **Neutral** — nothing BuildCraft touches; just note the update exists.
- **Beneficial** — a new API/event/hook that unblocks a `todos.md` item or enables an
  optimization; name the relevant bullet.
- **Cautionary** — a deprecation, removal, signature change, or restructuring in an API
  BuildCraft *does* use; identify what breaks *before* bumping.

Then offer the bump.

## Bumping

1. Edit `neo_version` (and `minecraft_version`, if it moved) in the bumped node's
   `versions/<node>/gradle.properties`.
2. `./gradlew :<node>:compileJava` — a Gradle sync so ModDevGradle regenerates artifacts for
   the new version.
3. `bash scripts/neoforge-sources-sync.sh` — refresh `.neoforge-ref/` (below).
4. Build and test; fix whatever the changelog flagged **cautionary**.
5. No `changelog.md` entry for a bump unless it changes player-facing behavior.

## `.neoforge-ref/` — decompiled API reference

`.neoforge-ref/` (gitignored; populated by `scripts/neoforge-sources-sync.sh`) holds reference
sources for **every active Stonecutter node's** pinned versions — one dir-set per MC line, kept
side by side (so a future 1.21.11 node's sources sit next to 26.1.x's, with no re-checkout on
node switch). It is the first place to look when you need to know how to call a NeoForge or
vanilla API — grep it rather than recalling signatures from memory; the 26.x API lines are new
and churn constantly. **Grep the dir-set for the line you're working on — never across
versions** (the APIs differ by line, which is the whole point of the split);
`.neoforge-ref/INDEX.txt` maps each node to its dirs.

- `.neoforge-ref/sources-<neo_version>/` — decompiled `.java`, one Grep/Read root:
  `net/minecraft/**` (patched Minecraft), `net/neoforged/**` (FML, capabilities, registries,
  events, attachments), `com/mojang/**` (blaze3d, datafixers, …).
- `.neoforge-ref/vanilla-<minecraft_version>/<minecraft_version>.jar` — the pure, unpatched,
  deobfuscated vanilla client jar. Bytecode, not source — inspect with `javap` (e.g.
  `javap -p -cp .neoforge-ref/vanilla-26.1.2/26.1.2.jar net.minecraft.client.Camera`).
  Authoritative ground truth for unpatched vanilla.

Dir names are version-stamped (`sources-<neo>`, `vanilla-<mc>`). The sync reads each
`versions/<node>/gradle.properties`, keeps one dir-set per active node, and prunes only
versions no node pins anymore — so adding a node *adds* its dirs without disturbing the others.
If the dirs don't match the current nodes — or `.neoforge-ref/` is absent — run the sync
(~1 min per new version; present dirs are reused). A node whose Gradle artifacts aren't built
yet is skipped with a note, not an error.

## Fresh clone: recreating the SessionStart hook

`.claude/` is gitignored **except `.claude/skills/`** (which is tracked), so
`.claude/settings.json` — which registers the hook — is not version-controlled (the `scripts/`
are). Recreate it after a fresh clone:

```json
{
  "hooks": {
    "SessionStart": [
      {
        "matcher": "",
        "hooks": [
          { "type": "command", "command": "bash scripts/neoforge-version-check.sh", "timeout": 15 }
        ]
      }
    ]
  }
}
```
