# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BuildCraft is a NeoForge mod for Minecraft 26.1.x targeting **NeoForge 26.1.x** with **Java 25**. It adds automation machinery: pipes/transport, engines/energy, builders/blueprints, quarries, factories, silicon logic gates, and robots.

Java 25 is the project toolchain — used for compile, test, and the actual game runtime (NeoForge 26.1.2's `fancymodloader`, `neoform`, and friends are themselves built for Java 25 and refuse to resolve against a Java 21 consumer). The moddev plugin additionally runs its NFRT tooling tasks (`downloadAssets`, `prepareClientRun`, …) on Java 21, but that's invisible to contributors: the [Foojay toolchain resolver](https://github.com/gradle/foojay-toolchains) applied in [settings.gradle](settings.gradle) auto-provisions a JDK 21 into Gradle's user home (`~/.gradle/jdks/`) on first build. The only JDK a contributor needs to install manually is **Java 25**.

## Build Commands

```bash
# BuildCraft builds with Stonecutter: ONE source tree, one node per MC LINE.
# Today there's a single node (26.1.2) that ships as the universal 26.1.x jar.
# Tasks are per-node — qualify with :26.1.2:. Unqualified tasks run across all nodes.

# Compile the node (fast feedback)
./gradlew :26.1.2:compileJava

# Build the jar(s) and collect into build/libs/<mod_version>/ (currently one jar, +mc26.1)
./gradlew buildAndCollect

# Run the client / server
./gradlew :26.1.2:runClient
./gradlew :26.1.2:runServer

# Unit tests / headless game tests
./gradlew :26.1.2:test
./gradlew :26.1.2:runGameTestServer
```

## Multi-version builds (Stonecutter)

BuildCraft targets multiple MC versions from ONE source tree via the [Stonecutter](https://stonecutter.kikugie.dev) Gradle plugin (Kotlin DSL). The unit is an MC **line** (a real Java/mapping cliff), not a patch — each line is a **node** under `versions/<id>/` whose `gradle.properties` carries its `minecraft_version`, `neo_version`, `jei_version`, and `neoforge.mods.toml` ranges. **Today there is one node, `26.1.2`, and it ships as a single jar covering all of 26.1 / 26.1.1 / 26.1.2.** The old branch-per-MC-line model is retired — these are nodes, not branches.

Two mechanisms handle version differences, chosen by *kind*:
- **Within a line (patch deltas, e.g. 26.1.1 vs 26.1.2) → runtime, NOT directives.** The handful of diverged APIs are absorbed at load so one compiled jar runs on every patch: GUI getters use the old names 26.1.2 still keeps, and the block-break event is resolved reflectively (`lib.misc.BreakEventCompat`) because `BreakBlockEvent` (26.1.2) replaced `BlockEvent.BreakEvent` (26.1.1). Prefer this for small within-line deltas — it keeps the jar count at one.
- **Across a cliff (a new line, e.g. a future 1.21.11 on Java 21) → a new node + `//? if` directives.** Add a `versions(...)` entry in `settings.gradle.kts`, a `versions/<id>/gradle.properties`, and wrap the genuinely cross-cliff call sites in Stonecutter directives (`//? if >=… { … } //?} else { … }`). That line then builds its own jar, and per-node/active-switching tasks (`:<id>:runClient`, `Set active project to <id>`) come into play.

- **Build scripts (all Kotlin):** `settings.gradle.kts` declares the nodes; `stonecutter.gradle.kts` is the controller (active node + moddev `apply false`); `build.gradle.kts` is the shared per-node build. `src/` lives at the tree root.
- `./gradlew buildAndCollect` builds each node and collects the `+mc<tag>` jars into `build/libs/<mod_version>/`. The 26.1.x jar is tagged `+mc26.1` (it covers the line); `mc_jar_tag` in the node's `gradle.properties` sets that tag.
- **Kotlin DSL gotcha:** inside a `tasks.xxx { }` block, `property("p")` resolves against the *task*, not the project — read gradle.properties values into top-level `val`s (see [build.gradle.kts](build.gradle.kts)).

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

## NeoForge Version Tracking

NeoForge for Minecraft 26.1 is pre-release — new beta builds land daily, sometimes hourly. `neo_version` is the pin — now **per-node** (`versions/<node>/gradle.properties`); root [gradle.properties](gradle.properties) keeps the active-node (26.1.2) value, which is what the version-check hook below reads. Two scripts under `scripts/` keep the project aware of upstream and able to cross-reference the right sources.

### Awareness — the SessionStart hook

`.claude/settings.json` registers a `SessionStart` hook that runs `scripts/neoforge-version-check.sh`: it reads `neo_version`, fetches NeoForge's `maven-metadata.xml`, filters to the `minecraft_version` line (ignoring the unrelated 21.1.x LTS line), and — only when the pin is behind — injects a one-line notice into the session. Silent when current; fails silently when offline. Check manually anytime with `bash scripts/neoforge-version-check.sh --plain`.

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

`.neoforge-ref/` (gitignored; populated by `scripts/neoforge-sources-sync.sh`) holds reference sources for the **pinned** versions. **It is the first place to look when you need to know how to call a NeoForge or vanilla API** — grep it rather than recalling signatures from memory; the 26.1 API line is new and churns constantly.

- `.neoforge-ref/sources-<neo_version>/` — decompiled `.java`, one Grep/Read root:
  - `net/minecraft/**` — patched Minecraft (vanilla + NeoForge's patches to it)
  - `net/neoforged/**` — the NeoForge framework: FML, capabilities, registries, events, attachments
  - `com/mojang/**` — Mojang libraries (blaze3d, datafixers, …)
- `.neoforge-ref/vanilla-<minecraft_version>/<minecraft_version>.jar` — the pure, unpatched, deobfuscated vanilla client jar. Bytecode, not source — inspect with `javap` (e.g. `javap -p -cp .neoforge-ref/vanilla-26.1.2/26.1.2.jar net.minecraft.client.Camera`). Authoritative ground truth for unpatched vanilla.

Dir names are version-stamped. If they don't match `gradle.properties` — or `.neoforge-ref/` is absent — run `scripts/neoforge-sources-sync.sh` (~1 min; stale version dirs are pruned automatically).

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

**To verify your test is actually running** (not silently skipped): note the "N GAME TESTS COMPLETE" count before and after. Each new test should bump N by 1. If it doesn't, the manifest is missing or its `function` field doesn't match the registered ID. Confirm by temporarily making the test throw — if the failure shows up in the "required tests failed" list, it's wired correctly; if it doesn't, fix the manifest first before debugging the test logic.

**Player-state testing limitation**: `GameTestHelper.makeMockPlayer(GameType)` returns an anonymous `Player` (see [GuiTester.java:64-66](src/test/java/buildcraft/lib/test/gui/GuiTester.java)), NOT a `ServerPlayer`. Anything guarded by `instanceof ServerPlayer` (including `AdvancementUtil.unlockAdvancement(Player, …)`) short-circuits silently. Test the predicates and the wiring around player-state calls; the final award/tracker write needs in-client verification.

### User notes
- When cross-referencing code from 1.12.2, there are multiple locations code can be; as a .disabled file in the current branch, or in the 8.0.x-1.12.2 branch in either the `src_old_license` folder (for code that was written before the license migration, very old) or in the `common` folder (actually used 1.12.2 code)
- Creating and running tests is critical, they should be written whenever and for whatever reason. Minecraft version bumps entail a lot of architectural changes all the time.
- The neoforged-docs MCP tool can be useful sometimes.
- **API lookup:** to find how to call a NeoForge or vanilla API, grep the decompiled sources under `.neoforge-ref/` — see [NeoForge Version Tracking](#neoforge-version-tracking). Don't recall 26.1 API signatures from memory; the line is pre-release and changes constantly.
- Please use `git mv` when moving or renaming files so that the diff understands what happened.