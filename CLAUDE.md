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

### Module Layout

All code lives under `src/main/java/buildcraft/` in a single source set. Modules are separated by package:

| Package | Contents |
|---|---|
| `api` | Public-facing interfaces and registries (no mod dependency required) |
| `lib` | Shared utilities: tile base classes, GUI framework, networking, config |
| `core` | Main `@Mod` entry point, markers, springs, world gen |
| `transport` | Pipes, pipe flows, pipe behaviors, plugs |
| `energy` | Engines (Redstone/Stirling/Combustion), fuel/coolant registries |
| `factory` | Refineries, mining wells, auto-workbench |
| `builders` | Quarries, architect table, builder, filler, snapshot/blueprint system |
| `silicon` | Logic gates, chipsets, circuit boards |
| `robotics` | Robots and zone planning |

### Initialization Pattern

`BCCore` is the single `@Mod` entry point. It constructs each module class (`BCTransport`, `BCEnergy`, `BCBuilders`, etc.) which registers their `DeferredRegister<T>` instances onto the mod event bus. Client-only code lives in `BC{Module}Client` classes, instantiated only on `Dist.CLIENT`.

Initialization order: `BCLib` → registries (blocks/items/BEs) → `FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

### Key Subsystems

**Tiles**: All block entities extend `TileBC_Neptune`, which provides item handler management, player tracking, and owner persistence. Capability attachment uses NeoForge's `RegisterCapabilitiesEvent`.

**Pipes/Transport**: Each pipe is a composition of a `PipeBehaviour` (determines pipe type logic) and a `PipeFlow` (handles what flows through it — items, fluids, power). `PipeRegistry` and `PipeDefinition` manage pipe types.

**Statements/Logic** (Gates): `IStatement`, `ITrigger`, and `IAction` interfaces with `StatementManager` as the central registry. Used by silicon gates to wire conditions to actions.

**Snapshots/Blueprints**: The builders module uses a snapshot system under `buildcraft.builders.snapshot`. Schematics (`ISchematicBlock`, `ISchematicEntity`) define how blocks/entities are saved and placed. `SchematicBlockFactoryRegistry` and `SchematicEntityFactoryRegistry` map block/entity types to their schematic factories. `GlobalSavedDataSnapshots` persists snapshot data server-side.

**Networking**: Custom payloads using NeoForge's `RegisterPayloadHandlersEvent`. Payloads use `StreamCodec<RegistryFriendlyByteBuf, T>` for serialization. Handlers call `ctx.enqueueWork()` to execute on the logical thread.

**Config**: Single `BCUnifiedConfig` wraps all module configs into one `ModConfigSpec` using `.push()/.pop()` sections per module.

**Recipes**: `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

### Conventions

- Deferred registries: use `DeferredRegister<T>` and `DeferredHolder<R,T>` — never direct registry access.
- Blocks/items/BEs for each module live in `BC{Module}Blocks`, `BC{Module}Items`, `BC{Module}BlockEntities`.
- Event listeners use `@SubscribeEvent` on `@EventBusSubscriber`-annotated classes, or `modEventBus.addListener()` in constructors.
- Files with a `.disabled` extension were created from the initial forking of 1.12.2 to the first modern port on version 1.21.11. They exist as references to how the 1.12.2 version functioned, but can be safely deleted once their functionality has been either 1:1 ported or recreated in a new, updated, and/or improved way.
- Commit successful transactions to git. Always squash or append commits that are related. Cleanup temporary files, even ones that were already (accidentally) commited.
- Write additions, fixes, and end-user facing changes to `changelog.md`. Combine related items concisely.
- Mojang changed the Minecraft versioning scheme; The version immediately after 1.21.11 is 26.1, using a *year.major.hotfix* nomenclature now. 26.1.2 is the latest version at the time of writing.

## Testing

Unit tests use JUnit 5 and live under `src/test/`. NeoForge game tests are registered dynamically via `RegisterEvent` on `Registries.TEST_FUNCTION` and cover pipes, transport, fluids, inventory, shapes, markers, and engines.

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- Please use `git mv` when moving or renaming files so that the diff understands what happened.