# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BuildCraft is a NeoForge mod for Minecraft 26.1.x targeting **NeoForge 26.1.x** with **Java 25**. It adds automation machinery: pipes/transport, engines/energy, builders/blueprints, quarries, factories, silicon logic gates, and robots.

Java 25 is the project toolchain — used for compile, test, and the actual game runtime (NeoForge 26.1.2's `fancymodloader`, `neoform`, and friends are themselves built for Java 25 and refuse to resolve against a Java 21 consumer). The moddev plugin additionally runs its NFRT tooling tasks (`downloadAssets`, `prepareClientRun`, …) on Java 21, but that's invisible to contributors: the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) applied in [settings.gradle](settings.gradle) auto-provisions a JDK 21 into Gradle's user home (`~/.gradle/jdks/`) on first build. The only JDK a contributor needs to install manually is **Java 25**.

## Build Commands

```bash
# Compile only (fast feedback)
./gradlew compileJava

# Full build (produces .jar in build/libs/)
./gradlew build

# Run Minecraft client with mod loaded
./gradlew runClient

# Run dedicated server
./gradlew runServer

# Run unit tests
./gradlew test

# Run NeoForge game tests (headless quick in-game integration tests)
./gradlew runGameTestServer
```

## Architecture

### History — this is one mod, not eight

Pre-1.13 BuildCraft was 8 separate mods (`buildcraft-core`, `buildcraft-transport`, `buildcraft-energy`, `buildcraft-builders`, `buildcraft-silicon`, `buildcraft-factory`, `buildcraft-robotics`, `buildcraft-lib`) fatjarred together at release time. Since the 1.21.11 port it is a **single mod with one mod ID** (`buildcraftunofficial`). The Java package layout still mirrors the old submod boundaries because renaming packages would be churn without benefit — but the packages are **just packages**. There is no `ModList.isLoaded` cross-mod check, no inter-mod IPC, no class-loading ordering problem to solve, no fatjar discovery, no separate API artifact. Treat package boundaries as code organisation only. If you find yourself reaching for reflection or `ModList`-style guards to call between packages, you are inventing a problem that does not exist — just call directly.

### Package Layout

All code lives under `src/main/java/buildcraft/` in a single source set. Packages are organised by subsystem (this layout matches the historical 1.12.2 submod boundaries):

| Package | Contents |
|---|---|
| `api` | Public-facing interfaces and registries other mods integrate against |
| `lib` | Shared utilities: tile base classes, GUI framework, networking, config |
| `core` | Main `@Mod` entry point, markers, springs, world gen |
| `transport` | Pipes, pipe flows, pipe behaviors, plugs |
| `energy` | Engines (Redstone/Stirling/Combustion), fuel/coolant registries |
| `factory` | Refineries, mining wells, auto-workbench |
| `builders` | Quarries, architect table, builder, filler, snapshot/blueprint system |
| `silicon` | Logic gates, chipsets, circuit boards |
| `robotics` | Robots and zone planning |

### Initialization Pattern

`BCCore` is the single `@Mod` entry point. It calls a per-subsystem `BC{Name}.init(modEventBus)` (e.g. `BCTransport.init`, `BCEnergy.init`) which registers that subsystem's `DeferredRegister<T>` instances onto the mod event bus. These `BC{Name}` classes are registration helpers grouped by topic — they are *not* sub-mods. Client-only code lives in `BC{Name}Client` classes, instantiated only on `Dist.CLIENT`.

Initialization order: `BCLib` → core registries → per-subsystem registries → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

### Key Subsystems

**Tiles**: All block entities extend `TileBC_Neptune`, which provides item handler management, player tracking, and owner persistence. Capability attachment uses NeoForge's `RegisterCapabilitiesEvent`.

**Pipes/Transport**: Each pipe is a composition of a `PipeBehaviour` (determines pipe type logic) and a `PipeFlow` (handles what flows through it — items, fluids, power). `PipeRegistry` and `PipeDefinition` manage pipe types.

**Statements/Logic** (Gates): `IStatement`, `ITrigger`, and `IAction` interfaces with `StatementManager` as the central registry. Used by silicon gates to wire conditions to actions.

**Snapshots/Blueprints**: The builders module uses a snapshot system under `buildcraft.builders.snapshot`. Schematics (`ISchematicBlock`, `ISchematicEntity`) define how blocks/entities are saved and placed. `SchematicBlockFactoryRegistry` and `SchematicEntityFactoryRegistry` map block/entity types to their schematic factories. `GlobalSavedDataSnapshots` persists snapshot data server-side.

