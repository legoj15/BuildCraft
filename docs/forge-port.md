# MinecraftForge port (#209)

Standing design for the third loader. "Forge" here is MinecraftForge (`net.minecraftforge`), never NeoForge. The
platform layer rules in [platform-layer.md](platform-layer.md) apply unchanged; this file records only what differs.
Per-API evidence lives in [forge-seam-map.md](forge-seam-map.md) (272 rows, each with a `.forge-ref/` path and line).

## Scope

- Nodes are `<line>-forge`, declared `version("<line>-forge", "<line>")`, `loader=forge` in the node's properties.
- Order: **26.3-forge** (active line; EventBus 7, the shape 26.2 / 26.1.2 / 1.21.11 share), then **1.21.1-forge**
  (the only Forge line with an audience; EventBus 6, its own event fork).
- Then (F8, 2026-11-21) **1.21.11-forge** (Forge 61, EventBus 7, server and client; "The 1.21.11 line (F8)" below) and
  **26.2-forge** / **26.1.2-forge** (Forge 65 / 64, the same EventBus 7 shape as 26.3, with version arms where Forge 64/65
  or vanilla 26.1/26.2 differ; "The 26.2 and 26.1.2 lines (F8)"). The five Forge lines' version arms are one chain per file
  (`>=26.3` / `>=26.2` / `>=26.1` / `>=1.21.10` / else, each present only where that line differs).
