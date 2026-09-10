"BuildCraft Unofficial" is a NeoForge port of the original 1.12.2 and 1.7.10 Minecraft Forge BuildCraft

The project is a mix of Java 25 and Java 21 out of necessity.

## Build Commands

```bash
# BuildCraft builds with Stonecutter: ONE source tree, one node per MC LINE.
# Five nodes today: 1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2 (26.2 is the primary).
# Tasks are per-node — qualify with :<node>:. Unqualified tasks run across all nodes.
# `./gradlew fullTestSuite` (root) runs unit + game tests + jar for EVERY node.

# Compile the node (fast feedback)
./gradlew :26.1.2:compileJava

# Build the jar(s) and collect into build/libs/<mod_version>/ (one jar per node, e.g. +mc26.1.2)
./gradlew buildAndCollect

# Run the client / server
./gradlew :26.1.2:runClient
./gradlew :26.1.2:runServer

# Unit tests / headless game tests
./gradlew :26.1.2:test
./gradlew :26.1.2:runGameTestServer
```

## Multi-version builds (Stonecutter)

Read docs\stoncutter.md before touching the Stonecutter system or when having issues with it. When and when not to cause node separation:
- Do separate nodes if methods and functions no longer exist or are deprecated
- Don't separate nodes if a shareable, non-deprecated solution, is available

## Architecture

Avoid using deprecated functions or API systems that throw warnings. This may result in *technically* unnecessary node separation, but ideally keeps the code compatible with newer NeoForge versions/Minecraft releases.

### History — this is one mod, not eight

The 1.12.2 (8.0.x) and 1.7.10 (7.1.x) versions we're based on used a coremod and submod architecture. This is no longer clean in modern NeoForge (all 9 mods would show up separately), so all the submods were collapsed into subsystems of one monolithic mod. So submods such as `energy`, `silicon`, `transport`, `api`, `lib`, `factory`, `builders`and `robotics`. are all just organized compartments of the same "core" mod now.

### Initialization Pattern

`BCCore` is the single `@Mod` entry point. It calls a per-subsystem `BC{Name}.init(modEventBus)` (e.g. `BCTransport.init`, `BCEnergy.init`) which registers that subsystem's `DeferredRegister<T>` instances onto the mod event bus. These `BC{Name}` classes are registration helpers grouped by topic — they are *not* sub-mods. Client-only code lives in `BC{Name}Client` classes, instantiated only on `Dist.CLIENT`.

Initialization order: `BCLib` → core registries → per-subsystem registries → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

### Key Subsystems

