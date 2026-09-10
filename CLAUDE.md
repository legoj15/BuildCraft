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

Avoid using deprecated functions or API systems that throw warnings. This may result in *technically* unnecessary node separation, but ideally keeps the code compatible with newer NeoForge versions/Minecraft releases. Long-form prose and reasoning: [docs/architecture.md](docs/architecture.md).

### History — this is one mod, not eight

The 1.12.2 (8.0.x) and 1.7.10 (7.1.x) versions we're based on used a coremod and submod architecture — no longer clean in modern NeoForge (all 9 mods would show up separately) — so every submod (`energy`, `silicon`, `transport`, `api`, `lib`, `factory`, `builders`, `robotics`) collapsed into an organized compartment of one monolithic "core" mod.

### Initialization Pattern

`BCCore` is the single `@Mod` entry point. It calls per-subsystem `BC{Name}.init(modEventBus)` (e.g. `BCTransport.init`) which registers that subsystem's `DeferredRegister<T>` instances onto the mod event bus. `BC{Name}` classes are registration helpers grouped by topic — *not* sub-mods. Client-only code lives in `BC{Name}Client` classes, instantiated only on `Dist.CLIENT`.

Order: `BCLib` → core registries → per-subsystem registries → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

### Key Subsystems

**Tiles** — every BC block entity sits on the shared hierarchy in `lib.tile`:
- `AbstractBCBlockEntity`: cross-version save/load (**override version-neutral `writeData`/`readData` — never `saveAdditional` directly**), owner persistence (`OwnerData` + `onPlacedBy`), menu-open/`canInteractWith`, `dropContentsOnRemoval`.
- `AbstractBCSyncedBlockEntity`: standard `getUpdateTag`/`getUpdatePacket` pair plus `markForRenderUpdate()`/`markForGuiUpdate()` — the no-re-mesh push for BER/GUI-only state; `sendBlockUpdated` only when the *block model* genuinely changes.
- On the synced base: `TileBC_Neptune` (item handler management, player tracking) and the bespoke machines (`TileEngineBase_BC8`, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`, `TileFloodGate`, `TileLaser`). `TileMarker`, `TileSpringOil`, `TilePipeHolder` stay on the plain base — they sync through their own channels; do **not** give them the standard pair.
- Block-side boilerplate (owner forward, menu open, ticker, `<1.21.10 onRemove` catch-all) lives once in `lib.block.BlockBCTile_Neptune` / `BlockBCTile_Directional` (+ `BlockBCTileSupport`); engines have `BlockEngineBase_BC8`.
- Every `BlockBCTile_Neptune` subclass must declare its own `getRenderShape -> RenderShape.MODEL` — `BaseEntityBlock` defaults INVISIBLE on the 1.21.1 node only, so omission compiles clean and breaks only there; enforced by the `BlockRenderShapeTester` game test.
- Capabilities attach via `RegisterCapabilitiesEvent`; MJ-consumer machines via the `MjCapabilities.registerMjConsumer` bundle.

**Menus/GUI** — `ContainerBC_Neptune` (plain `AbstractContainerMenu`); shared shift-click/phantom-slot/widget-message machinery in `BCContainerSupport` behind the `BCContainer` interface. Tile-backed menus extend `ContainerBCTile<T extends AbstractBCBlockEntity>` (`resolveTile` + `canInteractWith` reach-check). Only AutoWorkbench and Advanced Crafting extend `ContainerBCCrafting` (`RecipeBookMenu`-rooted, hosts the version-forked recipe-book stub once). Menu-opening tiles implement `IBCMenuProvider` — `writeClientSideData` writes the `BlockPos` on every open path (spectator-safe); never route the pos through the two-arg `openMenu(provider, pos)` overload (double-write).

**Pipes/Transport** — each pipe composes a `PipeBehaviour` (type logic) + `PipeFlow` (items/fluids/power); `PipeRegistry` + `PipeDefinition` manage pipe types.

**Statements/Logic (Gates)** — `IStatement`/`ITrigger`/`IAction` with `StatementManager` as the central registry. Statements do **not** use `DeferredRegister` — `StatementManager` is hand-rolled static and each `BCStatement` self-registers in its constructor (do not add redundant `registerStatement` calls). This is correct: statements aren't registry content.

**Snapshots/Blueprints** — `buildcraft.builders.snapshot`; `ISchematicBlock`/`ISchematicEntity` define save/place, the factory registries map types to schematic factories, `GlobalSavedDataSnapshots` persists server-side.

**Networking** — custom payloads via `RegisterPayloadHandlersEvent`; `StreamCodec<RegistryFriendlyByteBuf, T>` serialization; handlers call `ctx.enqueueWork()`.

**Config** — two specs: `BCUnifiedConfig` (COMMON) + `BCUnifiedClientConfig` (CLIENT), each wrapping per-subsystem configs via `.push()/.pop()`. The COMMON/CLIENT split is load-bearing (client-only render/display options must live on the CLIENT spec).

**Recipes** — `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

### Conventions

- Deferred registries: use `DeferredRegister<T>` and `DeferredHolder<R,T>` — never direct registry access.
- Blocks/items/BEs for each subsystem live in `BC{Name}Blocks`, `BC{Name}Items`, `BC{Name}BlockEntities`.
- Event listeners use `@SubscribeEvent` on `@EventBusSubscriber`-annotated classes, or `modEventBus.addListener()` in constructors.
- Files with a `.disabled` extension were created from the initial forking of 1.12.2 to the first modern port. They are references to how 1.12.2 functioned and can be safely deleted once their functionality has been ported or recreated.
- Commit successful transactions to git. Always squash or append commits that are related. Cleanup temporary files, even ones that were already (accidentally) commited.
- Write additions, fixes, and end-user facing changes to `changelog.md`. Combine related items concisely.
- Mojang changed the Minecraft versioning scheme; The version immediately after 1.21.11 is 26.1, using a *year.major.hotfix* nomenclature now. 26.1.2 is the latest version at the time of writing.

**Copyright headers track the FILE's text, not the feature's idea** (copyright protects expression, never concepts):
- **Ported file** → keep the upstream notice **verbatim** — names, years, even the old `mod-buildcraft.com` URL; never bump a year. Recover originals from `8.0.x-1.12.2`, `upstream/7.1.x`, or the BuildCraftAPI submodule — never from a neighbouring file. MPL-2.0 §3.4, MIT, and MMPL clause 6 all forbid altering it in their own ways.
- **New file** (including essentially every test) → `Copyright (c) <year written> the BuildCraftUnofficial contributors` plus the standard MPL notice, and nothing else. SpaceToad never appears on it.
- **Mixed** (new file that absorbed real code from a ported one) → both lines, upstream's first.
- **No header at all is fine** — upstream leaves ~21% of `common/` bare; don't invent or "fix" one. If you touch a headerless ported file and know its origin, record it in [NOTICE.md](NOTICE.md).
- **Three licences ship here; the header — never the directory — decides which**: MPL-2.0 default ([LICENSE](LICENSE)); MIT for most of `buildcraft/api` ([LICENSE.API](LICENSE.API)); MMPL-1.0.1 ([LICENSE.MMPL](LICENSE.MMPL)) for the 38 files carried from 1.7.10/1.12.2 (incl. seven inside `buildcraft/api`). [NOTICE.md](NOTICE.md) is the map and ships in the jar. **Never "harmonise" an MMPL notice to MPL** — no relicence ever happened upstream, so no grant exists. Full reasoning: [docs/licensing.md](docs/licensing.md).
- Enforced by [CopyrightHeaderTester](src/test/java/buildcraft/lib/misc/CopyrightHeaderTester.java) — an allowlist of exact known-good claims, cross-checked against NOTICE.md both ways; a new legitimate claim means editing `ALLOWED_CLAIMS` on purpose.

## Using `todos.md`

`todos.md` is the live punch list for the port. The porting work itself is essentially done — what remains is polish, bug fixes, and new-feature design. Read it at the start of any session and treat it as load-bearing context, not just a wish list:

- **One line per bullet — no technical detail in the file itself.** Bullets are curt, plain-English one-liners. Everything else — file paths, line numbers, diagnostics, design constraints, open questions — lives in a linked doc: a section of [docs/todo-details.md](docs/todo-details.md) for most items, or a dedicated doc for big programs (e.g. [docs/robotics-resurrection.md](docs/robotics-resurrection.md)). Add the detail section and the link in the same edit that adds the bullet. Never let a bullet grow past one line; if it needs more, that's the signal to move the overflow into the linked doc.
- **Skim it before starting work.** If the task you're about to do covers a bullet on that list (in whole or in part), recognise it without the user having to point at the line number, and remove or trim the bullet as part of the same change. e.g. "we just wired the `goggles` advancement" → strike `goggles` from the advancement orphan list in the same commit.
- **Only track what's planned, not what's done.** Finished items are removed entirely — the bullet AND its linked detail section — never crossed out or kept as historical record. The changelog already serves as the historical record (see [changelog.md](changelog.md)). If a bullet is partially done, trim the bullet and its detail section down to the remaining sub-scope.
- **Add follow-ups you discover.** If implementing one item surfaces a related gap that's clearly out of scope for the current change (a bug spotted in passing, a feature the user hand-waved at, a finalization sweep), add a new one-line bullet under the appropriate section, with any context going into the linked docs per the rule above.
- **Keep it readable.** Sections are: 🔧 Outstanding work → 🆕 New Features → 🚫 Blocked. Bullets are plain `-` list items (the user dropped the checkbox syntax by hand in 2026-08; don't reintroduce `- [ ]`). Don't reorganise without reason; the user edits this file by hand and stable structure matters.
- **Update the "Last audited" date** at the top whenever you make a substantive sweep (not for a single bullet edit).

## NeoForge Version Tracking

`neo_version` is pinned **per-node** (`versions/<node>/gradle.properties`); each node tracks its line independently. A SessionStart hook (`scripts/neoforge-version-check.sh`, registered in `.claude/settings.json`) checks every node against upstream and injects a notice when any is behind — silent when current, fails silently when offline (manual: `bash scripts/neoforge-version-check.sh --plain`). **When it fires — or the user asks for a bump — invoke the `neoforge-bump` skill** (changelog classification, pin edit, sources re-sync, post-bump testing, fresh-clone hook wiring).

`.neoforge-ref/` (gitignored; populated by `scripts/neoforge-sources-sync.sh`) holds decompiled reference sources for **every active node's** pinned version — one dir-set per MC line, side by side. **It is the first place to look when you need to know how to call a NeoForge or vanilla API** — grep the dir-set for the line you're working on, never across versions; `INDEX.txt` maps nodes to dirs. If the dirs don't match the current nodes or are absent, run the sync (~1 min per new version; present dirs reused).

`.claude/` is gitignored **except `.claude/skills/`** (tracked). `settings.json` is machine-local — recreate it on a fresh clone (hook JSON in the `neoforge-bump` skill).

## Testing

Unit tests use JUnit 5 and live under `src/test/`. NeoForge game tests are registered dynamically via `RegisterEvent` on `Registries.TEST_FUNCTION` and cover pipes, transport, fluids, inventory, shapes, markers, and engines.

Unit tests run under moddev's FML-JUnit environment (`neoForge { unitTest }` in [build.gradle.kts](build.gradle.kts)): each node's `test` task boots a real FML loader plus full mod loading before the JUnit engine, so tests can freely use `Entity`, `ItemStack`, registries, and BC's own content. `FmlJunitEnvironmentTest` pins this wiring on every node.

- **A unit test constructing ItemStacks must extend `VanillaSetupBaseTester`** — default item components bind only during server resource load; a bare test dies with "Components not bound yet" on the 26.x nodes while passing on 1.21.x.
- **Shared TEST sources with Stonecutter directives: only ONE branch may be live, and it must be the active-node's.** The active node (`stonecutter active "…"` in [stonecutter.gradle.kts](stonecutter.gradle.kts) — 26.1.2 as of 2026-08) compiles the shared test set **raw**; in each shared-test `//? if` block the non-applicable branch is `/* */`-commented in-tree (`//? if >=X` with X ≤ active → IF live, else commented; `//? if >=26.2` → else live, IF commented). Violation symptom in `:<active>:compileTestJava`: duplicate-class errors plus a misleading javac NPE (`Cannot invoke "TypeElement.getNestingKind()"`). Re-check every shared-test directive when the active node changes.
- **Adding a game test → invoke the `add-game-test` skill first.** Game tests take **three** parts (method + registration + manifest JSON) and silently skip with only two — [GameTestManifestTester](src/test/java/buildcraft/GameTestManifestTester.java) guards that wiring in the unit-test run. The skill carries the arena rules (never assert on a fixed tick after placing blocks — gate on observed state; keep positions inside the arena cell) and the `makeMockPlayer`-is-not-a-`ServerPlayer` limitation.

## Project skills

Procedure skills ship tracked in `.claude/skills/`: **add-game-test** and **neoforge-bump**. Invoke them when doing those tasks; the details live there, not here.

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- Please use `git mv` when moving or renaming files so the diff understands what happened.