**Networking**: Custom payloads using NeoForge's `RegisterPayloadHandlersEvent`. Payloads use `StreamCodec<RegistryFriendlyByteBuf, T>` for serialization. Handlers call `ctx.enqueueWork()` to execute on the logical thread.

**Config**: Single `BCUnifiedConfig` wraps all per-subsystem configs into one `ModConfigSpec` using `.push()/.pop()` sections.

**Recipes**: `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

### Conventions

- Deferred registries: use `DeferredRegister<T>` and `DeferredHolder<R,T>` — never direct registry access.
- Blocks/items/BEs for each subsystem live in `BC{Name}Blocks`, `BC{Name}Items`, `BC{Name}BlockEntities`.
- Event listeners use `@SubscribeEvent` on `@EventBusSubscriber`-annotated classes, or `modEventBus.addListener()` in constructors.
- Files with a `.disabled` extension were created from the initial forking of 1.12.2 to the first modern port on version 1.21.11. They exist as references to how the 1.12.2 version functioned, but can be safely deleted once their functionality has been either 1:1 ported or recreated in a new, updated, and/or improved way.
- Commit successful transactions to git. Always squash or append commits that are related. Cleanup temporary files, even ones that were already (accidentally) commited.
- Write additions, fixes, and end-user facing changes to `changelog.md`. Combine related items concisely.
- Mojang changed the Minecraft versioning scheme; The version immediately after 1.21.11 is 26.1, using a *year.major.hotfix* nomenclature now. 26.1.2 is the latest version at the time of writing.

### Using `todos.md`

`todos.md` is the live punch list for the port. The porting work itself is essentially done — what remains is polish, bug fixes, and new-feature design. Read it at the start of any session and treat it as load-bearing context, not just a wish list:

- **Skim it before starting work.** If the task you're about to do covers a bullet on that list (in whole or in part), recognise it without the user having to point at the line number, and remove or trim the bullet as part of the same change. e.g. "we just wired the `goggles` advancement" → strike `goggles` from the advancement orphan list in the same commit.
- **Only track what's planned, not what's done.** Finished items are removed entirely, not crossed out or kept as historical record. The changelog already serves as the historical record (see [changelog.md](changelog.md)). If a bullet is partially done, edit it down to the remaining sub-scope rather than leaving stale "✅ done" subtree noise behind.
- **Add follow-ups you discover.** If implementing one item surfaces a related gap that's clearly out of scope for the current change (a bug spotted in passing, a feature the user hand-waved at, a finalization sweep), add a new bullet under the appropriate section. Keep new entries terse — one line is usually enough; expand only when the *why* or a non-obvious constraint isn't recoverable from the bullet text itself.
- **Keep it readable.** Sections are: Subsystem Status table → 🔧 Outstanding work → 🆕 New Features → 🚫 Blocked → 🧹 Finalization. Bullets are checkboxes (`- [ ]`). Don't reorganise without reason; the user edits this file by hand and stable structure matters.
- **Update the "Last audited" date** at the top whenever you make a substantive sweep (not for a single bullet edit).

## Testing

Unit tests use JUnit 5 and live under `src/test/`. NeoForge game tests are registered dynamically via `RegisterEvent` on `Registries.TEST_FUNCTION` and cover pipes, transport, fluids, inventory, shapes, markers, and engines.

### Adding a new game test (read this — past agents kept getting it wrong)

Adding a game test in MC 26.1+ takes **three** things, not two. If you do only the first two it silently skips — there is no error, no warning, and `runGameTestServer` keeps reporting "N GAME TESTS COMPLETE" without your test included in N. This is the same footgun called out in the `blueprint_replace` comment in [BuildCraftGameTests](src/test/java/buildcraft/BuildCraftGameTests.java) ("37→34 even before these additions") — most of those skipped tests are missing the third piece.

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

**To verify your test is actually running** (not silently skipped): note the "N GAME TESTS COMPLETE" count before and after. Each new test should bump N by 1. If it doesn't, the manifest is missing or its `function` field doesn't match the registered ID. Confirm by temporarily making the test throw — if the failure shows up in the "required tests failed" list, it's wired correctly; if it doesn't, fix the manifest first before debugging the test logic.

**Player-state testing limitation**: `GameTestHelper.makeMockPlayer(GameType)` returns an anonymous `Player` (see [GuiTester.java:64-66](src/test/java/buildcraft/lib/test/gui/GuiTester.java)), NOT a `ServerPlayer`. Anything guarded by `instanceof ServerPlayer` (including `AdvancementUtil.unlockAdvancement(Player, …)`) short-circuits silently. Test the predicates and the wiring around player-state calls; the final award/tracker write needs in-client verification.

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- Please use `git mv` when moving or renaming files so that the diff understands what happened.