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
- The viewer plugin packages (`compat/jei`, `compat/rei`) compile on a Forge node only if it has a `jei_version` /
  `rei_version` property (none has a viewer for 26.3), so `26.3-forge` builds the rest of `src/main/java` without them.
- Forge needs `META-INF/mods.toml` (not `neoforge.mods.toml`) and a `pack.mcmeta`, or FML covers the title screen with
  a load warning. Pack fields differ: `pack_format` on 1.21.1, `min_format`/`max_format` on 26.x.
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
| Data | `forge:` namespace; `fluid_container` loader exists; no components ingredient; recipe conditions wired on 1.21.1, not found on 26.3; no `recipe_priorities` | `DataForge.kt` rewrite beside `DataFabric.kt`; BC-registered ingredient and flag condition as on Fabric |
| Tests | No FML-JUnit. 26.x: `TEST_FUNCTION` is a vanilla registry, test instances are datapack JSON, entry is `net.minecraft.gametest.Main`. 1.21.1: annotation / `Method` based | Fabric's arrangement: no unit tests, the compile is the deprecation guard, the shared game tests are the gate |

## Rules added for the third loader

- A third constant, `forge`. `constants.match(loader, "neoforge", "fabric", "forge")`.
- **No bare loader `else`.** With three loaders an `else` after `if neoforge` silently means "Fabric and Forge". Every
  loader chain names each loader it serves (`if neoforge || forge`, `elif fabric`). Five main-code forks and some thirty test-code
  forks have a bare else today; each is re-keyed in the slice that ports its seam, and the directive guard fails on a
  new one once the port compiles all of main.
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
| F4 | Resources: `mods.toml`, `pack.mcmeta`, access transformer check, `DataForge.kt`, jar, dedicated server boots to Done | `:26.3-forge:runServer` |
| F5 | Game tests on Forge; `LoaderTestEvents` forge arms; `nodeVerification` | `:26.3-forge` game tests green |
| F6 | Client seams, models, renderers; dev client | smoke runner on `26.3-forge` |
| F7 | `1.21.1-forge` (EventBus 6 arm, 1.21.1 client shapes, `ForgeChunkManager` or vanilla tickets) | same gates on that node |
| F8 | Remaining lines per #210; smoke, cross-mod (JEI on 1.21.1, OPAC), release tooling (`release/`, gitignored), skills and AGENTS.md | `fullTestSuite` |

Loader lists that must grow together (F0 does the first three): `build.gradle.kts` loader check and `constants.match`;
`StonecutterDirectiveGuardTester`; `scripts/platform-census.py`; `scripts/smoke/Invoke-InClientSmoke.ps1`
(`Get-NodeLoader`, `Get-NodeMcVersion`, `-Loader`, node list, the `neoforge-client.toml` edit);
`scripts/smoke/multiplayer/mp.init.gradle`; `release/modules/BcRelease.psm1` and `release/config/release.json`.

## Open

- The gitignored release tool rejects any node whose loader is not neoforge or fabric, so it must learn `forge`
  (or skip it) before this branch merges to main, or the next NeoForge/Fabric release run fails at enumeration.

- How a Forge mod declares a mixin config on 26.x and 1.21.1 (manifest `MixinConfigs` attribute is the expected
  answer; not in the reference sources). Settle in F3 with a one-mixin boot.
- Access transformer name form on 1.21.1 (official expected since Forge 50; the Forge docs still say SRG).
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
