# MinecraftForge port (#209)

Standing design for the third loader. "Forge" here is MinecraftForge (`net.minecraftforge`), never NeoForge. The
platform layer rules in [platform-layer.md](platform-layer.md) apply unchanged; this file records only what differs.
Per-API evidence lives in [forge-seam-map.md](forge-seam-map.md) (272 rows, each with a `.forge-ref/` path and line).

## Scope

- Nodes are `<line>-forge`, declared `version("<line>-forge", "<line>")`, `loader=forge` in the node's properties.
- Order: **26.3-forge** (active line; EventBus 7, the shape 26.2 / 26.1.2 / 1.21.11 share), then **1.21.1-forge**
  (the only Forge line with an audience; EventBus 6, its own event fork).
- 1.21.11, 26.2, 26.1.2: decided on measured cost once the first two exist (#210).
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
  `CustomData.tag`, and Forge's own `SidedInvWrapper` fields.
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
| Menus | `IForgeMenuType.create` + `IForgeServerPlayer.openMenu(provider, Consumer<buf>)`; vanilla `openMenu(provider)` is unhooked | `openFor` writes the data itself; the spectator path needs the Fabric `ServerPlayerMixin` approach |
| Config | `ForgeConfigSpec` (near-identical builder; no 4-arg `defineListAllowEmpty`; types CLIENT / COMMON / SERVER only) | Stonecutter replacement `ModConfigSpec` -> `ForgeConfigSpec` on forge nodes, like the `Identifier` rule; LOCAL maps to COMMON as on Fabric |
| Patched overrides | Of the 37 hook rows in the seam map (33 `@LoaderHook` uses plus the `platform/hook` and `VanillaExtensions` routes), 17 compile unchanged and 14 differ in signature; missing: clone-item-stack, block shearing on 26.2+, per-entity tick, `writeClientSideData` | Same-shape ones become `//? if neoforge || forge`; the rest route through `platform/hook` interfaces and Forge mixins |
| Client | No menu-screen event, no item tint source / sprite source / render pipeline registration, no level-render stage event after 1.21.4, no per-vertex quad colour, no PiP submit | Vanilla registries reached by access transformer where possible, otherwise the Fabric client mixins |
| Data | `forge:` namespace; `fluid_container` is a model-file loader, not an item-definition type; no components ingredient; recipe conditions are read at the top of any data-driven-registry datum on 26.3 (the seam map's "not found" was wrong); no `recipe_priorities` | `DataForge.kt` rewrite beside `DataFabric.kt` (decisions below); BC-registered ingredient serializer and flag condition |
| Tests | No FML-JUnit. 26.x: `TEST_FUNCTION` is a vanilla registry, test instances are datapack JSON, entry is `net.minecraft.gametest.Main`. 1.21.1: annotation / `Method` based | Fabric's arrangement: no unit tests, the compile is the deprecation guard, the shared game tests are the gate |

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
| `neoforge:fluid_container` item definitions (31: 30 buckets + `fragile_fluid_container`) | `items/<n>.json` becomes `{"model": {"type": "minecraft:model", "model": "<ns>:item/<n>"}}` and a new `models/item/<n>.json` carries `"loader": "forge:fluid_container"`, `"parent": "forge:item/default"`, the textures and the same four fields | Forge's `DynamicFluidContainerModel.Loader` is a geometry loader (`ClientForgeMod`, `ModelEvent.RegisterGeometryLoaders`) read from a model file, not an item-definition type. `neoforge:item/mask/bucket_fluid_drip` becomes `forge:item/mask/bucket_fluid_drip` (Forge's own mask in its universal jar), so no BC texture is needed. Forge 26.3 ships no item tint for the fluid layer (tint index 1), so the buckets render un-tinted until F6 supplies one |

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

## Mixins on Forge (F4 probe)

Settled on 26.3-66.1.8 by a one-mixin boot (`platform/forge/mixin/MixinConfigProbeMixin`, log line "BuildCraft's Forge mixin
config is applied: MinecraftServer#runServer entered" in `run/logs/debug.log`):

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
  compile-only dependency the mavenizer adds). Mixin 0.8.7's `CompatibilityLevel` stops at `JAVA_21` (no later constant in the
  jar), so the config says `JAVA_21`, not the node's Java 25 (Fabric's config expands the node's level; its Mixin is newer).
  It logs at debug level that JAVA_21 exceeds the JAVA_13 it can verify, and that a Java 25 mixin class (version 69) exceeds
  the class version it knows; the mixin applied regardless.