**Tiles**: every BC block entity sits on a thin shared hierarchy in `lib.tile`. `AbstractBCBlockEntity` hosts the cross-version save/load directive (subclasses override version-neutral `writeData`/`readData` — never `saveAdditional` directly), owner persistence (`OwnerData` + `onPlacedBy`), the menu-open/`canInteractWith` machinery, and the `dropContentsOnRemoval` hook. `AbstractBCSyncedBlockEntity` adds the standard `getUpdateTag`/`getUpdatePacket` sync pair (with the `<1.21.10 onDataPacket` fix) plus `markForRenderUpdate()`/`markForGuiUpdate()` — the no-re-mesh data push; use these for BER/GUI-only state, and `sendBlockUpdated` only when the *block model* genuinely changes. `TileBC_Neptune` (item handler management, player tracking) and the bespoke-state machines (engines via `TileEngineBase_BC8`, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`, `TileFloodGate`, `TileLaser`) extend the synced base; `TileMarker`, `TileSpringOil`, and `TilePipeHolder` stay on the plain base because they sync through their own channels — do not give them the standard pair. Block-side boilerplate (owner forward, menu open, ticker, `<1.21.10 onRemove` catch-all) lives once in `lib.block.BlockBCTile_Neptune` / `BlockBCTile_Directional` (+ shared `BlockBCTileSupport`); engines have their own `BlockEngineBase_BC8`. Every `BlockBCTile_Neptune` subclass must declare its own `getRenderShape -> RenderShape.MODEL` — the base doesn't supply it, and `BaseEntityBlock` defaults it to `INVISIBLE` on the 1.21.1 node only (dropped at 1.21.10), so omitting it compiles clean and looks correct everywhere except that one node; enforced by the `BlockRenderShapeTester` game test, which walks the live block registry. Capability attachment uses NeoForge's `RegisterCapabilitiesEvent`; MJ-consumer machines register through the `MjCapabilities.registerMjConsumer` bundle.

**Menus/GUI**: `ContainerBC_Neptune` is a plain `AbstractContainerMenu`; the shared shift-click/phantom-slot/widget-message machinery lives in `BCContainerSupport` behind the `BCContainer` interface. Tile-backed menus extend `ContainerBCTile<T extends AbstractBCBlockEntity>` (resolves the tile via `resolveTile`, reach-checks via the tile's `canInteractWith`). Only the two real recipe-book tables (AutoWorkbench, Advanced Crafting) extend `ContainerBCCrafting` (`RecipeBookMenu`-rooted; hosts the version-forked recipe-book stub block once). Menu-opening tiles implement `IBCMenuProvider` — `writeClientSideData` writes the `BlockPos` on every open path (spectator-safe); never route the pos through the two-arg `openMenu(provider, pos)` overload (double-write).

**Pipes/Transport**: Each pipe is a composition of a `PipeBehaviour` (determines pipe type logic) and a `PipeFlow` (handles what flows through it — items, fluids, power). `PipeRegistry` and `PipeDefinition` manage pipe types.

**Statements/Logic** (Gates): `IStatement`, `ITrigger`, and `IAction` interfaces with `StatementManager` as the central registry. Used by silicon gates to wire conditions to actions. Note: statements do **not** use `DeferredRegister` — `StatementManager` is a hand-rolled static registry and each `BCStatement` self-registers in its constructor (do not add redundant `registerStatement` calls). This is correct: statements aren't registry content.

**Snapshots/Blueprints**: The builders module uses a snapshot system under `buildcraft.builders.snapshot`. Schematics (`ISchematicBlock`, `ISchematicEntity`) define how blocks/entities are saved and placed. `SchematicBlockFactoryRegistry` and `SchematicEntityFactoryRegistry` map block/entity types to their schematic factories. `GlobalSavedDataSnapshots` persists snapshot data server-side.

**Networking**: Custom payloads using NeoForge's `RegisterPayloadHandlersEvent`. Payloads use `StreamCodec<RegistryFriendlyByteBuf, T>` for serialization. Handlers call `ctx.enqueueWork()` to execute on the logical thread.

**Config**: Two specs — `BCUnifiedConfig` (COMMON) and `BCUnifiedClientConfig` (CLIENT) — each wrap the per-subsystem configs into one `ModConfigSpec` using `.push()/.pop()` sections. The COMMON/CLIENT split is load-bearing (client-only render/display options must live on the CLIENT spec).

**Recipes**: `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

### Conventions

- Deferred registries: use `DeferredRegister<T>` and `DeferredHolder<R,T>` — never direct registry access.
- Blocks/items/BEs for each subsystem live in `BC{Name}Blocks`, `BC{Name}Items`, `BC{Name}BlockEntities`.
- Event listeners use `@SubscribeEvent` on `@EventBusSubscriber`-annotated classes, or `modEventBus.addListener()` in constructors.
- Files with a `.disabled` extension were created from the initial forking of 1.12.2 to the first modern port on version 1.21.11. They exist as references to how the 1.12.2 version functioned, but can be safely deleted once their functionality has been either 1:1 ported or recreated in a new, updated, and/or improved way.
- **Copyright headers track the FILE's text, not the feature's idea.** Copyright protects expression, never concepts — so a brand-new file that merely *implements the same feature* as an upstream one is new, and gets no upstream notice. Never clone a neighbouring file's header into a new file, and never bump a year (that's the exact reflex the 2026-07-26 audit found on 278 files):
  - **Ported file** → keep the upstream notice **verbatim**: names, years, capitalisation, even the old `mod-buildcraft.com` URL (still live, serving MMPL 1.0.1). MPL-2.0 §3.4 forbids removing *or altering* the notice on MPL-covered files; MIT requires the notice to travel with the code; MMPL has no explicit notice clause, but its clause 6 keeps modified files and "files containing sections copied from this mod" under MMPL, so the notice is the only way anyone can tell. In all three cases: do not touch it. Recover the original from `8.0.x-1.12.2`, `upstream/7.1.x`, or the BuildCraftAPI submodule — **not** from a neighbouring file.
  - **New file** (including essentially every test) → `Copyright (c) <year written> the BuildCraftUnofficial contributors` plus the standard MPL notice, and nothing else. SpaceToad never appears on it.
  - **Mixed** (new file that absorbed real code from a ported one) → both lines, upstream's first.
  - **No header at all is fine** — upstream itself leaves ~21% of `common/` bare, and inventing a notice for a file its author shipped without one is its own small fabrication. Don't "fix" a headerless ported file. But know the gap this leaves: a headerless *ported* file silently inherits the root MPL-2.0 default, and no test can catch that (the guard validates notices that exist, never notices that should exist). If you touch such a file and know its upstream origin, record it in [NOTICE.md](NOTICE.md).
  - **Three licences ship in this repo, and the header — never the directory — decides which.** MPL-2.0 by default ([LICENSE](LICENSE)); MIT for most of `buildcraft/api` ([LICENSE.API](LICENSE.API)); MMPL-1.0.1 ([LICENSE.MMPL](LICENSE.MMPL)) for the 38 files carried over from 1.7.10/1.12.2 — including seven that sit *inside* `buildcraft/api` next to MIT ones. [NOTICE.md](NOTICE.md) is the map and it ships in the jar. **Do not "harmonise" an MMPL notice to MPL:** upstream never relicensed those files (every upstream branch through `master` still ships MMPL at its root; this fork's MPL root LICENSE arrived in `6352b29bf`, which is not an ancestor of any upstream ref), so there is no grant to rely on. Full reasoning, evidence, and the rejected options: [docs/licensing.md](docs/licensing.md).
  - Enforced by [CopyrightHeaderTester](src/test/java/buildcraft/lib/misc/CopyrightHeaderTester.java), which allowlists the exact known-good claim strings and cross-checks `NOTICE.md` against the tree in both directions. It has to be an allowlist, not a year cutoff: `2020 SpaceToad` looks anachronistic but is genuine upstream text.
- Commit successful transactions to git. Always squash or append commits that are related. Cleanup temporary files, even ones that were already (accidentally) commited.
- Write additions, fixes, and end-user facing changes to `changelog.md`. Combine related items concisely.
- Mojang changed the Minecraft versioning scheme; The version immediately after 1.21.11 is 26.1, using a *year.major.hotfix* nomenclature now. 26.1.2 is the latest version at the time of writing.

### Using `todos.md`

`todos.md` is the live punch list for the port. The porting work itself is essentially done — what remains is polish, bug fixes, and new-feature design. Read it at the start of any session and treat it as load-bearing context, not just a wish list:

- **One line per bullet — no technical detail in the file itself.** Bullets are curt, plain-English one-liners. Everything else — file paths, line numbers, diagnostics, design constraints, open questions — lives in a linked doc: a section of [docs/todo-details.md](docs/todo-details.md) for most items, or a dedicated doc for big programs (e.g. [docs/robotics-resurrection.md](docs/robotics-resurrection.md)). Add the detail section and the link in the same edit that adds the bullet. Never let a bullet grow past one line; if it needs more, that's the signal to move the overflow into the linked doc.
- **Skim it before starting work.** If the task you're about to do covers a bullet on that list (in whole or in part), recognise it without the user having to point at the line number, and remove or trim the bullet as part of the same change. e.g. "we just wired the `goggles` advancement" → strike `goggles` from the advancement orphan list in the same commit.
- **Only track what's planned, not what's done.** Finished items are removed entirely — the bullet AND its linked detail section — never crossed out or kept as historical record. The changelog already serves as the historical record (see [changelog.md](changelog.md)). If a bullet is partially done, trim the bullet and its detail section down to the remaining sub-scope.
- **Add follow-ups you discover.** If implementing one item surfaces a related gap that's clearly out of scope for the current change (a bug spotted in passing, a feature the user hand-waved at, a finalization sweep), add a new one-line bullet under the appropriate section, with any context going into the linked docs per the rule above.
- **Keep it readable.** Sections are: 🔧 Outstanding work → 🆕 New Features → 🚫 Blocked. Bullets are plain `-` list items (the user dropped the checkbox syntax by hand in 2026-08; don't reintroduce `- [ ]`). Don't reorganise without reason; the user edits this file by hand and stable structure matters.
- **Update the "Last audited" date** at the top whenever you make a substantive sweep (not for a single bullet edit).

## NeoForge Version Tracking

NeoForge for Minecraft is constantly updating. `neo_version` is the pin, and it is **per-node** (`versions/<node>/gradle.properties`) — each node tracks its own line independently. The version-check hook below reads every node's pin directly (it no longer depends on the root [gradle.properties](gradle.properties) mirror). Two scripts under `scripts/` keep the project aware of upstream and able to cross-reference the right sources.

### Awareness — the SessionStart hook

`.claude/settings.json` registers a `SessionStart` hook that runs `scripts/neoforge-version-check.sh`: it enumerates every `versions/<node>/gradle.properties` and checks each node independently. For each node it derives that node's NeoForge line from its pinned `neo_version` by stripping the trailing `.<build>` (deriving the line from `minecraft_version` would only line up for the 26.1.2 node — the MC→NeoForge mapping is non-uniform across the CalVer cliff: MC `1.21.1`→NeoForge `21.1.x`, MC `26.2`→NeoForge `26.2.0.x`), fetches NeoForge's `maven-metadata.xml` once, and — only when one or more nodes are behind — injects a notice listing each behind node into the session. Silent when all nodes are current; fails silently when offline. Check manually anytime with `bash scripts/neoforge-version-check.sh --plain`.

### When behind — review, classify, offer a bump

Every build publishes a *cumulative* changelog at `…/neoforge/<version>/neoforge-<version>-changelog.txt`. Fetch the **latest** version's changelog, read the entries above the pinned `neo_version`, cross-reference `todos.md`, then classify the delta for the user:

- **Neutral** — nothing BuildCraft touches; just note the update exists.
- **Beneficial** — a new API/event/hook that unblocks a `todos.md` item or enables an optimization; name the relevant bullet.
- **Cautionary** — a deprecation, removal, signature change, or restructuring in an API BuildCraft *does* use; identify what breaks *before* bumping.

Then offer the bump.

### Bumping

1. Edit `neo_version` (and `minecraft_version`, if it moved) in [gradle.properties](gradle.properties).
2. `./gradlew compileJava` — a Gradle sync so ModDevGradle regenerates artifacts for the new version.
3. `bash scripts/neoforge-sources-sync.sh` — refresh `.neoforge-ref/` (below).
4. Build and test; fix whatever the changelog flagged **cautionary**.
5. No `changelog.md` entry for a bump unless it changes player-facing behavior.

### `.neoforge-ref/` — decompiled API reference

`.neoforge-ref/` (gitignored; populated by `scripts/neoforge-sources-sync.sh`) holds reference sources for **every active Stonecutter node's** pinned versions — **one dir-set per MC line, kept side by side** (so a future 1.21.11 node's sources sit next to 26.1.x's, with no re-checkout on node switch). **It is the first place to look when you need to know how to call a NeoForge or vanilla API** — grep it rather than recalling signatures from memory; the 26.1 API line is new and churns constantly. **Grep the dir-set for the line you're working on — never across versions** (the APIs differ by line, which is the whole point of the split); `.neoforge-ref/INDEX.txt` maps each node to its dirs.

- `.neoforge-ref/sources-<neo_version>/` — decompiled `.java`, one Grep/Read root:
  - `net/minecraft/**` — patched Minecraft (vanilla + NeoForge's patches to it)
  - `net/neoforged/**` — the NeoForge framework: FML, capabilities, registries, events, attachments
  - `com/mojang/**` — Mojang libraries (blaze3d, datafixers, …)
- `.neoforge-ref/vanilla-<minecraft_version>/<minecraft_version>.jar` — the pure, unpatched, deobfuscated vanilla client jar. Bytecode, not source — inspect with `javap` (e.g. `javap -p -cp .neoforge-ref/vanilla-26.1.2/26.1.2.jar net.minecraft.client.Camera`). Authoritative ground truth for unpatched vanilla.

Dir names are version-stamped (`sources-<neo>`, `vanilla-<mc>`). The sync reads each `versions/<node>/gradle.properties`, keeps one dir-set per active node, and prunes only versions no node pins anymore — so adding a node *adds* its dirs without disturbing the others. If the dirs don't match the current nodes — or `.neoforge-ref/` is absent — run `scripts/neoforge-sources-sync.sh` (~1 min per new version; present dirs are reused). A node whose Gradle artifacts aren't built yet is skipped with a note, not an error.

### Hook wiring on a fresh clone

`.claude/` is gitignored, so `.claude/settings.json` — which registers the hook — is **not** version-controlled (the `scripts/` are). Recreate it after a fresh clone:

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

## Testing

Unit tests use JUnit 5 and live under `src/test/`. NeoForge game tests are registered dynamically via `RegisterEvent` on `Registries.TEST_FUNCTION` and cover pipes, transport, fluids, inventory, shapes, markers, and engines.

Unit tests run under moddev's FML-JUnit environment (`neoForge { unitTest }` in [build.gradle.kts](build.gradle.kts)): each node's `test` task boots a real FML loader plus full mod loading before the JUnit engine, so tests can freely use `Entity`, `ItemStack`, registries, and BC's own content. One 26.x-only trap: default item components bind only during server resource load, so **a unit test that constructs ItemStacks must extend `VanillaSetupBaseTester`** (binds them once per JVM; a bare test dies with "Components not bound yet" on 26.1/26.2 while passing on the 1.21.x nodes). `FmlJunitEnvironmentTest` pins the environment on every node and fails loudly if the wiring regresses — details in [docs/robotics-ph3-design.md](docs/robotics-ph3-design.md) amendment 9.

**Shared TEST sources with Stonecutter directives: only ONE branch may be live, and it must be the active-node's.** The active node (`stonecutter active "…"` in [stonecutter.gradle.kts](stonecutter.gradle.kts) — 26.1.2 as of 2026-08) compiles the shared test set **raw**, without directive processing; only main sources are processed on every node. So in each shared-test `//? if` block the non-applicable branch is `/* */`-commented in the tree (the processor inverts that for the other nodes — a live-else on 1.21.1 is generated, not written). Convention: `//? if >=X` with X ≤ active → IF live, else commented; `//? if >=26.2` → else live, IF commented. Violation symptom in `:<active>:compileTestJava`: duplicate-class errors plus a misleading javac NPE (`Cannot invoke "TypeElement.getNestingKind()"`). When the active node changes, every shared-test directive must be re-checked against it.

### Adding a new game test (read this — past agents kept getting it wrong)

Adding a game test in MC 26.1+ takes **three** things, not two. If you do only the first two it silently skips — there is no error, no warning, and `runGameTestServer` keeps reporting "N GAME TESTS COMPLETE" without your test included in N. This footgun had silently disabled 34 registered tests until a manifest sweep caught them — it's easy to miss, so verify the count (below).

1. **Java method** — static, signature `void name(GameTestHelper helper)`, throws on failure, calls `helper.succeed()` (or one of the async `succeedWhen*` variants) on pass. Put it in a `*Tester.java` class under the right subsystem subpackage of `src/test/java/buildcraft/`.

2. **Registration in [BuildCraftGameTests.onRegister](src/test/java/buildcraft/BuildCraftGameTests.java)** — one line like `event.register(Registries.TEST_FUNCTION, Identifier.parse("buildcraftunofficial:your_test_id"), () -> YourTester::yourMethod);`

3. **Test-instance manifest JSON** at `src/test/resources/data/buildcraftunofficial/test_instance/<your_test_id>.json` — the test ID (no namespace) MUST match the file name and the `function` field. For most tests the body is just:

   ```json
   {
       "type": "minecraft:function",
       "function": "buildcraftunofficial:your_test_id",
       "environment": "minecraft:default",
       "structure": "minecraft:empty",
       "max_ticks": 100,
       "setup_ticks": 0,
       "required": true
   }
   ```

   `structure` can point at a saved arena structure if the test needs a pre-built world; `minecraft:empty` gives you a void arena to `helper.setBlock(...)` into. `max_ticks` is the watchdog timeout — async tests with `succeedWhen*` need enough headroom; synchronous tests that throw or succeed immediately can use `20`.

**Never assert on a fixed tick after placing blocks/entities** — even force-loaded chunks take a
variable 1–3+ ticks to become block/entity-ticking (`setChunkForced` adds the ticket; promotion
happens later), and the arena grid lands at a random world position every run, so the same test
passes or flakes run to run. Gate on observed state (`EntityArenaUtil.tickUntil`/`tickUntilThen`,
or poll for your own registration) instead of `runAfterDelay(N)`. Also keep ALL relative positions
inside the test's own arena grid cell — with the `minecraft:empty` structure the framework spaces
arenas 6 apart in X and 7 apart in Z, so x beyond 5 or z beyond 6 writes into the NEXT test's
arena. Full diagnosis: docs/robotics-ph3-design.md, amendment 4.

**To verify your test is actually running** (not silently skipped): note the "N GAME TESTS COMPLETE" count before and after. Each new test should bump N by 1. If it doesn't, the manifest is missing or its `function` field doesn't match the registered ID. Confirm by temporarily making the test throw — if the failure shows up in the "required tests failed" list, it's wired correctly; if it doesn't, fix the manifest first before debugging the test logic.

**Player-state testing limitation**: `GameTestHelper.makeMockPlayer(GameType)` returns an anonymous `Player` (see [GuiTester.java:64-66](src/test/java/buildcraft/lib/test/gui/GuiTester.java)), NOT a `ServerPlayer`. Anything guarded by `instanceof ServerPlayer` (including `AdvancementUtil.unlockAdvancement(Player, …)`) short-circuits silently. Test the predicates and the wiring around player-state calls; the final award/tracker write needs in-client verification.

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- **API lookup:** to find how to call a NeoForge or vanilla API, grep the decompiled sources under `.neoforge-ref/` — see [NeoForge Version Tracking](#neoforge-version-tracking). Don't recall 26.1 API signatures from memory; the line is pre-release and changes constantly.
- Please use `git mv` when moving or renaming files so that the diff understands what happened.