- **No 1.21.10 Forge node, ever** (user decision, #210: Forge takes no PRs for that branch).
- Nothing ships until the user says so.

## Toolchain

- ForgeGradle 7.0.40 (`net.minecraftforge.gradle`) through `bc.forge` in buildSrc; the Minecraft Mavenizer builds the
  patched artifact. Official names at runtime on every target line: no reobf, no refmap, no remap mode.
- `E:\GitHub\McDevBridge\build.forge.gradle.kts` is the working reference (same wrapper, same Stonecutter).
- Access transformer: the NeoForge node's own `src/main/resources/META-INF/accesstransformer.cfg` is handed to the mavenizer
  with `minecraft.getAccessTransformer().from(...)` in `bc.forge.gradle.kts` (set on the extension: the per-dependency
  `minecraft.dependency(...) { }` block cannot be configured from Kotlin, its Action overload throws a ClassCastException).
  Official names, so every entry applies as written. Forge's own transformer opens none of what NeoForge's does, so the
  file also lists the vanilla members BC reads that NeoForge opens itself (a no-op on a NeoForge node); a shared-source
  read of any other NeoForge-opened member fails the Forge compile with a "has private/protected access" error, and the
  fix is a line there. Not yet opened, so still reflective in `platform/forge`: `RecipeManager.recipes`,
  `CustomData.tag`, and Forge's own `SidedInvWrapper` fields. Opened for Forge's sake: `AbstractHorse.inventory` (a horse's
  handler is read from it, `ForgeVanillaHandlers.horse`). The target class is `animal.equine` from 1.21.11 and `animal.horse`
  before it, so a Forge node on an older line adds the other line to the file; the NeoForge nodes tolerate a line whose class
  they lack (the GhostSlots pair is absent on 1.21.1: `:1.21.1:compileJava` applies the file).
  **Never open a member Forge itself redirects.** Forge's `forge-transformers` jar (`FieldToMethodTransformer`) rewrites
  outside reads of `LiquidBlock.fluid` into `getFluid()` and refuses a class whose field is not private, so an AT line on
  it kills the boot at the first `LiquidBlock` load ("Field ... is not private and an instance field"). The line F3 added
  is gone; `BlockUtil.getFluidWithFlowing(Block)` reads the block's fluid off its level-0 state instead (the same fluid
  vanilla caches from the constructor's argument, and no field read on any loader).
  **At runtime** the same file ships in the jar as `META-INF/accesstransformer.cfg` (FML reads that name from a Forge mod
  as well; the dev run's Minecraft already carries it from the mavenizer, so the second application is idempotent).
- The viewer plugin packages (`compat/jei`, `compat/rei`) compile on a Forge node only if it has a `jei_version` /
  `rei_version` property (none has a viewer for 26.3), so `26.3-forge` builds the rest of `src/main/java` without them.
- Forge needs `META-INF/mods.toml` (not `neoforge.mods.toml`) and a `pack.mcmeta`, or FML covers the title screen with
  a load warning. Pack fields differ: `pack_format` on 1.21.1, `min_format`/`max_format` on 26.x.
- **Resources (F4).** The node ships `src/main/resources` less `META-INF/neoforge.mods.toml` and the Curios/Accessories data
  (`data/{curios,accessories}`, and the Curios entity assignment under BC's own namespace; no Forge builds), plus
  `src/main/resources-forge/` (the twin of `resources-fabric`): `META-INF/mods.toml`, `pack.mcmeta` and the mixin config.
  `bc.forge`'s `processResources` fills the placeholders (`forge_version_range`, `forge_loader_version_range`, `mc_dep_range`,
  `pack_fields`, `mod_version`, `update_branch`), with `DuplicatesStrategy.FAIL`, then runs `DataForge` and its
  no-`neoforge:`-left guard. The jar is `BuildCraftRedux-<mod_version>+mc<mc>-forge.jar`, collected into
  `build/libs/<mod_version>/`; `nodeVerification` is compile, resources, jar and collect (no run, no game tests until F5).
  `mods.toml` carries `logoFile` only (Forge reads no banner/icon key), the update JSON URL (the shared
  `BuildCraftVersions.json`: Forge's checker answers AHEAD/UP_TO_DATE from it), and `forge` + `minecraft` dependencies.
  ForgeGradle needs `net.minecraftforge.gradle.merge-source-sets=true` in the root `gradle.properties` for a dev run to find
  the classes and the resources in one mod (the mod file is `versions/26.3-forge/build/sourceSets/main`, not the usual
  `build/classes` + `build/resources`).
- Reference sources: `.forge-ref/` (gitignored, filled by `bash scripts/forge-sources-sync.sh`), Forge's classes plus `patches/` (vanilla patch diffs, the place to see
  where a hook is called) per line. The 1.21.1 tree names vanilla members by SRG, so grep it by Forge names. FML core
  (ModConfig, ModList, ModLoadingContext, the event bus) is not in it: use `javap` on the cached jars.

## What Forge is, measured against the two loaders we have

Forge is closer to NeoForge 1.21.1 than to anything newer, and on the client it is closer to Fabric.

| Area | Forge (all target lines unless said) | Consequence |
|---|---|---|
| Event bus | 1.21.1: EventBus 6 (`MinecraftForge.EVENT_BUS`, `setCanceled`). 1.21.6+: EventBus 7 (static `X.BUS` per event, cancel by returning `true`, mod bus is a `BusGroup`); `MinecraftForge.EVENT_BUS` is for-removal on 26.3 | One `ForgeBus` helper in `platform/forge` hides the two generations; no other file names a bus |
| Capabilities | `ICapabilityProvider` / `LazyOptional` / `ForgeCapabilities`; no `BlockCapability`, no transfer API, no `RegisterCapabilitiesEvent` on 26.x | The classic path (NeoForge's `<1.21.10` arm) is Forge's only path, on 26.x too. Exposure is `AttachCapabilitiesEvent` plus invalidation, not a registry |
| Item / energy handlers | `IItemHandler`, `IItemHandlerModifiable`, `IEnergyStorage` have NeoForge 21.1's exact method sets | Port by package rename, `LegacyTransactions` and `ClassicSlotLedger` included |
| Fluids | `FluidStack` is NBT-based: no components, no `StreamCodec` (`FluidType.getBucket(FluidStack)` exists); no fluid data component; no water-like flag; `BucketItem` capability wiring and the item fluid-handler templates are commented out upstream, and `FluidUtil.getFluidHandler(ItemStack)` always answers empty; `forge:fluid_type` registers after every vanilla registry, fluids included | `platform/forge/ForgeFluid*`, `ForgeFlowingFluids`, `ForgeBoatFluids`. Item handlers come from `stack.getCapability(FLUID_HANDLER_ITEM)` (a `FluidBucketWrapper` per plain bucket, attached by an `AttachCapabilitiesEvent.ItemStacks` listener installed at construction); fluid-on-item content is `ForgeFluidContent` (after `FabricFluidContent`); thick-fluid motion is `FluidType.move` and needs no mixin, only the water-like flag does (F3); boat drag rides `LevelTickEvent.Post` |
| Attachments | none | BC's own saved data / capability NBT, as `FabricAttachmentServices` does |
| Fake players, chunk tickets | no `FakePlayer`; `ForgeChunkManager` only on 1.21.1 (gone on 26.x) | Follow `FabricFakePlayers` and `FabricChunkTickets` (vanilla `TicketType`) on every line |
| Menus | `IForgeMenuType.create` + `IForgeServerPlayer.openMenu(provider, Consumer<buf>)`; vanilla `openMenu(provider)` is unhooked | `openFor` writes the data itself; the spectator path is `platform/forge/mixin/ServerPlayerMixin` (F3c) |
| Config | `ForgeConfigSpec` (near-identical builder; no 4-arg `defineListAllowEmpty`; types CLIENT / COMMON / SERVER only) | Stonecutter replacement `ModConfigSpec` -> `ForgeConfigSpec` on forge nodes, like the `Identifier` rule; LOCAL maps to COMMON as on Fabric |
| Patched overrides | Of the 37 hook rows in the seam map (33 `@LoaderHook` uses plus the `platform/hook` and `VanillaExtensions` routes), 17 compile unchanged and 14 differ in signature; missing: clone-item-stack, block shearing on 26.2+, per-entity tick, `writeClientSideData` | Same-shape ones become `//? if neoforge || forge`; the rest route through `platform/hook` interfaces and Forge mixins |
| Client | No menu-screen event, no item tint source / sprite source / render pipeline registration, no level-render stage event after 1.21.4, no per-vertex quad colour, no PiP submit | Vanilla registries reached by access transformer where possible, otherwise the Fabric client mixins |
| Data | `forge:` namespace; `fluid_container` is a model-file loader, not an item-definition type; no components ingredient; recipe conditions are read at the top of any data-driven-registry datum on 26.3 (the seam map's "not found" was wrong); no `recipe_priorities` | `DataForge.kt` rewrite beside `DataFabric.kt` (decisions below); BC-registered ingredient serializer and flag condition |
| Tests | No FML-JUnit. 26.x: `TEST_FUNCTION` is a vanilla registry, test instances are datapack JSON, entry is `net.minecraft.gametest.Main` (F5: measured, all of it holds). 1.21.1: annotation / `Method` based | Fabric's arrangement: no unit tests, the compile is the deprecation guard, the shared game tests are the gate (`:26.3-forge:runGameTest`, below) |

## Data on Forge (F4): `DataForge.kt`, decision per kind

BC's data stays NeoForge-shaped in `src/main/resources`; the Forge node's `processResources` rewrites its copy and fails
the build on any `neoforge:` it does not know (the guard that stands in for tests: the node has none). Each row was read
from `.forge-ref/sources-26.3-66.1.8` and Forge's universal jar, then proved by the boot.

| NeoForge datum (count) | Forge form | Why |
|---|---|---|
| `neoforge:conditions` list, top of 18 `pipe_*_rf` recipes | `"forge:condition": {condition}` (one object; several become `forge:and`) | `ForgeHooks.readConditional` / `ConditionCodec.wrap` read `ICondition.DEFAULT_FIELD` = `forge:condition` at the top of a datum. Forge patches `ResourceManagerRegistryLoadTask` (every data-driven registry, recipes included on 26.x) and `SimpleJsonResourceReloadListener` with it. Not below the top: `DataForge` fails on a nested one. `buildcraftunofficial:rf_enabled` passes through: `ForgeDataServices` registers it in `forge:condition_codecs`. NeoForge's stock ids map to `forge:true/false/not/and/or/mod_loaded/tag_empty/item_exists` (item registry only), field names read from the codecs |
| `neoforge:ingredient_type: neoforge:components` (26 gate recipes) | `{"type": "buildcraftunofficial:components", items, components}` | Forge's ingredient JSON dispatches on `type` through `forge:ingredient_serializers`; it has no component ingredient (its `NBTIngredient` compares `custom_data`). `ForgeCraftingServices.ComponentsIngredient` now has a codec (`items` holder set + `DataComponentPatch`), a stream codec and a registered serializer, called from the entrypoint before `BCCore.init`. Strict mode fails the build, as on Fabric |
| `data/<ns>/neoforge/biome_modifier/*.json` (3) | moved to `data/<ns>/forge/biome_modifier/` (the `forge:biome_modifier` datapack registry); `neoforge:add_features` becomes `forge:add_features` (same `biomes`/`features`/`step` fields, step names read from `Decoration.CODEC`) | `ForgeMod` registers `forge:add_features` and the datapack registry. `buildcraftunofficial:add_oil` passes through unchanged: `ForgeAllBiomesFeatureModifier`'s type is registered by `registerAllBiomesFeature` under that id with a `feature` field |
| `data/neoforge/recipe_priorities.json` | dropped | Forge has no recipe priorities (nothing in its sources or patches). The shared 8-glass tank grid falls to vanilla order, as on Fabric |
| `neoforge:fluid_container` item definitions (31: 30 buckets + `fragile_fluid_container`) | `items/<n>.json` keeps its place and becomes type `buildcraftunofficial:fluid_container` (F6), same fields (`fluid`, `textures`, `flip_gas`, `apply_fluid_luminosity`, `force_opaque_fluid`, `cover_is_mask`) | F4 first wrote a model file with `"loader": "forge:fluid_container"`; F6 replaced it, because Forge's `DynamicFluidContainerModel` bakes per fixed fluid, reads `block/` sprites as `item/` and registers no tint (see "Client on Forge (F6)", Fluid containers). `neoforge:item/mask/bucket_fluid_drip` becomes `forge:item/mask/bucket_fluid_drip` (Forge's own mask in its universal jar), so no BC texture is needed |

## Running the dedicated server (F4)

`./gradlew :26.3-forge:runServer` runs in `versions/26.3-forge/run/` (gitignored), with `buildcraft.dev` set and
`-PbcDebug[=tier]` honoured; `-PbcRunDir` and `-PbcQuickPlay` are client-only, as on NeoForge. The server will not start
without `run/eula.txt` (`eula=true`, written by hand: the F4 task's brief said to; `bc.fabric` records the user's earlier
acceptance, 2026-11-06, for Fabric's client-game-test run dir only). It boots to
`Done (0.36s)!` with BuildCraft constructed (the five `BuildCraft <Subsystem> initialized` lines and the
`Forge Version Check` line for `buildcraftunofficial`). To stop it cleanly from a script, feed its stdin from a pipe you can
append to (`tail -f cmds.txt | ./gradlew :26.3-forge:runServer`, then `echo stop >> cmds.txt`): Gradle's stdin forwarding fails on
a Windows named pipe ("Access is denied") but works on an anonymous one.

What the first boots turned up, in order:

1. **`Field ...LiquidBlock.fluid is not private and an instance field`**: the F3 access-transformer line (see Toolchain).
2. Nothing else in BuildCraft: constructed, registries, `forge:condition_codecs` / `forge:biome_modifier_serializers` /
   `forge:ingredient_serializers` entries, config registration, network registration, the data pack (every recipe, loot
   table, advancement, tag and biome modifier) all loaded with no BuildCraft error. The six client holders that throw
   `not ported (#209)` were never touched by a dedicated server.
3. Proof the data is live, not merely parsed (temporary log lines, since removed): with `disableRfPipe=true` all 18
   `rf_enabled` evaluations returned false and the recipes were dropped; all 26 components ingredients decoded through the
   registered serializer; `add_oil` ran for 67 biomes with its placed feature resolved to `buildcraftunofficial:oil`.

Log noise that is not BuildCraft's, expected on this machine: `HkeyPerformanceDataUtil` / `OSProcess` NullPointerExceptions
(oshi cannot read the Windows perf counters), a `Failed to load properties from file: server.properties` stack on a run
dir's first boot, `VanillaPackResourcesBuilder` "unexpected schema" warnings (the dev jar is a union filesystem path), the
`forge-common.toml` / `buildcraftunofficial-common.toml` "Incorrect key ... corrected" warnings on a first boot (FML writes
the spec's defaults), and two `AppenderLoggingException ... io.netty.channel.kqueue/epoll.Native` errors from the debug-file
appender (netty's native-transport probe logging into the appender it is being called from; no BuildCraft frame).

## Mixins on Forge (F4 probe, F3c)

Settled on 26.3-66.1.8 by a one-mixin boot in F4 (a probe on `MinecraftServer#runServer`, deleted in F3c once the real
mixins below proved the same mechanism: each shows "Mixing <Name> from buildcraftunofficial.forge.mixins.json into ..." in
`run/logs/debug.log`):

- **Declaration.** There is no mods.toml key: no FML 26.3 class reads a `mixins` table (strings of fmlloader, fmlcore and
  javafmllanguage searched). An installed mod declares its config with the jar manifest attribute `MixinConfigs`
  (`bc.forge`'s `jar` sets `buildcraftunofficial.forge.mixins.json`), which Mixin's `MixinPlatformAgentDefault` reads from
  the container FML hands it (the debug log shows "MixinPlatformAgentDefault accepted container ...(buildcraftunofficial)";
  FML's `JarInJarDependencyLocator` copies the same attribute for nested jars). A **dev run has no manifest** (the mod file
  is a directory), so the run declares it instead with the launch argument `--mixin.config buildcraftunofficial.forge.mixins.json`
  (Mixin's modlauncher option; `bc.forge` adds it to every run). The argument is also what turns Mixin on in a dev run:
  `ModDirTransformerDiscoverer` sets `isMixinEnabledInDev` only when a `-mixin`/`--mixin` argument is present. The
  manifest path was not exercised in a production install (no Forge server install here; installing one is a download).
- **Mixin and its library.** Forge 26.3-66.1.8 ships SpongePowered Mixin 0.8.7 and MixinExtras 0.5.4 (the common half is a
  compile-only dependency the mavenizer adds; the boot log shows "Initializing MixinExtras ... version=0.5.4", so
  `@WrapOperation`, `@WrapMethod` and `@Local` are available at runtime). Mixin 0.8.7's `CompatibilityLevel` stops at
  `JAVA_21` (no later constant in the jar), so the config says `JAVA_21`, not the node's Java 25 (Fabric's config expands
  the node's level; its Mixin is newer). It logs at debug level that JAVA_21 exceeds the JAVA_13 it can verify, and that a
  Java 25 mixin class (version 69) exceeds the class version it knows; every mixin applied regardless.
- **Strictness.** The config sets `injectors.defaultRequire` 1, so a target that moves fails the class load loudly. A mixin
  applies when its target class first loads, so a boot proves the classes that load at startup (`Entity`, `ServerPlayer`,
  `AnvilMenu`, `FloatGoal`, the registry wrapper, the saved-data storage); the two that load late
  (`ServerGamePacketListenerImpl`, `Swim`) were proved by loading them from a temporary probe.
- **No refmap, no annotation processor.** Forge 26.3 runs official names, so a Forge mixin names vanilla members as the shared
  source does (`bc.forge` adds no processor).
- **Layout.** `buildcraft.platform.forge.mixin` (package-private abstract classes, body in `//? if forge {`, like the Fabric
  ones), listed in `src/main/resources-forge/buildcraftunofficial.forge.mixins.json`, which only the Forge jar carries. A
  mixin shared in spirit with Fabric's is a second file of the same name, not a shared one.
- **A mixin's javadoc** names the NeoForge hook it stands in for and why its injection point is stable against Forge's
  patch of the method. Patched vanilla for reading: the mavenizer's `injected-sources.jar` for the node's Forge version
  (`~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/<mc>-<forge>/`), which is
  vanilla with Forge's patches applied.

### Hooks Forge 26.3 lacks, and how each is served (F3c)

| Hook | Served by | Why that point, and what the boot showed |
|---|---|---|
| Legacy-id aliases through vanilla codecs (`Item.CODEC`, every `holderByNameCodec`) | `NamespacedWrapperMixin`: tail of the wrapper's `get(Identifier)` and `get(ResourceKey)`, on an empty answer, through `ForgeRegistry#getRaw` and `getKey` | NeoForge's `MappedRegistry` resolves in those two; Forge's own aliases reach `getRaw` only. All 372 `LegacyAliases` mappings (items 198, blocks 72, fluids 60, block entities 38, components 4) resolve by id, by key, by `byNameCodec` and by `holderByNameCodec`; without the mixin the key and both codec lookups missed all 372. `containsKey` follows aliases on Forge and not on NeoForge: left as Forge has it |
| `IBCMenuProvider.writeClientSideData` (spectator `openMenu`) | `ServerPlayerMixin`: head of vanilla `ServerPlayer#openMenu(MenuProvider)`, then `ForgeMenus#openWithOpeningData` over Forge's own `openMenu(provider, writer)` | Forge's `IContainerFactory` hands a menu that arrives through the vanilla packet a `null` buffer; Forge's patch of the method only adds an event call at its tail. A connection-less player on an embedded channel: a `MenuOpeningData` provider opens (id 1) with its data written, a plain provider takes vanilla's path (id 2), a provider whose `createMenu` answers null gives empty |
| Level-aware saved data | `SavedDataStorageMixin` (`@WrapMethod` on `computeIfAbsent`, `get`, `collectDirtyTagsToSave`) and `ServerChunkCacheMixin` (constructor tail), with `ForgeSavedDataLevels` | Forge's own `LevelCapabilityData.type(level)` builds one `SavedDataType` per level, which cannot serve BuildCraft's static shared `TYPE`s (every call site would change, and the save-time encode still needs the storage's level). The factory saw `minecraft:overworld`, `the_nether` and `the_end` for the three dimensions' storages, and the encode wrote their files |
| Water-like flag (`FluidProperties.waterLike`) | `EntityMixin` (wraps `baseTick`'s `isEyeInFluid(WATER)` and `updateFluidInteraction`'s `isInFluid(WATER)`, injects before `checkBelowWorld`), `EntityFluidInteractionMixin` with `EntityFluidTrackerAccessor` (the per-fluid tracker list), `FloatGoalMixin`, `SwimMixin`, `ForgeFluidPhysics` | Forge keeps vanilla's tag-based water state beside its per-type trackers, and every BuildCraft fluid is in the water tag. What Forge already asks of the fluid type needed nothing (currents by `motionScale`, wading by `move`, boating, drowning, item bobbing). A pig in four pools: water and `fuel_light` `isInWater` true, fall distance 0; `oil` and `oil_residue` `isInWater` false, fall distance 10 to 5 (the type's 0.5), `FloatGoal` and `Swim` false; without `EntityMixin` oil read `isInWater` true, fall 0 |
| Lava ignition (`FireReactiveBlock.lavaIgnites`) | **No mixin.** `BCFluidBlock` sets vanilla's `ignitedByLava()` property from its constructor argument | Forge's `LavaFluid` tests that property AND `IForgeBlock#isFlammable` (`getFlammability > 0`, which `BCFluidBlock` answers from `fireBurnOdds`), and every burning band has a flammability above zero, so the pair equals `lavaIgnites(state)`; fire spread reads `getFlammability` and `getFireSpreadSpeed` and never the property. All 30 BuildCraft fluid blocks agree with `lavaIgnites(state)` (crude oil and residue unlit) |
| Block entity `onLoad` at the next block-entity tick (`LevelLifecycleBlockEntity`) | `LevelChunkMixin`: `@WrapOperation` on the one `onLoad` call in `LevelChunk#addAndRegisterBlockEntity`, adding a BuildCraft block entity to Forge's own `Level#addFreshBlockEntities` instead | Forge's patch calls `onLoad` on the spot and uses the fresh list (head of the next `tickBlockEntities`, after the block ticks) only for a chunk load; NeoForge's code puts every registration on that list. Another mod's block entities keep Forge's timing. The call's owner in the bytecode is `BlockEntity` (`onLoad` is `IForgeBlockEntity`'s default method); applied at boot, and `loader_parity_block_entity_joins_after_block_ticks` passes |
| A fake player's game mode | `ServerPlayerMixin` (head of the private `calculateGameModeForNewPlayer`, cancelling with survival for `ForgeFakePlayers.isMachinePlayer`) | NeoForge's patch and Fabric API's `ServerPlayerMixin` answer the same; Forge's patch leaves the method as vanilla has it. Asked while the vanilla constructor runs, so no game-mode change (and its tab-list broadcast) is needed. The robot tool tests read wear 1 and an emptied hand where the creative default gave neither |
| A fake player's advancement tracker | No mixin: `ForgeFakePlayers.MachinePlayer`'s constructor asks the player list for the online owner's tracker again right after `super(...)` | Vanilla's `ServerPlayer` constructor calls `PlayerList.getPlayerAdvancements(this)`, which re-points the cached tracker of that UUID at the asker; a machine built for an online owner would take the owner's tracker (rewards and advancement events then go to the dead machine) until the owner relogs. NeoForge's player list never re-points the tracker at a fake player (before the cache lookup on 1.21.1, 26.2 and 26.3; a guard after it on the lines between), Fabric API's `PlayerAdvancementsMixin` cancels `setPlayer` for its `FakePlayer`; Forge has neither. Measured by `fake_player_keeps_online_owners_tracker` (an online owner, then machines built three ways, then a reward read off the owner): with the re-ask removed it fails on `:26.3-forge:runGameTest` with `got 0 experience` (the reward went to the machine); with it, it passes on the six nodes it was run on (26.3 and 1.21.1, each on NeoForge, Fabric and Forge). The machine still shares the owner's tracker object, so BuildCraft awards through the owner by UUID (`AdvancementUtil`), never through the machine |
| Pick block on 26.x (`PickStackBlock`) | `ServerGamePacketListenerImplMixin`: `@WrapOperation` on the one `getCloneItemStack` call in `handlePickItemFromBlock`, with the listener's `player` | Forge patches neither the handler nor the call; 1.21.1 has `IForgeBlock#getCloneItemStack(..., player)` and its arm is written. Applied (the class loaded); the behaviour was not exercised, which needs a pipe holder with a pipe and a connected player |
| Anvil and `/enchant` gate (`EnchantmentFilterItem`) | `AnvilMenuMixin` and `EnchantCommandMixin` (`@WrapOperation` on `Enchantment#canEnchant(ItemStack)` with the holder in hand) through `ForgeEnchantments` | A Forge extension cannot: `canApplyAtEnchantingTable` is the table's alone (the machines stay out of it) and `isBookEnchantable` only vetoes. The Quarry takes silk touch, fortune and efficiency from a book at the anvil and silk touch from `/enchant`, refuses unbreaking; a diamond pickaxe still takes silk touch, a sword does not; without the two mixins every quarry enchantment was refused. Not routed: the `enchant_randomly` loot function's compatibility test (NeoForge's third site), which sits inside a lambda |
| Recipe sync for the guide (`syncRecipeTypes`) | `ForgeRecipeSync`: BuildCraft's own clientbound payload in batches from `OnDatapackSyncEvent`; the client end is `ForgeRecipeSync.addClientListener`, which the client slice's `ClientHooks#onRecipesReceived` calls | Forge has no switch for it. The payload carries each recipe's `Recipe.DIRECT_CODEC` NBT, not `RecipeHolder.STREAM_CODEC`: **Forge 26.3-66.1.8's `Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC` cannot encode or decode an empty ingredient slot** (it builds an empty `Ingredient`, whose constructor throws), so 295 of the 1518 crafting and smelting recipes failed to encode. With the data codec all 1518 (179 BuildCraft's) re-encode to the same bytes: 381 KB in 12 batches, the largest 61 KB against the 1 MiB clientbound cap. All 12 batches went through `Platform.network().sendToPlayer` to a player on a play-phase embedded connection without error (the payload's channel resolved and Forge's protocol check passed); the client receiver is not exercised |

`FluidFireTester.lavaIgnites` has a Forge arm that asks what Forge's `LavaFluid` asks (`state.ignitedByLava() &&
state.isFlammable(level, pos, face)`), not BuildCraft's hook, which Forge never calls and which is true by construction.

## Client on Forge (F6)

The six client holders of `ClientPlatform` are `platform/forge/client/Forge<Seam>`; client mixins are in
`platform/forge/mixin/client`, listed in the config's `client` array (so a dedicated server never loads them). The files
are `//? if forge {` with per-version arms since F7b (a few classes serve some lines only: whole-file `forge && >=1.21.10`,
`forge && <1.21.10`, or 1.21.11 alone for `ForgeLegacyFluidContainerModel`); this section describes the 26.3 arms, "The
1.21.11 line" the 1.21.11 ones and "F7b" below the 1.21.1 ones. No access
transformer line was added: what Forge's own AT leaves private is reached through accessor and invoker mixins, so the
shared `accesstransformer.cfg` (read raw by every NeoForge node too) is untouched.

| Seam | Forge hook, vanilla registry or mixin |
|---|---|
| `ClientRegistrationSinks` (`ForgeClientRegistrationSinks`) | Queue and replay, as NeoForge, through `ForgeBus.listenClient` (each event's static `BUS` on 26.3): `EntityRenderersEvent.RegisterRenderers` (block entity and entity renderers), `RegisterParticleProvidersEvent` (sprite sets), `AddGuiOverlayLayersEvent` (above-all: `add` on the root draw; above the crosshair: `addAbove(PRE_SLEEP_STACK, id, CROSSHAIR, layer)`), `RegisterColorHandlersEvent.Block` (block tint sources), `RegisterClientReloadListenersEvent` (no id), `RegisterPictureInPictureRendererEvent` (keyed by the renderer's own state class), `FMLClientSetupEvent` through `getBus(ForgeModBus.group())` (`clientSetup`, and through it the menu screens, `MenuScreens.register`, which Forge's AT opens). Straight into vanilla's maps at the call, as on Fabric: item tint sources (`ItemTintSources.ID_MAPPER`, opened by Forge's AT), render pipelines (`RenderPipelines.PIPELINES_BY_LOCATION`, Forge's AT), sprite sources (`SpriteSourcesAccessor`). Model hooks are plain listeners: `ModelEvent.ModifyBakingResult` (`onModelsBaking`, `editItemModels`, `pipeBlockModel`), `ModelEvent.BakingCompleted` at `ForgeGameHooks.priority(order)`. `fluidAppearance`: `ForgeFlowingFluids.Type#attachClientExtensions` on `RegisterClientReloadListenersEvent` (before the first resource reload; client setup raced it on 1.21.1) (`ForgeFluidTypeExtensions`: textures, no tint, fog colour and distance) plus a `ModelEvent.BakeFluidModels` listener registering the world `FluidModel` for source and flowing. `blockParticles`: `ForgeBlockParticles` asked by `ClientLevelMixin`. `headAccessoryRenderer`: nothing (no Curios on Forge 26.3). The constructor registers `ForgeFluidContainerModel`'s item model type |
| `ClientHooks` (`ForgeClientHooks`) | `TickEvent.ClientTickEvent.Post`, `ClientPlayerNetworkEvent.LoggingIn` (early: `Priority.HIGH`) and `LoggingOut` (which is where `BCLibClient` clears the guide recipe cache), `ItemTooltipEvent`, `LivingEntityUseItemEvent.Tick` (client side only), `RenderTooltipEvent.Pre`, `RenderHighlightEvent.Block` (a custom renderer drawing `submitShapeOutline` with vanilla's translucency test; misses and air skipped), `LevelEvent.Unload` and `ChunkEvent.Load`/`Unload` filtered to client levels. `onRecipesReceived` is `ForgeRecipeSync.addClientListener`. `onLevelRender` is called by `LevelRendererMixin` |
| `ClientModels` (`ForgeClientModels`) | Vanilla's `MaterialInfo` constructor; per-vertex colours in a weak identity side table (Fabric's), multiplied into vanilla's `QuadInstance` by `ModelBlockRendererMixin` (blocks, chunk mesh) and `ItemFeatureRendererMixin` (item layers); `setItemQuads` is `ItemQuads.split` |
| `ClientVanillaExtensions` (`ForgeClientVanillaExtensions`) | Forge's position-aware `Block.shouldRenderFace(level, pos, state, neighbour, face)`; PiP through Forge's public `GuiGraphicsExtractor.getRenderState()`; the scissor peek through `getScissorStack()` and `ScissorStackMixin`'s duck interface (the stack's class is private) |
| `ClientFluids` (`ForgeClientFluids`) | Sprite from the baked `FluidModel` (as NeoForge 26.x); tint from `IClientFluidTypeExtensions#getTintColor(FluidStack)` (Forge's fluid models carry vanilla's level-only tint source) |
| `ClientConfigScreens` (`ForgeClientConfigScreens`) | Nothing: Forge has no generated config screen (`TODO(#209 F6)`) |

The pipe block model (`ForgePipeBlockStateModel`) overrides `IForgeBlockStateModel`'s three-argument `collectParts`, which
Forge's `SectionCompiler` calls with the block entity's model data (`ForgeRenderData.pipe`).

**Fluid containers.** Forge's `forge:fluid_container` (what F4 converted the 31 items to) cannot serve BuildCraft: it is a
block-model geometry baked for the one fluid in its JSON (the fragile shard cannot show its contents), it rewrites the
fluid's `block/` sprite to `item/` and looks for it on the item atlas (BuildCraft's fluid sprites are block-atlas only:
a missing texture per bucket), and Forge registers no tint for its layer. So the Forge node uses the Fabric node's item
model type, ported as `ForgeFluidContainerModel` (`buildcraftunofficial:fluid_container`, NeoForge's fields), registered
through `ItemModelsAccessor` and built with `CuboidItemModelWrapperInvoker`; `DataForge` now renames the item model's type
(and swaps NeoForge's mask for Forge's `forge:item/mask/bucket_fluid_drip`) instead of writing `models/item` files.

### Client mixins (F6)

| Mixin | Stands in for | Injection point |
|---|---|---|
| `LevelRendererMixin` | NeoForge `SubmitCustomGeometryEvent` | `@Inject` before the one `finalizeGizmoCollection()` call in `submitFeatures`, with the method's `PoseStack` local (`@Local`) and node collector argument |
| `ClientLevelMixin` | NeoForge `IClientBlockExtensions#addDestroyEffects` / `#addHitEffects` (Forge's twin is attached by overriding `Block.initializeClient` and its hit hook gets no position) | `@Inject` at the head of `addDestroyBlockEffect` (cancellable); `@WrapWithCondition` on the one `addBreakingParticles` call in `addBreakingBlockEffects` |
| `ModelBlockRendererMixin` | NeoForge `BakedColors` (block quads) | `@Inject` before the one `BlockQuadOutput.put` call in `putQuadWithTint` |
| `ItemFeatureRendererMixin` | NeoForge `BakedColors` (item quads) | `@Inject` after the one `QuadInstance.setColor(int)` call in `prepareMainSubmit`, with the loop's `BakedQuad` (`@Local`) |
| `ScissorStackMixin` | NeoForge `GuiGraphicsExtractor#peekScissorStack` | none (duck interface `ForgeScissorPeek` over the private class's `peek()`) |
| `SpriteSourcesAccessor` | NeoForge `RegisterSpriteSourcesEvent` | none (static accessor, `SpriteSources.ID_MAPPER`) |
| `ItemModelsAccessor` | NeoForge `RegisterItemModelsEvent` | none (static accessor, `ItemModels.ID_MAPPER`) |
| `CuboidItemModelWrapperInvoker` | the Fabric class tweaker's opening of the constructor | none (constructor invoker) |

Gaps left (each a `TODO(#209 F6)` in the source): the config screen (`ForgeClientConfigScreens`).

**What was run (2026-11-16).** `:26.3-forge:runClient` reached the title screen (Realms check, sound engine, every atlas)
with no BuildCraft exception or WARN; the only missing-texture lines are vanilla's `minecraft:builtin/generated` particle
ones, which the NeoForge and Fabric 26.3 logs carry too. `-PbcQuickPlay=<save>` (a copy of the dedicated server's world in
`run/saves/`) joined (`Dev joined the game`) and ran on with no BuildCraft WARN or ERROR; every client mixin logged
`Mixing client.*`, `ModelBlockRendererMixin` at the first chunk mesh. `:26.3-forge:runServer` still boots to `Done` and
loads no `client.*` mixin and no `platform.forge.client` class. Nothing was checked by eye: render correctness (pipes,
fluids, buckets, outlines, overlays) is for the smoke runner (run the same day: "Smoke on Forge" below). The quick-play
client that sat about six minutes after its resource load, render thread idling in the frame limiter, with no integrated
server, was not a hang: the world was flagged experimental and vanilla's "Worlds using Experimental Settings are not
supported" confirmation (`BackupConfirmScreen`) was waiting for a click.

## Smoke on Forge (#209, 2026-11-16)

`pwsh scripts/smoke/Invoke-InClientSmoke.ps1 -Nodes 26.3-forge` (one scenario) or `Invoke-SmokeBatch.ps1 -Node 26.3-forge`
(the default list). The node is opt-in: it is not in the runner's default `-Nodes`, since nothing ships there. The runner reads a
node's loader from `versions/<node>/gradle.properties` (`loader=`), strips `-forge` for the Minecraft line, and runs
`:26.3-forge:runClient -PbcRunDir=run-smoke` (bc.forge's own switches; the game directory is the working directory, so the
McDevBridge `+mc26.3-forge` jar goes in `versions/26.3-forge/run-smoke/mods` as on the other loaders). What a Forge node lacks is
declared, not special-cased by name: a scenario or step with `"needs": ["recipeViewer"]` (or `"devProperties"`, implied by
`gradleProperties`) is reported SKIPPED with the reason on a node that lacks it (a recipe viewer is read from the node's
`jei_version` / `rei_version`: of the Forge nodes only 1.21.1-forge has one, JEI; Forge wires none of the
`-PwithGuideMe`-style switches; `-PwithMods` aliases work since 2026-11-21, see "Multiplayer smoke and `-PwithMods`" below), and
`mod-list-banner` has a Forge row click (Forge's own `ModListScreen` sorts Minecraft first). The two-client `mp.py` skipped Forge
until 2026-11-21 and drives it now (same section). Two vanilla confirmations that only a Forge-made world raised before #213 (and an older one still does) are
answered by the runner, with a console note each: "Warning! These settings are using experimental features" when the first-run
world is created, and `BackupConfirmScreen` when a world not yet confirmed is loaded (a confirmation is stored in `level.dat`
as `confirmedExperimentalSettings`, so it comes once per world). A first-run world also gets `gamemode creative`, a `tp` to the
scenario base and a wait for that chunk (a new world's spawn is random and its chunks arrive seconds after the join). No
`forge-client.toml` edit is needed: its `[client] showLoadWarnings` defaults to true and no loading-warning screen appeared in
any boot, so the NeoForge `showLoadWarnings = false` edit has no Forge twin.

Result: 53 scenarios in the default list; 39 ran (37 passed), 14 were skipped (3 need a recipe viewer; 6 crossmod-claims,
`crossmod-energy*` and `crossmod-storage` need dev mods or another loader; `goggles-curios` and `guideme-guide` are NeoForge
only). The same 39 on `26.3` passed except `line8`, whose `/screenshot` timed out once (it passes on rerun). Both Forge failures
are the guide (`port-26-3` steps 196-200, `release-2026-2-1` steps 74-78). Time: warm Forge client JVM to joined world about 15 s
quick-playing, about 22 s to the title screen on a cold first boot; the whole set took 44 minutes on each of the two nodes.

What the screenshots showed against NeoForge 26.3 (worlds differ in seed, so terrain, sky tint and the JEI side panel are not
differences): pipes, plugs, facades, fluids, engines, machine GUIs, robots, markers, goggles, Zone Planner and particles render
the same, and the mod list (Forge's own screen) shows the banner. Not the same:

1. **The guide was empty (high; fixed 2026-11-19, #212).** Cause confirmed: `ScriptableRegistry` called `Path.toFile()` on a
   union-filesystem mod path and the exception aborted every script registry; it now stays on NIO `Path` and skips only an
   unreadable source. After the fix `release-2026-2-1` passes on 26.3-forge with 202 of 202 pages. Before: `[lib.guide] Loaded 0 possible and 0 actual guide pages ... registries 26 ... pages en_us 0`
   (NeoForge: 202, registries 135, pages 225): 4 of 8 chapters, the cover reads "Everything", entries open as blank spreads,
   search results do not open, links read as raw ids (`buildcraftunofficial:pipe/iron_power`). Likely root (only the first
   half is observed): every guide reload logs `[lib.script] Failed to reload scripts` (`UnsupportedOperationException: Path not
   associated with default file system`) from `Path.toFile()` in `ScriptableRegistry.loadScripts0`, on a path
   `ForgeLoaderServices.modSourcePaths` returned for a mod whose file is not on the default filesystem (not identified;
   probably the `minecraft` or `forge` entry, whose dev jar is a union path), and `GuideManager.reload0` reloads its book and
   page registries through those scripts.
2. **Worlds were "experimental" (medium; fixed 2026-11-19, #213).** Measured with a dedicated-server probe: the only
   non-stable registry entries were BuildCraft's 3 configured features, 3 placed features and 3 biome modifiers, each
   with no known-pack information (Forge gives mod data packs none, and vanilla's `ResourceManagerRegistryLoadTask` makes
   any entry not from a known vanilla pack `Lifecycle.experimental`); Forge's own packs added none. So every Forge mod
   with worldgen data raises the warning; BuildCraft now avoids it for its own: `MappedRegistryMixin` registers an entry
   in BuildCraft's namespace as stable (known-pack information kept). After it the probe lists nothing, and a world made
   and reloaded on 26.3-forge loads with no confirmation (`confirmedExperimentalSettings` 0, as on NeoForge); both Forge
   lines boot their game tests with the mixin applied (948 and 949 pass). Worlds made
   before the fix keep their stored confirmation; the smoke runner still answers both screens for them.
3. Minor: no Config button (known then; a hand-built screen since F8, "Config screen (every Forge node from 1.21.11 up)"); the Forge pause menu's icon row has one button fewer than
   NeoForge's (a vanilla-screen difference, not BuildCraft's); after pouring a water bucket into a Tank in creative NeoForge leaves an
   empty bucket in the hotbar and Forge did not: NeoForge's player item access follows vanilla's
   `ItemUtils.createFilledResult` (a creative player keeps the item and is given the result if the inventory has none like
   it), and `ForgeFluidContainers.swapHeld` now does the same (#213). The
   `creative-bucket-tank` smoke scenario checks it (pour, pour again with an empty bucket held, fill) and passes on 26.3-forge
   and 26.3.

## Multiplayer smoke and `-PwithMods` on Forge (#209, 2026-11-21)

**`-PwithMods` on a Forge run.** `bc.forge` reads it through `WithMods.coordinates` like the other two loaders and puts the
coordinates on `runtimeOnly` (not transitive), so client, server and game-test runs all see them; an alias the node does not
define is logged and skipped (`-PwithMods=flan ignored on 26.3-forge: this node defines no withmod.flan`, printed at
configuration). FML finds a mod on a dev run's classpath without further wiring (`Loading Open Parties and Claims!` on a
`:26.3-forge:runServer -PwithMods=opac` boot, on the 1.21.1 line's too, and in the client's integrated server). The Forge nodes
define `opac` only. Two facts about it:

- **OPAC's Forge builds declare Forge Config API Port (`forgeconfigapiport`) as mandatory** (`[26.2.0,)` on 26.3, `[21.0.5,)` on
  1.21.1) and Forge ships none, so `withmod.opac` on both nodes names its Forge build too (`forge-config-api-port` 26.3.1 / 21.1.6;
  the first coordinate stays the compile pin, `WithMods.compileCoordinate`). `./gradlew withModsResolve -I
  scripts/withmods-resolve.init.gradle.kts --no-configuration-cache` resolves every alias of every node, the two Forge ones
  included (0 FAILED); the script covers every node whatever its loader, so it needed no change beyond its header comment.
- **A world's first OPAC config write dies on Windows.** With the alias loaded, the first boot of a world with no
  `serverconfig/openpartiesandclaims-server.toml` (dedicated or integrated) stops at `WritingException: Failed to write
  (REPLACE_ATOMIC) the config to .\world\serverconfig\openpartiesandclaims-server.toml` (`AccessDeniedException` renaming the
  `.new.tmp`: Forge Config API Port's NeoForge-spec adapter rewrites the file it has just created, apparently while it is still
  open). Reproduced on a fresh 26.3-forge world twice and a fresh 1.21.1-forge world once; the second boot of the same world is
  clean. Not BuildCraft's and not specific to a node. A seeded `<gameDir>/defaultconfigs/openpartiesandclaims-server.toml` (any
  world's copy of that file) makes Forge copy it instead of creating it, and a fresh world then boots at once (measured). The
  smoke runner plays a fresh copy of its seed save for `crossmod-claims-opac`, so `versions/<node>/run-smoke/defaultconfigs/`
  must hold that one file on a Forge node first; the runner does not seed it (follow-up).
- **Measured on 26.3-forge**, with that file seeded: `crossmod-claims-opac` passes (145 steps, 0 failed, 0 BuildCraft log
  errors, 1.6 min): rig A (no claim) and rig C (the dev's claim) let every machine place and break, rig B (a stranger's claim)
  refuses them all (Quarry frame, Builder, Filler, Stripes pipe and Stripes robot), so OPAC's claim check reaches BuildCraft's
  Forge protection hooks through the real mod. `crossmod-claims-opac-stranger` passes the same way (145 steps, 0 failed).
  `creative-bucket-tank` passes on the same worktree (the seed world's first
  run).

The smoke runner no longer lists dev mods as something Forge lacks (`devMods` is gone from its table; `gradleProperties`
switches, `-PwithGuideMe` and the like, are a separate `devProperties` capability Forge still lacks), and it skips a scenario whose
own `"mods"` the node defines none of (`crossmod-claims-flan*` on a Forge node: "needs a dev mod this node does not define").
A step's `"nodes": ["26.3"]` names the Minecraft line and, as it always did for the Fabric twin, also covers the Forge twin; a step
only one loader can run is `"loaders"`-filtered (decision for #209's runner note; the comment and README now say so).

**Two-client multiplayer smoke on Forge.** `scripts/smoke/multiplayer/mp.py` drives `26.3-forge` (it skipped a Forge node with
exit 0 because `mp.init.gradle` only extends the NeoForge run extension). `bc.forge`'s client run takes `-PbcUser=<name>` (the
`--username` value, default `Dev`) and `-PbcQuickPlayServer=<host:port>` (`--quickPlayMultiplayer`), and its server run honours
`-PbcRunDir` (the game directory, as the client run does); `mp.py` passes those on a Forge node and `-PmpUser` / `-PmpServer` /
`-PmpServerDir` on a NeoForge one (`run_switches`), and `setup` takes the bridge jar and default options itself on a node no
single-player run has prepared. A Fabric node has no arm yet and is skipped with a reason. The Forge dedicated server prints
the vanilla `Done (0.3s to 0.7s)!` line and a Forge client's `--quickPlayMultiplayer` joins it.

- **Scenes.** `scene_swing_particles.py` (every phase) and the new `scene_machines.py` (a Tank poured by one player and opened by
  both, a Stone Engine's GUI, a creative engine's kinesis rig filmed by the other player; 6 recorded checks) ran on `26.3-forge`
  and on `26.3`, each against a loopback server and against Server1's (four full cycles: setup, server, client A, client B,
  both scenes, stop), with the same result every time; the Forge clients' and servers' logs held no BuildCraft error. Swing phases: three robots deployed from
  their stations and at work in Watcher's shots, Worker's wrench swing is seen on Watcher's client (the
  raised arm, the "Wrenched!" advancement), and while Worker mined the gold pipe under Mining Fatigue Watcher's screen showed the
  gold pipe-sprite crack particles and then the break (the 26.3 remote destroy-progress path, served on Forge by
  `ClientLevelMixin`). `scene_machines.py`: the Tank holds 1000 mB of water on the server after Worker's bucket; the Tank GUI
  (`GuiTank`) opens on Worker and then on Watcher, who never touched it, with the water gauge on both; the Stone Engine's GUI
  (`ScreenEngineStone`) opens; the creative engine is still running. That exercises the menu opening data
  (`ForgeMenus.openWithOpeningData`), the container's fluid sync and the block entity and pipe sync over a real connection. Not
  exercised: the join's recipe payload (`ForgeRecipeSync`; no scene opens the guide, and nothing in the logs says it was received).
- **The server on another machine.** `MP_REMOTE_CHECKOUT=bc-forge-mp` (a checkout under `C:\Dev` on Server1, synced from the worktree
  with `Sync-ToServer1.ps1`, committed state only) runs the server half through McDevBridge's `tools/remote` (`Invoke-OnServer1
  -NoDesktop`, session 0, JDK 25.0.2.10, scratch ports 25661/25662, a per-run RCON password): the game directory is written by the
  remote script, `gradlew :<node>:runServer` runs there, the log is fetched over scp for the `Done` wait and RCON `stop` ends it.
  Both clients ran on the workstation (192.168.0.28 in the server's log) and joined `192.168.0.106:25661` over the LAN. Server1's
  Windows firewall has Private/Public inbound allow rules for that JDK's `java.exe` on any port (read only, nothing changed), and
  the workstation's TCP connect and RCON both got through. A cold Gradle compile of the Forge node in the fresh checkout took 3 min
  52 s; the warm server boot to `Done` about 45 s end to end. `server` writes a per-run nonce into the remote game directory and the
  waiter trusts the log only once it sees it (an earlier run's `Done` once answered for a new start). Two traps of the remote
  tools bit while building this, both now handled in `mp.py`: `Invoke-OnServer1.ps1` twice stalled in its own ssh step with no run
  directory on Server1 (trap 16 of its README; `server` ends a poller with no `-- t+` status line after 150 s and starts again),
  and ending the LOCAL poller does not stop a remote run that has started (a lost lock race left a Gradle and a server running on
  Server1 and the next start answered for them, so every kill path now also ends the java processes whose command line names the
  checkout, over plain ssh, and nothing else).
- **Forge's RCON never answers a command with an empty reply (found by the scenes).** `gamemode creative Watcher` on a player
  already in creative prints nothing, and on a Forge dedicated server the RCON client then waits forever (the NeoForge server
  answers at once). Cause, read in `.forge-ref/sources-26.3-66.1.8/patches/net/minecraft/server/rcon/thread/RconClient.java.patch`:
  Forge rewrote `sendCmdResponse` as `for (x = 0; x < data.length; x += 4096)`, which sends no packet for an empty string, where
  vanilla's `do { ... } while (0 != len)` (and NeoForge's copy) sends one empty packet. Any RCON client waiting for a silent
  command's reply hangs on Forge. The scene helpers give a `gamemode` command a 4 s wait and a fresh connection. Worth a report
  upstream (MinecraftForge) when the user wants one filed.
- Shared-lock note: the smoke lock is machine-wide, so every run above queued behind other sessions' runs (`MP_LOCK_WAIT_MINUTES`
  makes `server` wait; the batch driver always did). The scripts never take a lock they cannot hold for the whole run.

**The other four Forge lines (2026-11-22).** `26.2-forge`, `26.1.2-forge`, `1.21.11-forge` and `1.21.1-forge` each ran one full cycle
(setup, `server` on Server1 to `Done`, client A and B joined, `scene_swing_particles.py` prep, deploy, watchrobots, wrench,
robotclick, dismantle and mine, `scene_machines.py all`, `stop`), the server on Server1's checkout `bc-forge-mp`, both clients on the
workstation. Result on every line: `scene_machines.py` 6 checks, 0 failed (Worker aims at the Tank; the server's `data get` holds
1000 mB of water; `GuiTank` opens on Worker and then on Watcher; `ScreenEngineStone` opens; the creative engine is still there); all
swing phases ran with no command error; the server and both clients' logs held no BuildCraft ERROR (the only ERROR lines are
vanilla's: the offline client's `Failed to fetch Realms feature flags`, and on a server's first boot of a world
`DedicatedServerProperties: No key layers in MapLike[{}]`, the rig's `level-type=minecraft:flat` with no `generator-settings`; both
also on 26.3). The servers' own `Done (...)` figures: 0.64 s (26.2), 1.10 s (26.1.2), 0.52 s (1.21.11), 0.89 s (1.21.1); a Server1 pre-build of the four nodes in the fresh checkout took 4 min 03 s, the workstation's in a fresh worktree
7 min 36 s (`gradlew --no-daemon :<node>:compileJava` for the four nodes).

- **What the screenshots show, identical on all four lines.** Robots (`robots-*s`): the Lumberjack and Picker work from their stations
  (the server: the tree's base log gone, the three item stacks gone, the axe chest holds one axe of two), the Knight stands idle,
  all seen from Watcher. Wrench (`wrench-*`): Worker's arm rises with the wrench in the frames after the click, on both the slow swing and
  the plain 6-tick one. Helmet and dismantle: Worker's arm raised at the idle robot, the helmet appears on it, then the
  sneak-wrench takes the helmet off and the next click dismantles the robot (the server's `data get ... wearables` answers
  `Found no elements` and then `No entity was found`). Mine: Worker mines the gold pipe under Mining Fatigue: the pipe is intact
  while it is being mined, and the break shows gold pipe-sprite fragments and the dropped pipe item on Watcher's screen (no crack
  particles on 26.2, 26.1.2 and 1.21.1, as on NeoForge 26.2; the server confirms the pipe holder is gone). `1.21.11-forge` also
  shows one tiny white speck at the pipe's lower edge in every progress frame (not seen on the other lines, unexplained, not compared
  with NeoForge 1.21.11). Tank (`tank-gui-worker`, `tank-gui-watcher`): the Tank GUI with a blue sliver at the bottom of its gauge
  on both clients, Watcher never having touched it. Engine (`engine-gui-worker`): the "Stirling Engine" GUI; `engine-rig-0..2`: the
  creative engine on its redstone block pushing power into the kinesis pipes. On `1.21.1-forge` JEI's item list is drawn beside
  every GUI (it is that node's default viewer).
- **1.21.1 has no swing component.** `swing_animation` exists from 1.21.11 and `interact_animation` on 26.3; on 1.21.1 the item
  command fails, so `scene_swing_particles.py` branches on the line and gives Worker Mining Fatigue (amplifier 9, a 26-tick swing)
  instead. Watcher's frames catch the raised arm on that line too. On 26.1.2 to 1.21.11 the scene is unchanged.
- **`--quickPlayMultiplayer` works on 1.21.1-forge** although its quick-play of a SAVED world is defective
  (`quickplay_drops_game_events`). Both clients joined by quick-play (Watcher 02:20:01, Worker 02:20:59 in the server's log), render at
  120 fps, and every scene check passed with no client error: the join only starts an asynchronous connection before Forge's game bus
  starts a moment later, so no event that matters is lost, and the single-player world load (the part that crashes at its first loot
  roll) never happens. `mp.py` therefore needed no menu path for it (`Open-SaveFromMenu` stays the way to open a save there).
- **The Java 21 lines are not reachable from the workstation directly.** Server1's inbound allow rules (read only) name specific
  `java.exe` files: `Microsoft\jdk-25.0.2.10-hotspot`, `Java\jdk-17`, `jdk-22`, `jdk-24` and two JREs 1.8. The 26.x servers run on the
  first, so the clients connect straight to `192.168.0.106:25661`. `1.21.11-forge` and `1.21.1-forge` build for Java 21, so Gradle's
  toolchain starts their server on `C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe`, which has
  no rule: the game port and RCON both timed out (`Couldn't connect to server ... Connection timed out`). Opening that is a
  firewall change on Server1, not done. Instead `mp.py server` probes both ports once `Done` is printed and, when they do not
  answer, runs `ssh -N -L` for both to the server machine's loopback (Windows Firewall does not filter loopback): the clients and RCON
  then use `127.0.0.1:25661` / `:25662` and `stop` ends the tunnel. The traffic still crosses the LAN, inside ssh, so a Java 21 line
  is checked with a real server on another machine, not with the direct client-to-server path (not exercised for them).
- **`stop`, observed for real.** Normal path (RCON `stop` answered): `stop` took 15 s (26.2), 86 s (26.1.2), 29 s (1.21.11) and 16 s (1.21.1) to return, no java process of
  `C:\Dev\bc-forge-mp` was left on Server1 (the wrapper JVM and the server JVM both gone, ports 25661/25662 no longer listening),
  no local client or Gradle of the worktree was left, the lock was released and `procs.json` emptied (26.2, 26.1.2, and the
  tunnelled 1.21.11 and 1.21.1, after which the ssh tunnel was gone too). Kill path (first `1.21.11-forge` attempt, whose RCON
  could not connect): `rcon stop failed` was reported, the straggler kill printed `stopped <wrapper pid>` and `stopped <server
  pid>`, and a read-only look afterwards found no java process of the checkout (only another run's, whose command line names
  another checkout, were there). Gradle's single-use daemon JVM does not name the checkout; it was not left behind either.
- **Two launch failures of the remote command, both handled.** The first `Invoke-OnServer1` start of `26.2-forge` sat at its
  `=== run ... ===` header (trap 16); `mp.py` ended it after 150 s and the second start ran. One start of `1.21.11-forge` ended at once
  with `remote script on server1 exited 255` (ssh itself failed, Server1 busy with another run's connections); `mp.py` now treats
  that, seen before any status line, like a stalled launch and starts again (checked with a stub log, no real 255 since).
- Not exercised on these four lines: the direct client-to-server path of the two Java 21 lines (see the tunnel above); the join's
  recipe payload (as on 26.3-forge); `26.3-forge` itself on this commit (the earlier cycles ran before the tunnel and retry code,
  which a direct-reachable server never starts); the 255 retry against a real ssh failure.

## Rules added for the third loader

- A third constant, `forge`. `constants.match(loader, "neoforge", "fabric", "forge")`.
- **No bare loader `else`.** With three loaders an `else` after `if neoforge` silently means "Fabric and Forge". Every
  loader chain names each loader it serves (`if neoforge || forge`, `elif fabric`). The five main-code forks that had a bare
  else are re-keyed (F3b), the test code's forks were re-keyed in F5, and `StonecutterDirectiveGuardTester` fails a new one in
  `src/main` or `src/test/java`. A chain whose last arm is a version split (`else` for "before 1.21.10") names the version
  instead: `elif <1.21.10`.
- Stonecutter 0.9.8 reads `&&` and `||` left to right at one rank (measured on a scratch probe: `neoforge || forge &&
  >=1.21.10` is off on NeoForge 1.21.1). A mixed condition repeats the version term after each loader:
  `neoforge && >=1.21.10 || forge && >=1.21.10`; a loader that serves every line with one that serves only the newer
  ones puts the all-lines loader last, `forge && >=1.21.10 || neoforge`, which reads the same under either rule. The
  guard and the census evaluate left to right to match, and the guard fails a condition whose value on any node differs from the usual
  precedence (`scannerFlagsAConditionWhoseReadingOrderMatters`; the old `EntityRobot` arm
  `neoforge || forge && >=1.21.10` was the one such condition in the tree: it silently dropped the NeoForge
  `onRemovedFromLevel` override on 1.21.1, so a robot whose chunk unloaded kept its reservations, and
  `robot_removal_unload_releases_what_it_does_not_keep` failed there, alone and in the full run; fixed 2026-11-21).
- `@LoaderHook` takes one entry per loader that calls the method. An override both NeoForge and Forge call under one
  name opens `//? if neoforge || forge {` and lists both; a Forge signature that differs is an `//?} elif forge {` arm
  beside the NeoForge one (commented in-tree, like every inactive arm).
- Only binder holders fail loudly when an arm is missing. A no-else `if neoforge` block compiles clean on Forge with the
  feature absent, so "it compiles" proves nothing: the gate for a seam is its game tests, and a holder whose forge arm
  still throws `not ported (#209)` is the ledger of what remains (`grep -rn "not ported (#209)" src/main`).
- Forge-only code lives in `buildcraft/platform/forge/`, body inside `//? if forge {`. Forge mixins live in
  `platform/forge/mixin` with their own config; a mixin shared in spirit with Fabric is a second file, not a shared one.
- Classic-path classes that are NeoForge-typed today are copied into `platform/forge`, not shared through a combined
  loader-and-version directive (that nesting is banned). Accepted duplication, about 1.2k lines; revisit if a third
  copy ever appears.

## Slices

| Slice | Content | Gate |
|---|---|---|
| F0 | Spike: compile-only `26.3-forge`, `bc.forge`, `ForgeLoaderServices`, throwing arms in all 28 holders, loader lists in the guards | `:26.3-forge:compileJava`; NeoForge and Fabric unchanged |
| F1 | `ForgeBus` + entrypoint, lifecycle, game hooks, protection, registry, config (replacement rule), network, menus, sound, loot, crafting, shears, loader services | compile of `platform/**` |
| F2 | Transfer: capability exposure and query, item / energy adapters, fluid adapters, fluid containers, flowing fluids | compile of `platform/**` |
| F3 | Common code: re-key the loader forks, `@LoaderHook` routes, vanilla extensions, Forge mixins for the missing hooks | compile of all of `src/main` |
| F3c | **Done.** The Forge mixins and routes for the hooks Forge 26.3 lacks (table under "Mixins on Forge") | `:26.3-forge:runServer` boots with every mixin applied |
| F4 | **Done.** Resources: `mods.toml`, `pack.mcmeta`, access transformer check, `DataForge.kt`, jar, dedicated server boots to Done | `:26.3-forge:runServer` |
| F5 | **Done.** Game tests on Forge; `LoaderTestEvents` forge arms; the run is in `nodeVerification` | `:26.3-forge:runGameTest` green: all 948 registered tests pass, 7 client-class tests not registered (see "Game tests on Forge (F5)") |
| F6 | **Done to boot on 26.3-forge.** Client seams, models, renderers; the dev client reaches the title screen and a world (see "Client on Forge (F6)"). The 1.21.1 arms are F7b | smoke runner on `26.3-forge` (not run in F6; the boot was checked by hand) |
| F7 | **Server side done.** `1.21.1-forge`: EventBus 6 arm of `ForgeBus`, 1.21.1 event and API shapes, plain-Mixin arms, data down-ports, vanilla tickets (see "The 1.21.1 line (F7)") | `:1.21.1-forge:nodeVerification` green: boot clean, all 948 game tests pass |
| F7b | **Done.** The 1.21.1 client: per-version arms in `platform/forge/client/**` and the client mixins, `ForgeBus.listenClient`, the fluid-container geometry loader, `EditBoxMixin` and `ParticleEngineMixin`, baked vertex alpha, JEI 19.27.0.340, the smoke runner's per-node viewer pins and menu world open (see "F7b, the 1.21.1 client") | smoke on `1.21.1-forge` against `1.21.1`: 41 pass, 0 fail; every scenario passing on 1.21.1 passes or is skipped by design |
| F8 | **26.2-forge, 26.1.2-forge and 1.21.11-forge done** (2026-11-21; see "The 26.2 and 26.1.2 lines (F8)" and "The 1.21.11 line (F8)"), with the Forge config screen on every Forge node from 1.21.11 up. Remaining: cross-mod (OPAC; JEI on 1.21.1 is done in F7b), release tooling (`release/`, gitignored), skills and AGENTS.md | `:26.2-forge:nodeVerification`, `:26.1.2-forge:nodeVerification`, `:1.21.11-forge:nodeVerification` green (949 each), smoke batches on each; the rest `fullTestSuite` |
| F8 (1.21.11) | **Server and client done.** `1.21.11-forge`: Forge 61.2.5, the 26.3 bus and slice with per-version arms where Forge 61 or vanilla 1.21.11 differ (see "The 1.21.11 line (F8)") | `:1.21.11-forge:nodeVerification` green: all 949 game tests pass; runServer and runClient boot clean; smoke batch 39 PASS / 14 SKIPPED (the 26.3-forge set), its one bridge timeout passing on the rerun |

Loader lists that must grow together (F0 does the first three): `build.gradle.kts` loader check and `constants.match`;
`StonecutterDirectiveGuardTester`; `scripts/platform-census.py`; `scripts/smoke/Invoke-InClientSmoke.ps1`
(`Get-NodeLoader`, `Get-NodeMcVersion`, `-Loader`, node list, the `neoforge-client.toml` edit);
`scripts/smoke/multiplayer/mp.py` (`run_switches`; NeoForge reads `mp.init.gradle`, Forge reads `bc.forge`'s own `-PbcUser` /
`-PbcQuickPlayServer` / `-PbcRunDir`); `release/modules/BcRelease.psm1` and `release/config/release.json`.

## Game tests on Forge (F5)

`./gradlew :26.3-forge:runGameTest` runs every shared game test on Forge's own game-test server and fails the task unless
the run reports all required tests passed. Options: `-PbcTests=<glob>` (vanilla's `--tests`, `buildcraftunofficial:pipe_*`),
`-PbcGameTestTrace` (a start and an end line per test, and the stack of a failure thrown from a body: the way to name the
tests in flight when a product exception in a level tick crashes the server, which the crash report never does),
`-PbcLoudGameTestLogs` (the console on Gradle's output instead of `run-gameTestServer/console.log`). The run directory is
`versions/26.3-forge/run-gameTestServer/` (gitignored); the log the pass check reads is its `logs/latest.log`. A whole task
is about 45 s (35 s of boot, 14 s of tests). It is part of `nodeVerification`, as on Fabric.

**How the tests load.**

- *Run type.* ForgeGradle 7 inherits Forge's userdev run `gameTestServer` (launch target `forge_userdev_server_gametest`, main
  `net.minecraft.gametest.Main`, which Forge patches to set `forge.enableGameTest` / `forge.gameTestServer`, run the mod
  loader, and agree to the EULA itself). It makes `runGameTestServer` (the main source set alone) and `runTestGameTestServer`
  (the test source set's runtime classpath): `runGameTest` is the alias of the second, and the pass check is on it.
- *One mod, assembled.* The tests are part of BuildCraft's mod, not a second mod. This FML finds a dev mod by the
  `META-INF/mods.toml` of one classpath directory; FG7's `mods { sources(a, b) }` is read by nothing in it, and two directories
  that both hold `buildcraft.transport.block` fail module resolution ("Modules buildcraftunofficial and test export package
  ..."). So `assembleGameTestMod` (`buildSrc/.../AssembleGameTestMod.kt`) lays the test output over main's in
  `build/gameTestMod`, and the test source set's runtime classpath swaps both outputs for that directory (the jar never sees it).
  The only file both outputs hold is the `c:tools/wrench` tag (the test adds `shears`), written as the union of the two.
- *Registration.* `platform/forge/ForgeGameTestHooks` (an automatic `@Mod.EventBusSubscriber(bus = MOD)` on `FMLConstructModEvent`;
  FML injects those right after the `@Mod` constructor, which has bound `ForgeModBus`) calls `BuildCraftGameTests.register`.
  `Registries.TEST_FUNCTION` is a vanilla registry and `RegisterEvent` fires for it, so `BCRegistrar` registers the functions
  exactly as on the other loaders; there is no `RegisterGameTestsEvent` on 26.x and none is used. The manifests are datapack
  JSON in the mod's own resources and load as they are; `forge.gameTestServer` (set by `Main`) is the test-only-blocks switch.
- *Source set.* `src/test/java` through Stonecutter's copy, less `platform/neoforge/**` and every file importing `org.junit`
  (there are no unit tests on Forge). `platform/fabric/**` stays: its files are whole-file `//? if fabric` comments here, and
  `FabricAdapterTester` is a bare success. `processTestResources` drops the manifests of the tests the node does not register
  (the Fabric helper), and fails on a registered test without one.
- *Excluded on Forge* (none today), in `BuildCraftGameTests.registerAll`: a test only the Forge node cannot run sits in a
  `//? if neoforge || fabric {` arm under a `// Not on Forge: <reason>` line (`GameTestManifestTester` reads an arm that names
  Fabric as registered on Fabric). A client-class test (`//? if neoforge {`) is left out of Fabric and Forge both.
- *Pass check.* The JVM exit code is trustworthy (vanilla ends the process non-zero for a failed required test, a crash, and an
  empty selection: measured) and the task also demands `All N required tests passed` (N > 0) in `latest.log`. "N GAME TESTS
  COMPLETE" counts one test more than BuildCraft registers: Minecraft ships `minecraft:always_pass`.
- *A product exception in a tick listener ends the run* (the server crashes; nothing contains it per test), so a product bug of
  that kind has to be excluded to see the rest, and is named with the trace.

**Test code on Forge.** `LoaderTestEvents`, `WorkClaimMod`, `CapabilityOnlyCart`, `FeHandlerAccess` and 12 test classes got
Forge arms (EventBus 7: an event's own `BUS`, a listener refuses by answering `true`, and is removed through the handle
`addListener` returned; a claim needs `EntityPlaceEvent` and `EntityMultiPlaceEvent` both). Where the answer was a platform
seam it is shared with Fabric (`TransferAccess.findEnergy` / `findItem`, `ForgeChunkTickets`'s owner-set probe). Not covered on
Forge: the claim-provider tests have no Common Protection API (they post the events instead, as NeoForge's arm does).

**State of the run** (Forge 66.1.8, MC 26.3; BuildCraft registers 955 tests on NeoForge and 948 here): every one of the 948 runs and
passes ("All 949 required tests passed": the 949th is Minecraft's own `minecraft:always_pass`), nine consecutive full runs since
the last code change, `nodeVerification` included; 7 are not registered (below). The table is what each of the 21 failures
of the first green-seeking run turned out to be (F5b), with the diagnosis the first triage gave where it was wrong.

| # | Root cause (as confirmed) | Tests | Fix |
|---|---|---|---|
| 1 | **Level-aware saved data** was not wired (every factory and codec saw a null level). The mixins fixed the product (F3c). The last three failures were the **test's** Forge arm: `LegacySavedDataTester.newStorage` built a storage nobody told its level | 12: nine failed (five read a null `world`, four were legacy saved-data tests) and three were excluded because a lock on a null-level box crashed the server; the mixins fixed all but `legacy_saved_data_{content_current_wins_every_type,empty_current_is_recovered,legacy_alone_migrates_every_type}` | `SavedDataStorageMixin` + `ServerChunkCacheMixin` + `ForgeSavedDataLevels` (F3c). The test arm now calls `LevelAwareStorage.buildcraft$setLevel` as Fabric's does. `filler_owns_box_from_floating_box`, `claimed_box_resizes_with_filler`, `filler_planner_save_migrates` are registered again on every loader, pass, and no longer take the server down |
| 2 | **Forge replaces the held stack object** on a refused placement (`ForgeHooks.onPlaceItemIntoWorld` puts its pre-use COPY in the hand and leaves the caller's object one short); confirmed, and `loader_parity_refused_two_cell_drops_nothing` is the same cause | 7: `stripes_refused_claim_stops_block_placement`, `stripes_fire_needs_placement_ask_at_target`, `robot_plant_failure_keeps_seed`, `robot_planter_does_not_retry_refused_planting`, `robot_planter_sleeps_after_repeated_refusals`, `builder_claim_filler_asks_and_parks`, `loader_parity_refused_two_cell_drops_nothing` | `ForgeProtection.useItemOn`: a FAIL answer with the hand holding a content-equal copy of the pre-use stack that is not the caller's object is put back in place (count and components) and the caller's stack goes back in the hand, as NeoForge's hook leaves it |
| 3 | **A fake player took the server's default game mode** (creative on the game-test server). Confirmed. NeoForge patches `calculateGameModeForNewPlayer` to answer survival for a fake player, and Fabric API does the same with a mixin on that method | 3: `robot_use_tool_holds_tool_in_hand`, `robot_use_tool_breaking_tool_empties_hand`, `robot_deployer_stripes_throw_not_refused_for_owner_cell` | `ServerPlayerMixin` injects at the head of that private method and answers survival for `ForgeFakePlayers.isMachinePlayer`. Not `setGameMode` afterwards: it broadcasts a tab-list update for a player nobody lists |
| 4 | **No capability for the block-entity-less vanilla blocks** (cauldron, composter). Confirmed. Fabric API's transfer API serves both on Fabric and BuildCraft adds nothing there | 6: `pipe_fluid_fills_cauldron`, `pipe_wood_fluid_drains_cauldron`, `pipe_wood_fluid_keeps_its_fluid`, `gate_reads_cauldron_fluid`, `pipe_item_feeds_composter`, `gate_reads_composter_inventory` | `ForgeVanillaHandlers.find`, behind `ForgeTransfer.query`: the 1.21.1 NeoForge `CauldronWrapper` on Forge's `IFluidHandler` (empty, water, lava; whole buckets) and a forwarding `SidedInvWrapper` over the composter's own container |
| 5 | **A horse's item handler is never set** (Forge patch order, as diagnosed), **and** Forge's stock living-entity wrapper is not NeoForge's fallback: it adds the body slot (7 slots, not 6) and takes any item in any slot. The second showed only once the first was fixed, in the same test | 1: `entity_item_storage_living_fallback` | `ForgeTransfer.findItem(Entity)`: a horse reads `AbstractHorse.inventory` itself (opened in the access transformer); Forge's stock `CombinedInvWrapper` for a living non-player entity is replaced by `ForgeVanillaHandlers.equipment` (hands and humanoid armour over Forge's `EntityEquipmentInvWrapper`, with the `isEquippableInSlot` check) |
| 6 | **Forge calls `onLoad` at registration**, NeoForge at the head of the next block-entity tick. Confirmed | 1: `loader_parity_block_entity_joins_after_block_ticks` | `LevelChunkMixin` wraps the `onLoad` call in `addAndRegisterBlockEntity` and, for a `LevelLifecycleBlockEntity` only, adds the block entity to Forge's own `addFreshBlockEntities` list (what NeoForge's code does). `AbstractBCBlockEntity.onLoad` skips a removed or detached block entity, which NeoForge's tick-head pass does itself and Forge's does not |
| 7 | **The water-like flag** was missing | 1: `oil_bobbing_physics` | Fixed in F3c (`EntityMixin` and the fluid mixins, "Hooks Forge 26.3 lacks") |
| 8 | **The enchantment filter hook** had no route | 1: `machine_enchantments_anvil_takes_only_mining_enchantments` | Fixed in F3c (`AnvilMenuMixin`, `EnchantCommandMixin`) |
| 9 | Intermittent: `mining_well_redstone_pauses_and_resumes`, `mining_well_placed_beside_redstone_starts_paused` failed in about half the full runs before the saved-data mixins | 2 | **Not reproduced**: green in nine consecutive full runs on the final tree. Unconfirmed: the likeliest cause is the earlier runs' own wreckage (null-level exceptions in the level tick), not the tests, which are synchronous and drive the well by hand. If one recurs, run with `-PbcGameTestTrace` |

Also from F5b: `FluidFireTester.lavaIgnites` on Forge asks what Forge's `LavaFluid` asks (`state.ignitedByLava() &&
state.isFlammable(level, pos, face)`) instead of calling BuildCraft's hook, which Forge never calls.

Not run on Forge, 7: the client-class tests (`guide_recipe_lookup_synced_cache`, `guide_modifier_recipe_renders_correct_input_variant`,
`zone_planner_{overlay_in_view,overlay_survey_zoom,cull_above_camera,survey_edge_low_terrain,survey_outline_on_cells}`): Forge's
dedicated server refuses a client class ("Attempted to load class ... for invalid dist DEDICATED_SERVER", measured), so they need a
Forge client game test (F6). No other test had to be left out: none of them needs JEI, REI or Curios, and the FallingTree ones
pass without the mod, as on the other nodes (`-PwithFallingTree` is not wired on Forge).

## The 1.21.1 line (F7): `1.21.1-forge`

Forge 52.1.21 on Minecraft 1.21.1, Java 21, EventBus 6 (`versions/1.21.1-forge/gradle.properties`; player minimum
`[52.1.0,53)`). Server side done: `:1.21.1-forge:nodeVerification` is green (compile, transformed resources, jar
`BuildCraftRedux-<v>+mc1.21.1-forge.jar`, `runGameTest`), `:1.21.1-forge:runServer` boots to Done with no BuildCraft
warning or error. The client is F7b, below. Forge 52 runs Mojang's official names in dev and in production, so there is no
reobfuscation here either, and the access transformer is written in official names (the 1.21.1 horse line is proof: the
compile and `entity_item_storage_living_fallback` both read the field it opens).

**The bus (EventBus 6).** `platform/forge/ForgeBus` is the only file that names a bus; every listener and post in
`platform/forge` and the Forge test arms goes through it, so a file's version arms are about event shapes alone.

- `listen` / `listen(order)` → `MinecraftForge.EVENT_BUS.addListener(priority, false, type, consumer)`: a plain listener
  never sees a cancelled event, as an EventBus 7 one does not.
- `refuse` wraps the predicate in a consumer that calls `setCanceled(true)`; `monitor` is `MONITOR` priority with
  `receiveCanceled = true`, handed `isCanceled()`; `post` answers `EVENT_BUS.post`, which is whether it ended cancelled.
- `listenRemovable` / `refuseRemovable(type[, order], ...)` register a fresh consumer object and return
  `EVENT_BUS.unregister(thatObject)` (EventBus 6 keys a lambda listener by the object).
- `listenMod` and `register(DeferredRegister)` use `ForgeModBus.bus()`, the context's mod `IEventBus` (a `BusGroup` on
  EventBus 7). `listenCreativeTabsLow` exists because `BuildCreativeModeTabContentsEvent` is a mod-bus event on 1.21.1 and a
  game-bus one on 26.x.
- `attachCapabilities(kind, listener)` (1.21.1 only) is `addGenericListener(kind, ..., AttachCapabilitiesEvent.class, ...)`:
  EventBus 6 has one generic `AttachCapabilitiesEvent<T>` told apart by the object's class, where 26.x has a subclass per kind.
- A subclass event reaches its parent's listeners on EventBus 6, so the placement listeners (product and tests) listen on
  `EntityPlaceEvent` alone there; EventBus 7 gives `EntityMultiPlaceEvent` its own bus and needs both.
- Test-side annotations: `@SubscribeEvent` is `net.minecraftforge.eventbus.api.SubscribeEvent` on 1.21.1 and
  `...api.listener.SubscribeEvent` on 26.x; `@Mod.EventBusSubscriber(bus = MOD)` is the same on both.

**Per-line differences that cost an arm** (all `//? if >=1.21.10` inside the Forge files unless named):

| Area | 1.21.1 (Forge 52) | Where |
|---|---|---|
| Events | Classes with getters and public fields (`TickEvent.*.Pre/Post` with `level` / `player`, `getServer()`), the spawn reason is `MobSpawnType`, a dig speed's position is an `Optional`, use results are `Event.Result`; the entity click is `PlayerInteractEvent.EntityInteract` (26.3: `EntityInteractSpecific`) | `ForgeGameHooks`, `ForgeLifecycleHooks`, `ForgeProtection`, `LoaderTestEvents` |
| Burn time | `ForgeHooks.getBurnTime(stack, null)`. `ItemStack#getBurnTime` is the item's own override alone, -1 for every vanilla fuel (found by `engine_stirling_fuel` / `engine_stirling_lava_bucket`) | `ForgeVanillaExtensions.burnTime` |
| Living-entity items | Forge's stock `CombinedInvWrapper` is hands plus HUMANOID and ANIMAL armour (7 slots) on 1.21.1 as on 26.3, so the swap to `ForgeVanillaHandlers.equipment` (hands and humanoid armour) runs on both; the equippability check is 1.21.10+, as NeoForge 1.21.1's wrappers take any item (found by `entity_item_storage_living_fallback`) | `ForgeTransfer.findItem`, `ForgeVanillaHandlers` |
| Fluids | Forge 52's entity physics already asks each `FluidType`, so the water-like flag needs no mixin; `FlowingFluid` methods take a `Level` (`tick`, `getSpread`, `getNewLiquid`); `ForgeFluidPhysics` is whole-file `forge && >=1.21.10` | `ForgeFlowingFluids`, `ForgeBoatFluids` (`vehicle.Boat`) |
| Recipes | Vanilla 1.21.1 syncs every recipe, so `ForgeRecipeSync` is whole-file `forge && >=1.21.10` and `syncRecipeTypes` is 1.21.10+; the components ingredient has no `IIngredientSerializer.simple` (codec and stream codec by hand) and shows the variant stacks themselves | `ForgeGameHooks`, `ForgeCraftingServices` |
| Saved data | Written synchronously inside `DimensionDataStorage.save`, so `awaitSavedDataWrites` is a no-op; no level-aware saved data (a 26.1+ seam) | `ForgeVanillaExtensions` |
| Crafting remainder | `stack.getCraftingRemainingItem()` | `ForgeVanillaExtensions.craftingRemainder` |
| Custom data | `CustomData.contains` is public | `ForgeVanillaExtensions` |
| Fake players | No `setPermanentlyInvulnerable` (refuse every damage source, as NeoForge 1.21.1's `FakePlayer`), `startRiding(Entity, boolean)`, `send(Packet, PacketSendListener)`, `invulnerableTime` is a field, no `recentKineticEnemies`; `displayClientMessage(msg, true)` for the action bar | `ForgeFakePlayers` |
| Chunk tickets | `ForgeChunkManager` still exists, but the vanilla route (`TicketType.create`, `addRegionTicket`, unsaved) is kept so both lines behave alike and the owner probe holds | `ForgeChunkTickets` |
| Horse | `animal.horse.AbstractHorse`; the patch sets its handler in `createInventory`, but the container is still read directly (one path) | `ForgeTransfer`, AT line |
| Loader | `ModList.get()` is an instance; biome builder `getGenerationSettings()`; `IForgeEntity#onRemovedFromWorld` (EntityRobot) | `ForgeLoaderServices`, `ForgeAllBiomesFeatureModifier`, `EntityRobot` |
| Config | Forge's file watcher can deliver a reload after the config closed at shutdown ("Cannot get config value before config is loaded" on a fresh run dir): `onReload` acts only for `spec.isLoaded()` | `ForgeConfigHooks` |
| Registries | `NamespacedWrapper`'s lookups are `getHolder(ResourceLocation)` / `getHolder(ResourceKey)`; `key.location()` | `NamespacedWrapperMixin` |
| Client bus | Registration and model events are mod-bus events: `ForgeBus.listenClient` | `ForgeClientRegistrationSinks` |
| Client models | Baked models (`BakedModel`, `IDynamicBakedModel`, `ItemOverrides`) instead of item-model types and block-state parts; the pipe model splits its quads by the chunk render type asked | `ForgeClientModels`, `ForgePipeBlockStateModel`, `ForgeQuadItemModel`, `ForgeFluidShardModel` |
| Fluid containers | A geometry loader around Forge's `DynamicFluidContainerModel` (its own fluid lookup is empty for BuildCraft's items) instead of the item-model type | `ForgeFluidContainerGeometry`, `DataForge` step 5 |
| Item and block tints | `RegisterColorHandlersEvent.Item` / `.Block` (26.3: vanilla's tint-source map) | `ForgeClientRegistrationSinks` |
| Sprite sources | `SpriteSources.TYPES` | `SpriteSourcesAccessor` |
| World render, outline | `RenderLevelStageEvent` (`AFTER_TRANSLUCENT_BLOCKS`), `LevelRenderer.renderVoxelShape` | `ForgeClientHooks` |
| Fluid fog | `modifyFogRender` sets the shader fog range | `ForgeFluidTypeExtensions` |
| Block particles | `ParticleEngineMixin` (26.3: `ClientLevelMixin`) | `ForgeBlockParticles` |
| Edit-box shadow | `EditBoxMixin` (26.3 has `setTextShadow`) | `ForgeClientVanillaExtensions` |

**Mixins.** Forge 52 ships Mixin 0.8.7 and no MixinExtras. The config lists the same 14 names on both lines; the 1.21.1 dev
run declares it with `--mixin.config` as on 26.3, and the boot's debug log shows 11 applied (the other two target classes a
dedicated server never loads during a boot).

- Empty 1.21.1 arms (an empty `@Mixin` on the same target, each with its reason): `EntityFluidInteractionMixin`,
  `EntityFluidTrackerAccessor`, `EntityMixin`, `FloatGoalMixin`, `SwimMixin` (Forge 52 answers through the fluid type),
  `SavedDataStorageMixin`, `ServerChunkCacheMixin` (no level-aware saved data before 26.1),
  `ServerGamePacketListenerImplMixin` (1.21.1 has `IForgeBlock#getCloneItemStack` with the player).
- Plain-Mixin arms: `AnvilMenuMixin` (`@Redirect` on `canEnchant`; extends `ItemCombinerMenu` for `player`, and the holder
  comes from the enchantment registry's `wrapAsHolder`), `EnchantCommandMixin` (static `@Redirect` that captures the target
  method's arguments, the holder among them), `LevelChunkMixin` (`@Redirect` on `onLoad`), `NamespacedWrapperMixin`
  (`getHolder` targets). `ServerPlayerMixin` is the same on both.

**Data composition.** `processResources` runs the same down-ports as `bc.neoforge` and `bc.fabric` before the Forge
transform: `DataDownportPre263` (loot and advancements), then on 1.21.1 `DataDownport1211` (recipes, advancement
backgrounds, classic `models/item` files), then `DataForge`. `DataForge` step 5 renames the classic model loaders
(`neoforge:fluid_container` → `buildcraftunofficial:fluid_container`, BuildCraft's own geometry loader since F7b, with
NeoForge's mask swapped for Forge's; `neoforge:separate_transforms` → `forge:separate_transforms`, which Forge 52 registers
with the same fields) and the components-ingredient rewrite also reads the down-port's `"type"` key (step 4,
the `items/` rewrite to `buildcraftunofficial:fluid_container`, runs on this line too and is inert: 1.21.1 reads no `items/`
file). Pack
format: `"pack_format": 34`. Forge-only data in `src/main/resources-forge`: `data/c/tags/item/cobblestones/normal.json`
(Forge 52's tags do not fill `c:cobblestones/normal`; without it the structure-pipe recipe was dead), with
`#forge:cobblestone/normal` optional. `processTestResources` down-ports the fixture recipes too.

**Game tests on 1.21.1.** `:1.21.1-forge:runGameTest`: all 948 registered tests pass ("All 948 required tests passed"; 1.21.1
ships no `minecraft:always_pass`), the same 948 as 26.3-forge, in about 33 s of tests. Wiring:

- 1.21.1 has no `TEST_FUNCTION` registry. Forge posts `net.minecraftforge.event.RegisterGameTestsEvent` on the mod buses
  (`ForgeGameTestHooks.registerGametests`, when `forge.enableGameTest` / `forge.gameTestServer` is set, which the
  `gameTestServer` run's `Main` does). `platform/forge/ForgeGameTestHooks` answers it with the shared
  `LegacyGameTestRegistry.registerAll()` (the NeoForge and Fabric 1.21.1 hooks' call), which adds `TestFunction`s against the
  `buildcraftunofficial:empty` arena and reads each manifest's batch and `max_ticks`. `FMLConstructModEvent` registers
  `TestOnlyBlocks` (under `forge.gameTestServer`) and `CapabilityOnlyCart.registerLookup()` (a generic
  `AttachCapabilitiesEvent<Entity>` listener there).
- `-PbcTests=<glob>` becomes `-Dbuildcraft.gameTestFilter` (1.21.1's server has no `--tests`), as on NeoForge 1.21.1.
- The pass check and the assembled one-mod directory are F5's, unchanged.

**F7b, the 1.21.1 client (done, 2026-11-21).** `:1.21.1-forge:runClient` reaches a world; the six `ClientPlatform` holders
have a Forge arm on both lines (`grep -rn "not ported (#209)" src/main` is empty). How the F6 slice was split:

- Every file of `platform/forge/client/**` and `platform/forge/mixin/client/**` is `//? if forge {` with per-version arms
  (the 26.3 arm plain, the 1.21.1 one commented in-tree). Four files are new and whole-file `forge && <1.21.10`:
  `ForgeQuadItemModel`, `ForgeFluidShardModel`, `ForgeFluidContainerGeometry` and the `ForgeEditBoxShadow` duck.
- **Buses.** `ForgeBus.listenClient(type[, order], listener)` is the client registration helper: `listen` on EventBus 7
  (1.21.11 up: each event's own `BUS`), the mod bus on 1.21.1, where the registration and model events (`EntityRenderersEvent`, `ModelEvent.*`,
  `RegisterColorHandlersEvent`, `RegisterParticleProvidersEvent`, `RegisterClientReloadListenersEvent`) are `IModBusEvent`s.
  Game-bus events (`RenderLevelStageEvent`, `RenderHighlightEvent`, `RecipesUpdatedEvent`, ticks, tooltips) go through
  `listen` / `refuse` as on the server side.
- **Fluid client extensions.** Attached on `RegisterClientReloadListenersEvent` on both lines, which fires before the first
  resource reload; at `FMLClientSetupEvent` (F6's choice) the 1.21.1 reload had already read the fluid types and every
  BuildCraft fluid rendered as missing texture. 1.21.1's fog hook is `modifyFogRender` (shader fog start 0, end at the
  type's distance); the `BakeFluidModels` listener stays on the lines with baked fluid models (26.1 up).
- **Models.** `ModelEvent.ModifyBakingResult#getModels` for the pipe block model (`ForgePipeBlockStateModel`, an
  `IDynamicBakedModel` on 1.21.1 splitting cutout and translucent quads by the render type Forge's chunk compiler asks
  for) and the item edits; `ModelEvent.BakingCompleted` for `onModelsBaked`. `fixedQuadItemModel` is `ForgeQuadItemModel`, a
  leaf `IDynamicBakedModel`; `layeredFluidContainerModel` (the fragile fluid shard) is `ForgeFluidShardModel`, which wraps
  the vanilla model and resolves its overrides per stack, drawn with `ForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT`.
- **Fluid containers (buckets, the shard).** Forge 52's `DynamicFluidContainerModel` reads the stack's fluid through
  `FluidUtil.getFluidHandler(ItemStack)`, which is empty for every BuildCraft container on Forge, so its overrides never
  bake a fluid. `ForgeFluidContainerGeometry` (`buildcraftunofficial:fluid_container`, registered in
  `ModelEvent.RegisterGeometryLoaders` and read with Forge's own `DynamicFluidContainerModel.Loader`) wraps the baked model:
  its overrides ask Forge's handler first, then `Platform.transfer().getFluidContained(stack)`, and bake one model per
  fluid (cached). `DataForge` step 5 writes that loader into the down-ported `models/item` files.
- **Hooks.** `onLevelRender` is `RenderLevelStageEvent` at `AFTER_TRANSLUCENT_BLOCKS` with an identity `PoseStack` (the
  event's pose is a `Matrix4f` on 1.21.1, as NeoForge 1.21.1 does); the block outline is `RenderHighlightEvent.Block` drawing
  `LevelRenderer.renderVoxelShape` with BuildCraft's lines type and cancelling; `onRecipesReceived` is
  `RecipesUpdatedEvent` (vanilla 1.21.1 syncs every recipe, so no `ForgeRecipeSync` there).
- **Vanilla extensions.** Ported from the NeoForge 1.21.1 twin with Forge's `ModelData.EMPTY`; `shouldRenderFace` is
  vanilla's `Block.shouldRenderFace(state, level, pos, face, neighbourPos)`.
- **Recipe viewer.** JEI 19.27.0.340 (`jei-1.21.1-forge`, the build the bare 1.21.1 node pins): `jei_version` in the node's
  properties compiles `compat/jei` and puts JEI's Forge jar in the dev runtime (`-Precipe_viewer=none` leaves it out).
  REI has no Forge build for 1.21.1. The smoke runner reads each node's `jei_version` / `rei_version`, so a scenario that
  needs a viewer runs on 1.21.1-forge and stays skipped on the other Forge nodes, which pin none.
- `headAccessoryRenderer` is a no-op on both lines (no Curios Forge build). The config screen is absent on 1.21.1-forge
  only: since the merge with the 1.21.11/26.x lines `ForgeClientConfigScreens` registers `ForgeConfigScreen` from 1.21.11
  up and its 1.21.1 arm is empty (`TODO(#209)`).

**Client mixins on 1.21.1.** The config's `client` list is the same on both lines (`DataForge.dropClientMixins` is gone);
a mixin with nothing to do on a line is an empty `@Mixin` on a class that exists there. The "26.3" column holds from
1.21.11 up since the merge with those lines; the 1.21.11 arms are in "The 1.21.11 line (F8)".

| Mixin | 1.21.1 | 26.3 |
|---|---|---|
| `EditBoxMixin` (new) | NeoForge 1.21.1's `EditBox#setTextShadow`: a `@Redirect` on each `drawString(Font, X, int, int, int)` overload `renderWidget` calls (`FormattedCharSequence`, `Component`, `String`; each covers every call of that overload), drawing with the shadow flag set through `ForgeEditBoxShadow` | empty (26.3 has the setter) |
| `ParticleEngineMixin` (new) | NeoForge's `addDestroyEffects` / `addHitEffects`: `@Inject` at the head (cancellable) of `ParticleEngine#destroy` and `#addBlockHitEffects`, through `ForgeBlockParticles` | empty (`ClientLevelMixin` does it) |
| `SpriteSourcesAccessor` | `SpriteSources.TYPES` (a private `BiMap`) | `ID_MAPPER` |
| `ModelBlockRendererMixin` | `@Redirect` on the one `putBulkData` call in `putQuadData`: the alpha scaled by the quad's baked vertex alpha (BuildCraft sprites only; "Vertex alpha on 1.21.1" below) | F6's table |
| `ClientLevelMixin`, `LevelRendererMixin`, `ItemFeatureRendererMixin`, `ItemModelsAccessor`, `CuboidItemModelWrapperInvoker`, `ScissorStackMixin` | empty (`ItemFeatureRendererMixin`, `ItemModelsAccessor`, `CuboidItemModelWrapperInvoker`, `ScissorStackMixin` target `Minecraft`: their 26.3 targets do not exist) | F6's table |

**Vertex alpha on 1.21.1.** NeoForge 1.21.1 patches `VertexConsumer#putBulkData` to multiply in each vertex's baked
alpha; Forge 52 leaves it as vanilla has it (RGB read, one alpha for the quad), so BuildCraft's painted-pipe mask quads
(alpha 76) drew opaque blue (found by `port-26-3`'s blue-painted quartz pipe). `ModelBlockRendererMixin`'s 1.21.1 arm
redirects the one `putBulkData` call in `putQuadData` and scales its alpha by the quad's baked alpha, for quads on
BuildCraft's own sprites only. The item path (`ItemRenderer`) is left as Forge has it: no BuildCraft item was seen to
need it.

**Quick-play on Forge 1.21.1.** Forge 52's `Minecraft` patch runs `onResourceLoadFinished` (which performs
`--quickPlaySingleplayer`) before `ClientModLoader.completeModLoading()` starts `MinecraftForge.EVENT_BUS`, so a quick-played
world loads with the game bus shut and every post is dropped, Forge's own `AddReloadListenerEvent` included: the integrated
server then crashes at its first loot roll (`Can not retrieve LootModifierManager until resources have loaded once`; a
squid drowning, water breaking a block, a `fill` over pipes). Forge's defect, not BuildCraft's, and any launcher's "join
world on start" meets it. The node declares `quickplay_drops_game_events=true`; `-PbcQuickPlay` warns there, and the smoke
runner boots such a node to the title screen and opens its save through Singleplayer (`Open-SaveFromMenu`).

**What was run (2026-11-21).**

- `:1.21.1-forge:nodeVerification`: all 948 game tests pass (rerun on the final tree). `:26.3-forge:nodeVerification`: all 949.
- `:1.21.1:compileJava`, `:26.3:compileJava`, `:26.3-fabric:compileJava`, `:1.21.1-fabric:compileJava` clean;
  `StonecutterDirectiveGuardTester` 7 of 7.
- `grep -rn "not ported (#209)" src/main`: nothing.
- The dev client's log on every smoke boot: no BuildCraft WARN or ERROR (first-boot "config is not correct. Correcting"
  lines are Forge's config writer); JEI starts (`JEI StartEventObserver ... JEI_STARTED`); all 10 client mixins log
  `Mixing client.*`.
- Smoke, `Invoke-SmokeBatch.ps1 -Node 1.21.1-forge` against `-Node 1.21.1` in the same worktree: 41 PASS, 0 FAILED,
  13 SKIPPED on 1.21.1-forge; NeoForge 1.21.1 had 50 PASS, 2 FAILED, 1 FATAL, 1 SKIPPED. Every scenario that passed on
  1.21.1 passed on 1.21.1-forge with the same step count (`mod-list-banner` 10 against 9: the Forge mod-list row click),
  except the 9 skipped on Forge by design: `crossmod-claims-{flan,flan-stranger,flan-admin-stranger,ftbchunks,opac,
  opac-stranger}`, `crossmod-energy`, `crossmod-storage` (no `-PwithMods` runtime on Forge) and `guideme-guide` (NeoForge
  only). `creative-bucket-tank` passed on Forge and failed on NeoForge 1.21.1 (no empty bucket after a creative pour);
  `goggles-curios` and `rei-parity` fail on NeoForge 1.21.1 and are skipped on Forge.
- The 520 Forge screenshots were ranked by pixel difference against their NeoForge twins and read pair by pair across
  pipes (painted, translucent), fluids in tanks and the world, engines, the heat exchanger, buckets and the fragile shard
  in hand and in GUIs, machine GUIs, facades (shading), plugs (lens), robots, markers, the guide, JEI, tooltips, outlines
  and the guide's shadowless search box. The only rendering difference was the painted pipe, fixed above and re-checked
  (`port-26-3` passes, 289 steps). The rest were world-dependent (different seeds: terrain, biome foliage tint, mob and
  robot positions, a running exchanger's steam).

Not checked: `:1.21.1-forge:runServer` (not run: the node's `run/` has no `eula.txt` and accepting the EULA was not this
slice's call; the game-test server, a dedicated server, boots clean and applies no client mixin), block-hit and destroy
particles by eye (`ParticleEngineMixin` applied; no scenario screenshots them), the item-path vertex alpha.

## The 1.21.11 line (F8): `1.21.11-forge`

Forge 61.2.5 on Minecraft 1.21.11, Java 21, EventBus 7 (`versions/1.21.11-forge/gradle.properties`; player minimum
`[61.1.0,62)`, OPAC `forge-1.21.11-0.33.0` as the compile-only claims jar). It is the 26.3-forge build with version arms:
`bc.forge` needed no change (`DataDownportPre263` runs, `DataDownport1211` does not, the client mixin list is kept, game
tests filter with `--tests`). `ForgeBus`'s split (`>=1.21.10` is EventBus 7) is right for 1.21.11: Forge 61 is EventBus 7
and there is no 1.21.10 Forge node. Forge 61 bundles MixinExtras 0.5.4, so the 26.3 mixins keep their MixinExtras
injectors. Official names at runtime, as on the other two lines. The access transformer needed no new line (the
`animal.equine.AbstractHorse` line already covers 1.21.11).

**pack.mcmeta.** Vanilla 1.21.11 validates the jar's one pack.mcmeta once per pack type (`PackFormat.validate`): read as
data (last pre-minor format 81), a `min_format` of 81 or lower must come with `supported_formats` and `pack_format`; read as
resources (last pre-minor 64), a `min_format` above 64 must not carry `supported_formats`. `75..94` (resource and data
majors) failed the data read ("Couldn't load mod:buildcraftunofficial pack metadata" on the server). `64..94` with
`"supported_formats": [64, 94], "pack_format": 64` passes both. McDevBridge's `1.21.11-forge` node declares `75..94` and
will log the same error (that repo is not this slice's to edit).

The reference for every arm below is Forge 61's patched vanilla (the mavenizer's `official/1.21.11/remapped-javadoc.jar`
sources and `recompiled.jar` classes, under
`~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/1.21.11-61.2.5/`), compared
with javap against Forge 64 (26.1.2), 65 (26.2) and 66 (26.3). Most 1.21.11 arms are therefore `>=26.1` or `>=26.3` splits,
which the 26.1.2 and 26.2 nodes share.

**Server arms.**

| Area | 1.21.11 (Forge 61) | Gate, where |
|---|---|---|
| Crafting | No `ItemStackTemplate`, so the item holder is `getItemHolder()` and the components-ingredient display is a plain `ItemStack`; no `IIngredientSerializer.simple` (Forge 66 only), so the serializer is built by hand from the codec and stream codec (`byHand`, shared by every line before 26.3: Forge 52, 61, 64, 65) | `>=26.1`, `>=26.3`: `ForgeCraftingServices` |
| Biomes | `getGenerationSettings()` | `>=26.3`: `ForgeAllBiomesFeatureModifier` |
| Loader | `ModList.get()` is an instance (static from Forge 64) | `>=26.1`: `ForgeLoaderServices` |
| Fake players | No `setPermanentlyInvulnerable` / `setInvulnerableTime` (Forge 66): `isInvulnerableTo(ServerLevel, DamageSource)` answers true, `invulnerableTime` is a field | `>=26.3`: `ForgeFakePlayers` |
| Events | A dig speed's position is an `Optional` (Forge 61, 64, 65); the entity click is `PlayerInteractEvent.EntityInteract` (Forge 64 moved `Player.interactOn` to `EntityInteractSpecific`; found by `stripes_refused_entity_interaction_ends_dispatch`) | `>=26.3`, `>=26.1`: `ForgeGameHooks`, `LoaderTestEvents` |
| Menus | `displayClientMessage(msg, true)` for the action bar (`sendOverlayMessage` is 26.1+) | `>=26.1`: `ForgeMenus` |
| Recipe sync | `Recipe.CODEC` (`DIRECT_CODEC` is 26.3) | `>=26.3`: `ForgeRecipeSync` |
| Burn time | `ForgeHooks.getBurnTime` is gone; `level.fuelValues().burnDuration(stack)` (Forge's patched `FuelValues`, on 61, 64, 65, where the one-argument call delegates to Forge's `burnDuration(stack, null)`; one arm for all three) | `>=1.21.10 && <26.3`: `ForgeVanillaExtensions.burnTime` |
| Crafting remainder | `IForgeItemStack#getCraftingRemainder()` (no `ItemStackTemplate`) | `>=1.21.10 && <26.1`: `ForgeVanillaExtensions.craftingRemainder` |
| Fluids, saved data | No `EntityFluidInteraction` and no `SavedDataStorage` (26.1+); Forge 61's `Entity`, `FloatGoal` and `Swim` ask the fluid type, as Forge 52 does | `>=26.1`: `ForgeFluidPhysics` (whole file) and the seven mixins below |
| Entity | `IForgeEntity#onRemovedFromWorld` (`onRemovedFromLevel` from Forge 64) | `forge && >=26.1 || neoforge && >=1.21.10`: `EntityRobot` |

**Server mixins, re-proved against Forge 61.** The config's 14 names are unchanged. Applied as on 26.3 (target and point
read in the patched source): `AnvilMenuMixin`, `EnchantCommandMixin`, `LevelChunkMixin`, `MappedRegistryMixin`,
`NamespacedWrapperMixin`, `ServerPlayerMixin`, and `ServerGamePacketListenerImplMixin` (Forge 61 has no
`IForgeBlock#getCloneItemStack` with the player, so the `>=1.21.10` pick-block wrap applies unchanged). Empty 1.21.11 arms
(the reason in each file): `EntityFluidInteractionMixin`, `EntityFluidTrackerAccessor`, `EntityMixin`, `FloatGoalMixin`,
`SwimMixin` (the fluid type answers), `SavedDataStorageMixin` (on `DimensionDataStorage`), `ServerChunkCacheMixin` (no
level-aware saved data before 26.1). A `runServer` boot's debug log shows 12 applied; `ServerGamePacketListenerImplMixin` and
`SwimMixin` target classes a boot with no player never loads, as on 26.3.

**Client arms.** Before the merge with F7b the client files kept a whole-file `forge && >=1.21.10` header; since it they
are `//? if forge {` with the 1.21.1 arms beside the `>=1.21.10` ones, and the 1.21.11 shapes are `>=26.1` / `>=26.3` arms
inside those, or an `elif >=1.21.10` arm between the 26.x and the 1.21.1 one. An inactive nested arm's own comments are `//` lines
(a block comment there would need a third nesting level).

| Seam | 1.21.11 (Forge 61) | Where |
|---|---|---|
| GUI | `GuiGraphics` (26.1 renamed it `GuiGraphicsExtractor`), `gui.render.state.pip`; PiP through `getRenderState().submitPicturesInPictureState`; the scissor stack is `GuiGraphics$ScissorStack` (the duck is kept); a PiP renderer is built from the GUI buffer source the event hands over (`Function<BufferSource, ...>`, the interface's 1.21.x shape) | `ForgeClientVanillaExtensions`, `ScissorStackMixin`, `ForgeClientHooks` (tooltip context), `ForgeClientRegistrationSinks` |
| Block models | `renderSingleBlock` and `particleIcon` through Forge's model-data overloads (the plain ones are deprecated); a part's layer is `IForgeBlockModelPart.layer()`, else the layers its model draws on (`getRenderTypes`, translucent if any is; Forge deprecates `ItemBlockRenderTypes.getChunkRenderType`) | `ForgeClientVanillaExtensions` |
| Pipe model | A quad carries no layer: Forge's `SectionCompiler` asks `getRenderTypes(state, rand, data)` and collects once per layer into that layer's buffer. A pipe with geometry answers cutout and translucent and returns the matching list (both for a null layer); parts are `SimpleModelWrapper(quads, ao, particle, layer, layer)` | `ForgePipeBlockStateModel` |
| Quad colours | No `QuadInstance`: `ForgeClientModels.putColouredQuad` writes a coloured quad as vanilla's `putBulkData` loop does with the colours multiplied in, and answers false (vanilla's call runs) for a quad with none; `setItemQuads` fills `prepareQuadList()`; no `materialInfo` (the interface's is 26.x) | `ForgeClientModels`, the two draw mixins |
| Level overlay | Immediate mode after the translucent terrain (NeoForge `AfterTranslucentBlocks`, Fabric `END_MAIN`): the context carries the main buffer source, which the renderers flush | `ForgeClientHooks`, `LevelRendererMixin` |
| Block outline | `RenderHighlightEvent.Block.Callback` is `(BufferSource, PoseStack, translucentPass, LevelRenderState)` and is called in both outline slots of the main pass; BuildCraft draws in the slot vanilla's would take (any of the model's layers sorted on upload) with `ShapeRenderer.renderShape` and `endLastBatch`, and draws nothing in the other, which keeps vanilla's outline out of both | `ForgeClientHooks` |
| Hit particles | Forge adds `ClientLevel.addBreakingBlockEffect(BlockPos, BlockHitResult)`, the overload the attack calls; the override takes the hit result (the interface's 1.21.x shape) | `ClientLevelMixin`, `ForgeBlockParticles` |
| Registration | `RegisterColorHandlersEvent.Block.register(BlockColor, Block...)` (`blockColor`); render pipelines are `com.mojang.blaze3d.pipeline.RenderPipeline`; no fluid world model (`BakeFluidModels` / `FluidModel` are 26.1+): Forge's fluid renderer reads the client extension textures, which `ForgeClientFluids.stillTexture` returns too | `ForgeClientRegistrationSinks`, `ForgeClientFluids` |
| Fluid containers | No `CuboidItemModelWrapper` and no `Material.Baked`: `ForgeLegacyFluidContainerModel` (whole-file `forge && >=1.21.10 && <26.1`) is the port of Fabric's `FabricLegacyFluidContainerModel`, same type id and fields; the fluid sprite is the type's still texture on the block atlas, the light level the type's. `ForgeFluidContainerModel` is `>=26.1` inside its file | `ForgeLegacyFluidContainerModel`, `ForgeFluidContainerModel` |

**Client mixins, re-proved against Forge 61.** Same eight names in the shared config.

| Mixin | 1.21.11 target and point |
|---|---|
| `LevelRendererMixin` | `ChunkSectionsToRender.renderGroup`, at `RETURN`, acting for `ChunkSectionLayerGroup.TRANSLUCENT` only. The point after the translucent terrain is inside `addMainPass`'s frame-pass lambda, whose synthetic name is the compiler's and need not match between the dev jar and an installed game; `renderGroup(TRANSLUCENT)` has one caller, once a frame, between the translucent and tripwire groups. The pose stack is a fresh one (the main pass's own is `new PoseStack()`), the camera position the main camera's. Forge does not patch the class |
| `ModelBlockRendererMixin` | `@WrapOperation` on the one nine-argument `VertexConsumer.putBulkData` call in `putQuadData`, where the chunk mesh and every lit draw end, and on the one eight-argument call in the private static `renderQuadList` (`renderModel`: block-entity renderers, `renderSingleBlock`). Forge patches neither |
| `ItemFeatureRendererMixin` | `ItemRenderer.renderQuadList` (1.21.11's `ItemFeatureRenderer` hands each submit to `ItemRenderer.renderItem`): `@WrapOperation` on its one `putBulkData` call. Not patched by Forge |
| `ClientLevelMixin` | `addDestroyBlockEffect` head as on 26.3; the hit hook is the head of Forge's `addBreakingBlockEffect(BlockPos, BlockHitResult)` (cancellable), before Forge's own `IClientBlockExtensions.addHitEffects` and the vanilla direction overload (no hit sound in that path on 1.21.11) |
| `ScissorStackMixin` | `net.minecraft.client.gui.GuiGraphics$ScissorStack` (string target), duck over `peek()` |
| `ItemModelsAccessor`, `SpriteSourcesAccessor` | Unchanged: `ItemModels.ID_MAPPER` and `SpriteSources.ID_MAPPER` are private static on 1.21.11 too |
| `CuboidItemModelWrapperInvoker` | Empty 1.21.11 arm on `BlockModelWrapper` (the legacy fluid container model draws its own layers) |

**Deprecations.** `-PbcLint=deprecation,removal` on `:1.21.11-forge:compileJava` leaves `Block.BLOCK_STATE_REGISTRY`
(`FacadeStateManager`, `FacadePhasedState`), `LiquidBlock(FlowingFluid, Properties)` (`BCFluidBlock`) and
`BucketItem(Fluid, Properties)` (`BCEnergyFluids`): Forge deprecates all three on 61 and on 66 alike (javap `Deprecated`
attribute), so they are a Forge-wide gap of the shared code, not a 1.21.11 one.

**Smoke (2026-11-21).** `Invoke-SmokeBatch.ps1 -Node 1.21.11-forge` with McDevBridge `1.4.4+mc1.21.11-forge`: 39 PASS,
14 SKIPPED, 1 FAILED, 0 BuildCraft log errors in any scenario. The failure was `port-26-3` step 234, the bridge's HTTP
call for `spreadplayers 400 400` timing out while the chunks generated (the later oil screenshots show the player landed);
alone it passed 289/289. The 14 skips are the 26.3-forge batch's: scenarios whose loader filter names only NeoForge or
Fabric (FTB Chunks and the Flan admin claims, cross-mod energy and storage, `goggles-curios`, `guideme-guide`), the OPAC
and Flan claims (they need `-PwithMods`), and the JEI/REI scenarios (no recipe viewer on that node's dev run). The screenshots were looked at: pipes in world and in hand, shards, facades, the heat
exchanger, markers and volume boxes, the builder and its GUI, the creative tank with a bucket, goggles worn, the zone
planner map (same as `26.3-forge`'s), the guide. In `port-26-3`'s B12 probe, water replaces flowing oil on 1.21.11
("Test failed"), the pre-26.3 behaviour: only the 26.3 port puts oil in `minecraft:washed_away_by_fluids`. The client
logs carry no BuildCraft WARN or ERROR; McDevBridge's own jar logs `Couldn't load mod:mcdevbridge pack metadata`, its
pack.mcmeta (75..94) missing the 1.21.11 fields this node's `pack_fields` adds for BuildCraft.

## The 26.2 and 26.1.2 lines (F8): `26.2-forge`, `26.1.2-forge`

Forge 65.1.6 on Minecraft 26.2 and Forge 64.1.6 on Minecraft 26.1.2, Java 25, EventBus 7: the same shape as 26.3-forge, so
`ForgeBus`, the entrypoint, the config, network, menus, transfer, loot and crafting seams needed no change. Pins in
`versions/26.2-forge/gradle.properties` and `versions/26.1.2-forge/gradle.properties` (player minimum `[65.1.0,66)` and
`[64.1.0,65)`, `mc_dep_range` the line, `pack_fields` 88-107 and 84-101 as on the NeoForge twins, the Modrinth OPAC pin of
the line). Each builds its own jar (`BuildCraftRedux-<v>+mc26.2-forge.jar`, `+mc26.1.2-forge.jar`). No change was needed in
the access transformer, `DataForge`, `mods.toml` or `pack.mcmeta`: the transformed resources pass the node's guard as they
are. Measured 2026-11-21:

- `:26.2-forge:nodeVerification` and `:26.1.2-forge:nodeVerification` green: "All 949 required tests passed" on each, the
  same 948 BuildCraft tests plus `minecraft:always_pass` as 26.3-forge; the same 7 client-class tests are not registered (see
  "Game tests on Forge (F5)"), nothing else is left out. All 14 server mixins apply in the game-test server (debug.log
  "Mixing ...").
- `:26.2-forge:runServer` and `:26.1.2-forge:runServer` boot to Done (2.1 s, 2.2 s) and stop cleanly with no BuildCraft
  warning or error. A bare server with nobody on it loads only the mixed classes it touches: 10 of 14 mixins on 26.2 and 12 on
  26.1.2 are applied there (`ServerGamePacketListenerImpl` and `Swim` need a player or a mob; the game-test run applies all).
- Client: see "Smoke on 26.2-forge and 26.1.2-forge" below.

**What differs from 26.3, arm by arm** (every arm is keyed on the version alone inside a whole-file `forge` or
`forge && >=1.21.10` body; the else arm serves 26.2 and 26.1.2 unless it says otherwise; where 1.21.11 shares or splits the
arm, the row says so, and the 1.21.11 section above lists that line's own arms):

| File | 26.3 arm | 26.2 / 26.1.2 arm, and why |
|---|---|---|
| `ForgeVanillaExtensions.burnTime` | none (the `VanillaExtensions` default) | `level.fuelValues().burnDuration(stack)`, one `>=1.21.10 && <26.3` arm with 1.21.11: Forge 61/64/65 patch `FuelValues#burnDuration` into the stack-aware answer and have no `ForgeHooks.getBurnTime` (1.21.1 keeps that) |
| `ForgeGameHooks.onBreakSpeed` | `getPosition()` is a `BlockPos` | `getPosition().orElse(null)`: an `Optional` on Forge 64/65, as on 1.21.1 |
| `ForgeAllBiomesFeatureModifier` | `generationSettings()` | `getGenerationSettings()` (the 1.21.1 name) |
| `ForgeFakePlayers` | `setPermanentlyInvulnerable(true)`, `setInvulnerableTime(0)` | an `isInvulnerableTo(ServerLevel, DamageSource)` override answering true, and the public `invulnerableTime = 0` field: neither 26.3 setter exists in 26.1/26.2 vanilla |
| `ForgeCraftingServices` components ingredient serializer | `IIngredientSerializer.simple(CODEC, STREAM_CODEC)` | the `byHand` serializer (`codec()`, `write`, `read`), shared with 1.21.11 and 1.21.1: Forge 52/61/64/65 have no `simple` |
| `ForgeRecipeSync` | `Recipe.DIRECT_CODEC` | `Recipe.CODEC` (26.3 renamed it) |
| `mixin/FloatGoalMixin`, `mixin/SwimMixin` | the water-like float check | empty: 26.1/26.2 vanilla asks `isInWater() && getFluidHeight(WATER) > ...`, which the fluid mixin below already answers, as on 1.21.1 |
| `mixin/EntityFluidInteractionMixin` + `EntityFluidTrackerAccessor` | per-fluid-type trackers with a `fluidType` (26.3) | an `elif >=26.1` arm (1.21.11 and 1.21.1 share the empty else): 26.1/26.2 vanilla tracks per fluid tag (`trackerByFluid`) and Forge adds a per-type map (`trackerByFluidType`) whose `Tracker` has no type. `@WrapOperation` on the one `Fluid.is(TagKey)` call in `getTrackersFor` keeps a fluid out of the WATER tag tracker unless `ForgeFluidPhysics.isWaterLike`; the water-like and eye-in-water answers are vanilla's WATER tracker; the fall-distance modifier walks the per-type trackers through the accessor's `height`. The accessor arm reads `height` and `eyesInside` only |
| `ForgeFluidPhysics` | | `declared(FluidType)`, `isWaterLike(Fluid)`, `fallDistanceModifier(FluidType, Entity)` for that arm |
| `client/ForgeClientModels` | `ItemQuads`, the 26.3 `MaterialInfo` | the 6-argument `MaterialInfo` (sprite, layer, render type, tint, shade, light emission) and `layer.prepareQuadList().addAll(quads)`: 26.1/26.2 have no `ItemQuads` |
| `client/ForgeFluidContainerModel` | the 26.3 `MaterialInfo.of`, two item sheets | `MaterialInfo.of(..., true, 0)`, the 6-argument fluid layer (shade off for a glowing fluid), one item sheet (as Fabric's arms) |
| `client/ForgeBlockParticles` | the hit hook gets no position | `addHitEffects(BlockState, Level, HitResult, ParticleEngine)` (the interface's pre-26.3 shape; fed the `BlockHitResult`), shared with 1.21.11 |
| `mixin/client/ClientLevelMixin` | `@WrapWithCondition` on `addBreakingParticles` in `addBreakingBlockEffects` | `@Inject` at the head (cancellable) of Forge's `addBreakingBlockEffect(BlockPos, BlockHitResult)`: one arm for Forge 61, 64 and 65 |
| `client/ForgeClientRegistrationSinks` | `RenderPipeline` from `client.renderer`; the PiP factory a `Supplier` | `com.mojang.blaze3d.pipeline.RenderPipeline`; on 26.1 (`<26.2`) the PiP factory is a `Function<BufferSource, ...>` fed Forge 64's `event.getBufferSource()` (the same arm serves Forge 61) |
| `client/ForgeClientHooks.onBlockOutline` | `>=26.2`: the collector callback | 26.1: Forge 64's `render(BufferSource, PoseStack, translucent, LevelRenderState)` callback, drawing in the matching pass with `ShapeRenderer.renderShape` and ending the batch; the same callback arm serves Forge 61, only the translucency test before it is per line (`>=26.1` model flag, 1.21.11 sorted layer) |
| `mixin/client/LevelRendererMixin` | `>=26.2`: before `finalizeGizmoCollection()` in `submitFeatures` | 26.1: `submitBlockDestroyAnimation` at TAIL (26.1.2 has no `submitFeatures`; it submits inside the main pass) |
| `mixin/client/ItemFeatureRendererMixin` | `>=26.2`: `prepareMainSubmit` | 26.1: `renderItem` (the same `QuadInstance.setColor(I)V` target) |

One behaviour differs from 26.3-forge: the WATER tag tracker leaves out a fluid that is not water-like, so on these lines
`getFluidHeight(WATER)` is 0 in oil, where 26.3's per-type tracking reports it. Nothing in BuildCraft or vanilla asks for it
in oil except the float and swim checks, which must not see oil as water (`oil_bobbing_physics` passes on both).

**Mixin re-proof.** Every injection point was re-read in each line's patched sources (the mavenizer's
`injected-sources.jar` for `26.2-65.1.6` and `26.1.2-64.1.6`), counting the target calls inside the target method:

| Mixin | 26.2 (Forge 65.1.6) | 26.1.2 (Forge 64.1.6) |
|---|---|---|
| `AnvilMenuMixin` (`canEnchant` in `createResult`) | 1 | 1 |
| `EnchantCommandMixin` (`canEnchant` in `enchant`) | 1 | 1 |
| `EntityFluidInteractionMixin` (`Fluid.is` in `getTrackersFor`) | 1 | 1 |
| `EntityFluidTrackerAccessor` (`height`, `eyesInside`) | present | present |
| `EntityMixin` (`isEyeInFluid`, `checkBelowWorld` in `baseTick`; `isInFluid` in `updateFluidInteraction`) | 1, 1, 2 | 1, 1, 2 |
| `FloatGoalMixin`, `SwimMixin` | no injection (empty arm) | no injection (empty arm) |
| `LevelChunkMixin` (`onLoad` in `addAndRegisterBlockEntity`) | 1 | 1 |
| `MappedRegistryMixin` (`register(key, value, RegistrationInfo)`) | present | present |
| `NamespacedWrapperMixin` | applied (game-test run) | applied (game-test run) |
| `SavedDataStorageMixin` (`computeIfAbsent`, `get`, `collectDirtyTagsToSave`) | present | present |
| `ServerChunkCacheMixin` (constructor) | present | present |
| `ServerGamePacketListenerImplMixin` (`getCloneItemStack` in `handlePickItemFromBlock`) | 1 | 1 |
| `ServerPlayerMixin` (`openMenu`, `calculateGameModeForNewPlayer`) | present | present |
| `client.ClientLevelMixin` (`addDestroyBlockEffect`; `addBreakingBlockEffect(BlockPos, BlockHitResult)`) | present, present | present, present |
| `client.ItemFeatureRendererMixin` (`QuadInstance.setColor` in `prepareMainSubmit` / `renderItem`) | 1 | 1 |
| `client.LevelRendererMixin` (`finalizeGizmoCollection` in `submitFeatures` / TAIL of `submitBlockDestroyAnimation`) | 1 | TAIL |
| `client.ModelBlockRendererMixin` (`BlockQuadOutput.put` in `putQuadWithTint`) | 1 | 1 |
| `client.ScissorStackMixin`, `client.CuboidItemModelWrapperInvoker`, `client.ItemModelsAccessor`, `client.SpriteSourcesAccessor` | same members as 26.3 | same members as 26.3 |

Runtime application: the game-test run (server mixins) and the smoke clients' debug.log (client mixins, below).

### Config screen (every Forge node from 1.21.11 up)

Forge generates no screen from a spec, so `platform/forge/client/ForgeConfigScreen` is hand-built and the mod list's Config
button opens it (`ForgeClientConfigScreens` registers a `ConfigScreenHandler.ConfigScreenFactory` extension point on the
mod's own container; the same call on Forge 61, 64, 65 and 66; not `MinecraftForge.registerConfigScreen`, which goes through
the for-removal `ModLoadingContext.get()`). It walks the specs `ForgeConfigRegistrar` registered, in order: a section is a
button opening its own screen, a value a label and an editor by type (on/off, a cycle over the accepted enum constants, a
text box for numbers and strings, comma-separated text for a list of strings, read-only for anything else). A typed value
reaches the config only when the spec's validator accepts it (red text until then); changes are set at once and written when
the screen goes away. Labels and tooltips use NeoForge's lang keys (`<modid>.configuration.<key>[.tooltip]`), so one lang
file serves both, falling back to the spec comment. Fabric's answer (Forge Config API Port's copy of NeoForge's
`ConfigurationScreen`) was not reused: on Forge it would be a new runtime dependency for players. `1.21.1-forge` registers
no screen: since the merge with F7b its arm of `ForgeClientConfigScreens` is empty (TODO(#209)). Since the merge with the 1.21.11 line
(2026-11-21) `1.21.11-forge` compiles the same file unchanged (its `>=26.2` / else arm takes `setScreen`, as 26.1 does);
the screen is not exercised in a 1.21.11-forge client yet (`forge-config-screen` not run there).

### Smoke on 26.2-forge and 26.1.2-forge (Server1, 2026-11-21)

`Invoke-SmokeBatch.ps1 -Node 26.2-forge` and `-Node 26.1.2-forge`, the default list (56 scenarios; 16 skipped on Forge for a
recipe viewer, dev mods or a loader filter, as on 26.3-forge). 26.1.2-forge: all 40 that ran passed (106 min). 26.2-forge: 37
of 40 passed (102 min); the three failures are McDevBridge requests that timed out (`TimeoutException`) on a busy game
thread, not a BuildCraft error (0 BuildCraft log errors in every scenario): `port-26-3` step 234 (`spreadplayers 400 400`,
which generates new chunks), `release-2026-2-1-f` step 144 (a `/screenclick`), `release-2026-2-1` step 34 (a `/state`).
The NeoForge twins ran the same 40 (the Forge skips and `forge-config-screen` left out): `26.2` passed all 40, `26.1.2` 38,
failing `port-26-3` step 234 with the same `spreadplayers` timeout and `zone-planner-zoom` step 4 (a `setblock` under the
player refused in that world), so the `spreadplayers` timeout is not Forge's; the two `release-2026-2-1*` timeouts were seen on
26.2-forge only, once. Same-named shots read side by side: `line8` render rig and Tank GUI (26.1.2-forge against 26.1.2),
`heat-exchanger-look` running-ne and `facade-shading` c-mixed (26.2-forge against 26.2) show the same models, fluids, shading
and ambient occlusion; only terrain and sky differ (other seeds). The rest of the shots were not read.

`forge-config-screen` passed on both: the mod list's Config button opens the screen (sections General, World Generation,
Display), Display lists its values with on/off and enum editors, a click flipped "Searing Fluids Emit Steam" and the client
config on disk then read `searingFluidSteam = false`, which every later scenario's client booted with (the scenario now flips
it back and re-opens the section before it ends; that version is not run yet). On `26.3-forge` the same scenario (and
`mod-list-banner`) failed at its first step on Server1: the runner could not bring the 26.3 (SDL3) window to the front to open
the pause menu ("could not focus the game window"), so the screen is not verified in a 26.3-forge client.

## Common-code loader forks (F3b)

Every loader-keyed directive outside `platform/` was walked (`//? if neoforge`, `fabric`, `forge`, bare elses). Evidence
per hook is in [forge-seam-map.md](forge-seam-map.md) (loader-hooks); each row was re-read against Forge 26.3 66.1.8 and
the call site in its `patches/`.

- **Shared with NeoForge (`neoforge || forge`):** `AbstractBCBlockEntity` onLoad / onChunkUnloaded; `BlockPipeHolder`
  getSoundType, getExplosionResistance, addRunningEffects, addLandingEffects; `BCFluidBlock` getFlammability,
  getFireSpreadSpeed; `ItemWrench_Neptune` doesSneakBypassUse, onItemUseFirst; `EntityRobot` onRemovedFromLevel (Forge 1.21.1
  names it `onRemovedFromWorld`: the 1.21.1 node needs an arm for it). `BlockPipeHolder.canConnectRedstone` needed no arm:
  Forge's own `RedStoneWireBlock` patch calls `IForgeBlock#canConnectRedstone` on every line (the seam map said it did
  not), so the 26.3 `shouldRedstoneWireConnectTo` override is not what the wire's connection test asks there.
- **Forge arm of its own (`elif forge`):** `TilePipeHolder` handleUpdateTag / onDataPacket (the 2-argument and 3-argument
  forms with the registry lookup, 26.x) and getModelData (Forge's `ModelData`); `BCFluidBlock.onCaughtFire` (the 5-argument
  boolean, 26.x; NeoForge 26.3 alone takes the item); `EntityRobot` class header and spawn data (`IEntityAdditionalSpawnData`
  with a plain `FriendlyByteBuf`, wrapped into a `RegistryFriendlyByteBuf` through `Platform.network().registryBuf`; the
  platform layer cannot add the interface from outside, since Forge tests `this instanceof` it); `TileFiller`
  getRenderBoundingBox (Forge culls by block entity, so RenderFiller's renderer-level hook stays NeoForge-only);
  `EntityRobot.isPushedByFluid(FluidType)` (Forge's own `FluidType`; its no-argument vanilla twin is `@Deprecated` there,
  which is why the Fabric arm is not shared).
- **Missing on Forge but served on Fabric through a `platform/hook` interface the class implements on every loader:** the
  four hooks F3b listed (menu opening data, the anvil gate, lava ignition, the 26.x pick block) are served in F3c; see the
  table under "Mixins on Forge".

- **Unchanged and fine:** `BCEvent` / `ICancellableBCEvent` keep Forge on the plain-class arm (see Open); the NeoForge-1.21.1
  `AbstractBCSyncedBlockEntity.onDataPacket` twin is not needed on Forge 1.21.1 (its default applies an empty update tag).
  `BCFluidTank.movesNothing` gives Forge the classic answer 0 that NeoForge 1.21.1 and Fabric give.

## Open

- The seven client-class game tests are not registered on Forge (below): they need a Forge client game test (F6).
- `StaticTablesLevelUnloadTester`'s chunk-owner probe runs on Forge only as far as `ForgeChunkTickets.claim` / `release` go; the
  rest of that test is shared.
- The gitignored release tool rejects any node whose loader is not neoforge or fabric, so it must learn `forge`
  (or skip it) before this branch merges to main, or the next NeoForge/Fabric release run fails at enumeration.

- 1.21.1 mixin declaration: the dev run's `--mixin.config` is measured (F7); the jar manifest's `MixinConfigs` on an installed
  Forge 52 is the same unproven item as on 26.3 (below).
- Production-install proof of the two F4 runtime declarations: the jar manifest `MixinConfigs` and the shipped
  `META-INF/accesstransformer.cfg` are read by FML in an installed Forge, but only the dev-run equivalents were booted
  (a Forge server install is a download). Boot the collected jar in `mods/` of a real install before anything ships.
- Settled in F1: the 3-arg `registerConfig(type, spec, fileName)` exists; Forge keeps one config per type per mod.
  `ForgeConfigSpec` lacks only the 4-arg `defineListAllowEmpty` (nine calls in `BCEnergyConfig` and `BCSiliconConfig`):
  they go through `lib.misc.ConfigListUtil.stringList`, whose one directive is the only loader fork.
- Forge's packet splitter is commented out upstream on 26.3: payloads are held to vanilla's caps (1 MiB clientbound,
  32 KiB serverbound). Measure the snapshot payloads in F5.
- Forge 26.3-66.1.8 cannot network-encode a shaped recipe with an empty slot (`Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC`,
  see the recipe sync row). BuildCraft works around it; any other mod syncing `RecipeHolder.STREAM_CODEC` hits it. Worth a
  report upstream (MinecraftForge) when the user wants one filed.
- Unexercised F3c behaviour (applied and compiled, needs a client or a pipe in a game test): the pick-block wrap, the recipe
  payload's client receiver (F6 wires it through `ForgeClientHooks#onRecipesReceived`; the dev-client join negotiated the
  `recipe_sync` channel, but no guide panel was opened), the spectator menu's client half (the factory reading the data).
- `BCEvent` posts to the NeoForge bus only. Whether Forge addons get a bus post is an API decision; until asked, the
  Forge arm is the plain class Fabric uses.