- **No refmap, no annotation processor.** Forge 26.3 runs official names, the probe targets `runServer` by its Mojang name and
  applied with neither (`bc.forge` adds no processor). A Forge mixin names vanilla members as the shared source does.
- **Layout.** `buildcraft.platform.forge.mixin` (package-private abstract classes, body in `//? if forge {`, like the Fabric
  ones), listed in `src/main/resources-forge/buildcraftunofficial.forge.mixins.json`, which only the Forge jar carries.

## Rules added for the third loader

- A third constant, `forge`. `constants.match(loader, "neoforge", "fabric", "forge")`.
- **No bare loader `else`.** With three loaders an `else` after `if neoforge` silently means "Fabric and Forge". Every
  loader chain names each loader it serves (`if neoforge || forge`, `elif fabric`). The five main-code forks that had a bare
  else are re-keyed (F3b) and `StonecutterDirectiveGuardTester` fails a new one in `src/main`; about thirty test-code forks
  still have one, re-keyed in the slice that ports each seam, and the guard does not hold test code to the rule until then.
- Stonecutter 0.9.8 reads `&&` and `||` left to right at one rank (measured on a scratch probe: `neoforge || forge &&
  >=1.21.10` is off on NeoForge 1.21.1). A mixed condition repeats the version term after each loader:
  `neoforge && >=1.21.10 || forge && >=1.21.10`. The guard and the census evaluate left to right to match.
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
| F4 | **Done.** Resources: `mods.toml`, `pack.mcmeta`, access transformer check, `DataForge.kt`, jar, dedicated server boots to Done | `:26.3-forge:runServer` |
| F5 | Game tests on Forge; `LoaderTestEvents` forge arms; `nodeVerification` | `:26.3-forge` game tests green |
| F6 | Client seams, models, renderers; dev client | smoke runner on `26.3-forge` |
| F7 | `1.21.1-forge` (EventBus 6 arm, 1.21.1 client shapes, `ForgeChunkManager` or vanilla tickets) | same gates on that node |
| F8 | Remaining lines per #210; smoke, cross-mod (JEI on 1.21.1, OPAC), release tooling (`release/`, gitignored), skills and AGENTS.md | `fullTestSuite` |

