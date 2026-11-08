"BuildCraft Redux" (formerly "BuildCraft Unofficial"; the mod id, asset namespace, copyright collective name and store URLs keep the old name on purpose) is a NeoForge port of the original 1.12.2 and 1.7.10 Minecraft Forge BuildCraft, plus a Fabric node (`26.3-fabric`, #98) that builds all of it into its own jar

The project is a mix of Java 25 and Java 21 out of necessity.

## Build Commands

```bash
# BuildCraft builds with Stonecutter: ONE source tree, one node per MC LINE.
# Six nodes today: 1.21.1, 1.21.10, 1.21.11, 26.1.2, 26.2, 26.3 (26.3 is the primary from its first release; nothing ships until the user says so).
# Plus a Fabric twin of every line (#98, #170): 1.21.1-fabric, 1.21.10-fabric, 1.21.11-fabric, 26.1.2-fabric,
# 26.2-fabric, 26.3-fabric. All of BuildCraft on Fabric Loader + Fabric API + Forge Config API Port,
# jar BuildCraftRedux-<v>+mc<mc>-fabric.jar; :<node>-fabric:runServer / :<node>-fabric:runClient work,
# :<node>-fabric:runGameTest runs the shared game tests on Fabric, and :<node>-fabric:runClientGameTest is the in-world
# client check with screenshots (needs a GL context; not in nodeVerification). 26.x Fabric nodes use Loom without
# remapping; the 1.21.x ones use fabric-loom-remap (Mojang mappings, mod* configurations, remapJar). No JUnit tests on
# them (FML-JUnit has no Fabric twin); their compiles are the deprecated-API guard (-Werror, :<node>-fabric:checkDeprecations).
# `python -I scripts/fabric-mixin-check.py <node>-fabric` checks mixin targets exist by name (not by type) on that
# node's Minecraft; never run it while Gradle is regenerating sources.
# Build logic per loader: buildSrc/src/main/kotlin/bc.<loader>.gradle.kts.
# Tasks are per-node — qualify with :<node>:. Unqualified tasks run across all nodes.
# `./gradlew fullTestSuite` (root) runs unit + game tests + jar for EVERY NeoForge node, and each Fabric node's nodeVerification.
# The ACTIVE node (the raw text of src/, what the IDE sees) is 26.3; see stonecutter.gradle.kts.

# Compile the node (fast feedback)
./gradlew :26.3:compileJava

# Build the jar(s) and collect into build/libs/<mod_version>/ (one jar per node, e.g. +mc26.3)
./gradlew buildAndCollect

# Run the client / server
./gradlew :26.3:runClient
./gradlew :26.3:runServer
./gradlew :26.3:runClient -Precipe_viewer=rei   # dev runtime gets REI instead of JEI (default jei; never both)
./gradlew :26.3-fabric:runClient
./gradlew :26.3-fabric:runServer
# Both Fabric run tasks set buildcraft.dev and honour -PbcDebug[=tier] (and -PbcQuickPlay=<save> on the client), as the NeoForge ones do; -PbcRunDir is NeoForge-only (Loom fixes the working dir).

# Unit tests / headless game tests
./gradlew :26.3:test
./gradlew :26.3:runGameTestServer
./gradlew :26.3-fabric:runGameTest
./gradlew :26.3-fabric:runClientGameTest   # needs a display/GL context
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

`platform/neoforge/BCNeoForgeEntrypoint` is the single `@Mod` entry point and the only class that sees `IEventBus`/`ModContainer` (it hands them to `NeoForgeModBus` for the platform implementations). It runs the loader-neutral `BCCore.init()`, then `BCCoreClient.init()` on a physical client; `BCCore.init()` calls per-subsystem `BC{Name}.init()` (e.g. `BCTransport.init`), which subscribes that subsystem's `BCRegistrar`s. `BC{Name}` classes are registration helpers grouped by topic — *not* sub-mods. Client-only code lives in `BC{Name}Client` classes, instantiated only on `Dist.CLIENT`.

Order: `BCLib` → core registries → per-subsystem registries → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

### Key Subsystems

**Tiles** — every BC block entity sits on the shared hierarchy in `lib.tile`:
- `AbstractBCBlockEntity`: cross-version save/load (**override version-neutral `writeData`/`readData` — never `saveAdditional` directly**), owner persistence (`OwnerData` + `onPlacedBy`), menu-open/`canInteractWith`, `dropContentsOnRemoval` + its `Clearable.clearContent` twin (a command replace wipes instead of spilling; override both over the same contents; `ClearableTwinGuardTest` fails on an override missing its twin).
- `AbstractBCSyncedBlockEntity`: standard `getUpdateTag`/`getUpdatePacket` pair plus `markForRenderUpdate()`/`markForGuiUpdate()` — the no-re-mesh push for BER/GUI-only state; `sendBlockUpdated` only when the *block model* genuinely changes.
- On the synced base: `TileBC_Neptune` (item handler management, player tracking) and the bespoke machines (`TileEngineBase_BC8`, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`, `TileFloodGate`, `TileLaser`). `TileMarker`, `TileSpringOil`, `TilePipeHolder` stay on the plain base — they sync through their own channels; do **not** give them the standard pair.
- Block-side boilerplate (owner forward, menu open, ticker, `<1.21.10 onRemove` catch-all) lives once in `lib.block.BlockBCTile_Neptune` / `BlockBCTile_Directional` (+ `BlockBCTileSupport`); engines have `BlockEngineBase_BC8`. The `>=1.21.10` `preRemoveSideEffects` hook that calls `dropContentsOnRemoval` lives once on `AbstractBCSyncedBlockEntity` (its `<1.21.10` twin is each block base's `onRemove` catch-all) — a tile that holds contents (engines, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`, `TileFloodGate`) overrides only `dropContentsOnRemoval` + `clearContent` (emptying what it spills), and its block drops nothing in `playerWillDestroy` since a player break reaches the same hook. `TileBC_Neptune` is the opt-in flag-guarded variant (`markDropsHandled`); the FE engine and MJ dynamo share their gear slots, FE buffer (direction set by constructor) and burn heat via `TileEngineGearBase`.
- Every `BlockBCTile_Neptune` subclass must declare its own `getRenderShape -> RenderShape.MODEL` — `BaseEntityBlock` defaults INVISIBLE on the 1.21.1 node only, so omission compiles clean and breaks only there; enforced by the `BlockRenderShapeTester` game test.
- Capabilities attach through the platform's `CapabilityRegistrar` (`Platform.transfer().registerCapabilities`, replayed into `RegisterCapabilitiesEvent` on NeoForge) with `BCCapability` tokens; MJ-consumer machines via the `MjCapabilities.registerMjConsumer` bundle.

**Menus/GUI** — `ContainerBC_Neptune` (plain `AbstractContainerMenu`); shared shift-click/phantom-slot/widget-message machinery in `BCContainerSupport` behind the `BCContainer` interface. Tile-backed menus extend `ContainerBCTile<T extends AbstractBCBlockEntity>` (`resolveTile` + `canInteractWith` reach-check). Only AutoWorkbench and Advanced Crafting extend `ContainerBCCrafting` (`RecipeBookMenu`-rooted, hosts the version-forked recipe-book stub once). Menu types come from `Platform.menus().create(ContainerFoo::new)`. Every BC menu provider is an `IBCMenuProvider` (a tile, or `IBCMenuProvider.of(title, ctor, writer)`) opened with `openFor(player)`, which sends exactly `writeOpeningData` (a tile's `BlockPos` by default); its `writeClientSideData` hook writes the same bytes for vanilla's spectator path — never call `player.openMenu` on one directly with a writer (double-write). Buttons: vanilla `Button` for plain text, `BCButton.builder(...)` for icon/toggle buttons (vanilla face + `ButtonSprite` icon from `textures/gui/sprites/button/`, latched/active/tooltip suppliers, mouse-button mask), `BCButton.builder(x, y, ButtonImage)` where the art is the whole button (guide book arrows / sort radio) — don't paint or hit-test buttons in a screen. `ButtonUnificationGuardTester` catches the fingerprints only (screen-played click sound, hover-variant `GuiIcon`, button subclass outside `lib/gui/button`), not every hand-rolled hotspot.

**Pipes/Transport** — each pipe composes a `PipeBehaviour` (type logic) + `PipeFlow` (items/fluids/power); `PipeRegistry` + `PipeDefinition` manage pipe types.

**Wrenches** — real tools: `WrenchTier` (IRON for the Basic and Marker Wrench, DIAMOND for the Volume Wrench) builds each node's durability/enchantability/repair/mining/attack components (vanilla `ToolMaterial` on `>=1.21.10`, `Tier` plus item overrides on 1.21.1); they mine the `buildcraftunofficial:mineable/wrench` block tag. Wear is one use per wrench action, charged once in `ItemWrench_Neptune.wrenchUsed`, so any new wrench interaction must reach `EntityUtil.wrenchUsed`/`activateWrench` (a marker link or volume-box click must not). Zombies carrying or dropping a wrench live in `ZombieWrenches` (a drop event, not a loot table: the loot formats differ on every line).

**Statements/Logic (Gates)** — `IStatement`/`ITrigger`/`IAction` with `StatementManager` as the central registry. Statements do **not** use `DeferredRegister` — `StatementManager` is hand-rolled static and each `BCStatement` self-registers in its constructor (do not add redundant `registerStatement` calls). This is correct: statements aren't registry content.

**Snapshots/Blueprints** — `buildcraft.builders.snapshot`; `ISchematicBlock`/`ISchematicEntity` define save/place, the factory registries map types to schematic factories, `GlobalSavedDataSnapshots` persists server-side. Every inventory and tank key of a tile that some rule prices must be named in the rules under `assets/buildcraftunofficial/compat/buildcraft/builders/buildcraft/`: once any rule prices a block's contents the deny-by-default `clearContent` never runs for it. A tile no rule prices relies on its `clearContent` emptying its contents instead. `TileContentsPricingGuardTester` fills every BC tile and fails naming the unpriced key.

**Networking** — custom payloads register during init through `Platform.network()` (`BCNetwork.clientbound`/`serverbound`/`bidirectional`; NeoForge replays them inside `RegisterPayloadHandlersEvent`) and send with its `sendTo*` methods, never `PacketDistributor`; `StreamCodec<RegistryFriendlyByteBuf, T>` serialization (`registryBuf` wraps a bare buffer); handlers take a `PacketContext` and call `ctx.runOnMain()`.

**Config** — two specs: `BCUnifiedConfig` (COMMON) + `BCUnifiedClientConfig` (CLIENT), each wrapping per-subsystem configs via `.push()/.pop()`. The COMMON/CLIENT split is load-bearing (client-only render/display options must live on the CLIENT spec).

**Recipes** — `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

### Platform layer

Loader-facing seams live in `buildcraft.platform` (`Platform.loader()` today: side, mod list, game/config dirs, current server); common code goes through it, not straight to FML. Contract rule: the files in `buildcraft/platform/` outside the per-loader packages name only the JDK, vanilla, other `buildcraft.platform` types, `buildcraft.api.fluids` (`BCFluidStack`) and `buildcraft.api.capability` (`BCCapability`) — never a loader type or any other BC class. Loader code lives in `buildcraft/platform/<loader>/`, each file's body inside `//? if <loader> {` (Stonecutter constants `neoforge`/`fabric`, set from the `loader` property), and only the binder names an implementation. Standing design, slice plan and finish line: [docs/platform-layer.md](docs/platform-layer.md) (#98).

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
- **Three licences ship here; the header — never the directory — decides which**: MPL-2.0 default ([LICENSE](LICENSE)); MIT for most of `buildcraft/api` ([LICENSE.API](LICENSE.API)); MMPL-1.0.1 ([LICENSE.MMPL](LICENSE.MMPL)) for the files carried from 1.7.10/1.12.2 (about a hundred, seven of them inside `buildcraft/api`). [NOTICE.md](NOTICE.md) is the map, lists them all, and ships in the jar. **Never "harmonise" an MMPL notice to MPL** — no relicence ever happened upstream, so no grant exists. Full reasoning: [docs/licensing.md](docs/licensing.md).
- Enforced by [CopyrightHeaderTester](src/test/java/buildcraft/lib/misc/CopyrightHeaderTester.java) — an allowlist of exact known-good claims, cross-checked against NOTICE.md both ways; a new legitimate claim means editing `ALLOWED_CLAIMS` on purpose.

## Work tracking: GitHub Issues

Planned work lives in [GitHub Issues](https://github.com/legoj15/BuildCraft/issues), never in a file in this tree. The `issues` skill (`bash ~/.claude/skills/issues/issues.sh`, run from the repo or any worktree) is the only way to touch it. `todos.md` is a pointer kept for old links; `docs/todo-details.md` is gone, its sections are the issue bodies.

- **Session start:** `issues.sh ready` lists what may be picked up; `issues.sh show N` gives the body and comments that used to live in todo-details. Read the issue before briefing an implementer.
- **Recognise covered work.** If the change you are making covers an open issue, in whole or in part, say so without being pointed at it. Finish with `Closes #N` in the commit body (GitHub closes it when the commit reaches `main` on the remote) or `issues.sh done N` when nothing will be pushed soon. A partially covered issue is closed and the remainder re-filed with `add`; never left open as "mostly done".
- **Claim before implementing.** The orchestrator runs `issues.sh claim N`; implementer briefs name the number and never claim or close.
- **Follow-ups you discover** become `issues.sh add "one plain-English line" --type bug|enhancement|question|chore --body "file pointers, constraints, prior attempts"`. Decisions the user must make are `question`. Detail goes in the body or a `note`, not into a doc, unless it belongs to a standing design document (e.g. [docs/robotics-resurrection.md](docs/robotics-resurrection.md)); then the issue links the doc by path.
- **Blocked** items get `issues.sh block N "why, and what unblocks it"`; the body ends with a reopen trigger.
- **Nothing to reconcile at merge time.** A worktree merging to `main` does not touch any tracking file; `issues.sh stale` occasionally catches claims whose branch is merged or gone.
- Milestone `2026.3` groups the next release's features and is the human-facing roadmap.

## NeoForge Version Tracking

`neo_version` is pinned **per-node** (`versions/<node>/gradle.properties`); each node tracks its line independently. A SessionStart hook (`scripts/neoforge-version-check.sh`, registered in `.claude/settings.json` for Claude Code and in the tracked `.zcode/config.json` for ZCode) checks every node against upstream and injects a notice when any is behind — silent when current, fails silently when offline (manual: `bash scripts/neoforge-version-check.sh --plain`). **When it fires — or the user asks for a bump — invoke the `neoforge-bump` skill** (changelog classification, pin edit, sources re-sync, post-bump testing, fresh-clone hook wiring).

`.neoforge-ref/` (gitignored; populated by `scripts/neoforge-sources-sync.sh`) holds decompiled reference sources for **every active node's** pinned version — one dir-set per MC line, side by side. **It is the first place to look when you need to know how to call a NeoForge or vanilla API** — grep the dir-set for the line you're working on, never across versions; `INDEX.txt` maps nodes to dirs. If the dirs don't match the current nodes or are absent, run the sync (~1 min per new version; present dirs reused).

Agent-config directories are gitignored except what must be shared: `.claude/` except `.claude/skills/`, and `.zcode/` except `.zcode/config.json` (which carries the version-check hook above). `.claude/settings.json` is machine-local — recreate it on a fresh clone (hook JSON in the `neoforge-bump` skill). ZCode does not read `.claude/skills/`, so its copies are a machine-local mirror made by `bash scripts/zcode-skills-sync.sh` — after a fresh clone (or after editing a skill), run it once. `CLAUDE.md` is a one-line `@AGENTS.md` import stub so Claude Code, which does not read AGENTS.md, still loads these rules — edit AGENTS.md, never the stub.

## Testing

Unit tests use JUnit 5 and live under `src/test/`. Game tests are registered by `BuildCraftGameTests.register` through BuildCraft's registrar on `Registries.TEST_FUNCTION` (a loader hook calls it: `NeoForgeGameTestHooks` from `FMLConstructModEvent`, the Fabric game-test mod's initializer on `26.3-fabric`) and cover pipes, transport, fluids, inventory, shapes, markers, and engines. The Fabric node runs the same bodies (`:26.3-fabric:runGameTest`), so a game-test body reaches the loader only through `Platform` seams and `lib.test.LoaderTestEvents`; the skill has the rules.

Unit tests run under moddev's FML-JUnit environment (`neoForge { unitTest }` in [build.gradle.kts](build.gradle.kts)): each node's `test` task boots a real FML loader plus full mod loading before the JUnit engine, so tests can freely use `Entity`, `ItemStack`, registries, and BC's own content. `FmlJunitEnvironmentTest` pins this wiring on every node.

- **A unit test constructing ItemStacks must extend `VanillaSetupBaseTester`** — default item components bind only during server resource load; a bare test dies with "Components not bound yet" on the 26.x nodes while passing on 1.21.x.
- **Shared TEST sources with Stonecutter directives: only ONE branch may be live, and it must be the active-node's.** The active node (`stonecutter active "…"` in [stonecutter.gradle.kts](stonecutter.gradle.kts) — 26.3 as of 2026-10) compiles the shared test set **raw**; in each shared-test `//? if` block the non-applicable branch is `/* */`-commented in-tree (`//? if >=X` with X ≤ active → IF live, else commented; `//? if <X` with X ≤ active → IF commented, else live; a future `//? if >=26.4` → else live, IF commented). Violation symptom in `:<active>:compileTestJava`: duplicate-class errors plus a misleading javac NPE (`Cannot invoke "TypeElement.getNestingKind()"`). Re-check every shared-test directive when the active node changes.
- **A test that inspects code (not runs it) reads this node's view through `buildcraft.lib.test.MainSourceSet`** — never `src/` directly, which is the active node's text on every node. The one exception is [StonecutterDirectiveGuardTester](src/test/java/buildcraft/StonecutterDirectiveGuardTester.java), which inspects the directive markup only the raw tree carries (it fails on a live arm made only of comments, which Stonecutter strips into bare text on the nodes that match it). **[ForRemovalApiGuardTester](src/test/java/buildcraft/ForRemovalApiGuardTester.java) fails on any use of an `@Deprecated(forRemoval = true)` API** (constant-pool refs, overrides, imports; javac's `[removal]` warnings otherwise scroll past): migrate, or add the declaration to its `PENDING` map with a reason. Plain `@Deprecated`: fix it, or suppress on the narrowest element (a local variable or method; the class only when the use sits in its header) with a one-line why. On `26.3-fabric` the compile itself is the guard (`-Xlint:deprecation,removal -Werror` on main and gametest, `checkDeprecations`): the same rules, enforced for Fabric API and vanilla alike.
- **Adding a game test → invoke the `add-game-test` skill first.** Game tests take **three** parts (method + registration + manifest JSON) and silently skip with only two — [GameTestManifestTester](src/test/java/buildcraft/GameTestManifestTester.java) guards that wiring in the unit-test run. The skill carries the arena rules (never assert on a fixed tick after placing blocks — gate on observed state; keep positions inside the arena cell) and the `makeMockPlayer`-is-not-a-`ServerPlayer` limitation.

## Project skills

Procedure skills ship tracked in `.claude/skills/`: **add-game-test**, **neoforge-bump** and **in-client-smoke** (scripted per-node dev-client runs with screenshots, `scripts/smoke/`). Invoke them when doing those tasks; the details live there, not here. ZCode sessions load them from the machine-local `.zcode/skills/` mirror (see the sync script above).

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- Please use `git mv` when moving or renaming files so the diff understands what happened.