Loader lists that must grow together (F0 does the first three): `build.gradle.kts` loader check and `constants.match`;
`StonecutterDirectiveGuardTester`; `scripts/platform-census.py`; `scripts/smoke/Invoke-InClientSmoke.ps1`
(`Get-NodeLoader`, `Get-NodeMcVersion`, `-Loader`, node list, the `neoforge-client.toml` edit);
`scripts/smoke/multiplayer/mp.init.gradle`; `release/modules/BcRelease.psm1` and `release/config/release.json`.

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
- **Still owed, missing on Forge but served on Fabric through a `platform/hook` interface the class implements on every loader.**
  No mixin is written yet; each needs one in `platform/forge/mixin`:

  | Hook (interface) | Fabric route | Forge call site to patch |
  |---|---|---|
  | `IBCMenuProvider.writeClientSideData` (`MenuOpeningData`) | `ServerPlayerMixin` (`openMenu` head) | vanilla `ServerPlayer#openMenu(MenuProvider)`, reached by the spectator branch of `ServerPlayerGameMode#useItemOn` (Forge adds only an event beside it) |
  | `ItemEnchantableMachine.supportsEnchantment` (`EnchantmentFilterItem`) | `EnchantmentEvents.ALLOW_ENCHANTING` (Fabric API, no mixin) | vanilla `Enchantment#canEnchant(ItemStack)` (anvil, `/enchant`), unpatched; Forge's `canApplyAtEnchantingTable` is the table's alone and the item must stay out of it |
  | `BCFluidBlock.ignitedByLava` (`FireReactiveBlock.lavaIgnites`) | `LavaFluidMixin` on the 2-argument `LavaFluid#isFlammable` | Forge's `LavaFluid` patch tests the 3-argument `isFlammable(level, pos, face)`, whose test is the state's own `ignitedByLava()` flag and `IForgeBlock#isFlammable` (shared with fire spread): re-target the mixin there |
  | `BlockPipeHolder.getCloneItemStack` (`PickStackBlock.pickedStack`), 26.x | `PlayerPickItemEvents.BLOCK` | server pick handler `ServerGamePacketListenerImpl#handlePickItemFromBlock`, which reaches `BlockStateBase#getCloneItemStack(BlockPos, LevelReader, boolean, Player)`; Forge patches neither. Forge 1.21.1 has the hook, and its arm is written |

- **Unchanged and fine:** `BCEvent` / `ICancellableBCEvent` keep Forge on the plain-class arm (see Open); the NeoForge-1.21.1
  `AbstractBCSyncedBlockEntity.onDataPacket` twin is not needed on Forge 1.21.1 (its default applies an empty update tag).
  `BCFluidTank.movesNothing` gives Forge the classic answer 0 that NeoForge 1.21.1 and Fabric give.

## Open

- The gitignored release tool rejects any node whose loader is not neoforge or fabric, so it must learn `forge`
  (or skip it) before this branch merges to main, or the next NeoForge/Fabric release run fails at enumeration.

- 1.21.1 mixin declaration: the 26.3 answer is below under "Mixins"; check the same two paths on 1.21.1 (Forge 52's
  Mixin and FML differ) when F7 starts.
- Access transformer name form on 1.21.1 (official expected since Forge 50; the Forge docs still say SRG).
- Production-install proof of the two F4 runtime declarations: the jar manifest `MixinConfigs` and the shipped
  `META-INF/accesstransformer.cfg` are read by FML in an installed Forge, but only the dev-run equivalents were booted
  (a Forge server install is a download). Boot the collected jar in `mods/` of a real install before anything ships.
- Fluid-container item tint: Forge 26.3's `forge:fluid_container` model bakes the fluid layer with tint index 1 and registers no
  item tint for it, so the 31 converted items render un-tinted until F6 supplies a tint source (`items/*.json` `tints`).
- Settled in F1: the 3-arg `registerConfig(type, spec, fileName)` exists; Forge keeps one config per type per mod.
  `ForgeConfigSpec` lacks only the 4-arg `defineListAllowEmpty` (nine calls in `BCEnergyConfig` and `BCSiliconConfig`):
  they go through `lib.misc.ConfigListUtil.stringList`, whose one directive is the only loader fork.
- Legacy-id aliases reach Forge's own lookups but not vanilla's codecs (`ForgeRegistryServices`, Alias gap): F3 mixin
  or a BuildCraft-side fixer, before any Forge jar ships.
- Forge's packet splitter is commented out upstream on 26.3: payloads are held to vanilla's caps (1 MiB clientbound,
  32 KiB serverbound). Measure the snapshot payloads in F5.
- `syncRecipeTypes` has no Forge switch: the guide's recipe panels need a BuildCraft payload on remote servers (F3).
- `BCEvent` posts to the NeoForge bus only. Whether Forge addons get a bus post is an API decision; until asked, the
  Forge arm is the plain class Fabric uses.
- McDevBridge has no `1.21.11-forge` node; needed only if that line is taken.
