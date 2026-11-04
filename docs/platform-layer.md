# Platform layer (#98)

Standing design for moving every NeoForge use behind `buildcraft.platform` (Phase 1, NeoForge-only), then adding a `26.3-fabric` node (Phase 2). Synthesised 2026-11-03 from three competing proposals. The base is the "incremental-risk" design: in-tree packages, a compile-time binder and Stonecutter loader constants. Grafted onto it: the Fabric-shaped registration replay from "fabric-first", and the golden-save-first ordering, the no-generic-event-bus rule and the git-mv-ready package layout from "build-structure". Read this before writing or briefing any platform slice.

## Goal and finish line

- **Goal.** Common code (everything outside `buildcraft/platform/<loader>/**`) compiles against vanilla, BC and `buildcraft.platform` types only. Loader code lives in one package per loader. Phase 1 changes no behaviour and adds no release target.
- **Finish line, Phase 1.** Two things must hold, not one:
  1. The census (see Progress metric) reports zero live `net.neoforged` references outside `buildcraft/platform/neoforge/**` on the 26.3 view, apart from the explicit allowlist.
  2. Every patched-vanilla override (a NeoForge-injected method with no import, such as `getFlammability` or `writeClientSideData`) carries a `@LoaderHook` tag.
- **Which arms count.** The 1.21.1-only arms (`//?} else {` of `>=1.21.10`) do not count toward the finish line. That node is NeoForge-only forever, because Phase 3 excludes 1.21.x. Migrate them into the adapter whenever a slice already owns the seam (it removes directives for free), but never open a slice just for them.
- **The issue's criterion is replaced on purpose.** "No file outside `buildcraft.platform` imports `net.neoforged`" has three faults:
  - It is blind to patched overrides.
  - It can be gamed with fully qualified names (204 live before S1).
  - It counts types that may legitimately stay, such as `ModConfigSpec`.
- **Real gate into Phase 2.** `:26.3-fabric:compileJava` passes.

## Layout and build wiring

Verified facts this section rests on:
- Stonecutter 0.9.8 has user constants. The jar ships `build/config/ConstantContainer` (`put(String, boolean)`, `match(String value, String... candidates)`) and `ConstantDsl.constants(Action)`, and the Stitcher parser has a `ConstantExpressionContext` rule. So `//? if neoforge {` is valid directive grammar. Two scout reports said otherwise; they were wrong.
- Stonecutter registers processing for every Gradle source set, at `src/<sourceSetName>` (the string `src/\u0001` in `StonecutterBuildImplKt.createProcessingTasks`, iterated over `getSourceSets()` in `afterEvaluate`). A separate source set named `neoforge` would therefore be version-processed. An extra `srcDir` added to `main` would not. That kills the "extra srcDir of main" plan from the build-structure proposal.

**Phase 1 layout (in-tree; no new source set).**
- `src/main/java/buildcraft/platform/`: loader-neutral contracts, one interface per category (`platform.network`, `platform.menu`, `platform.event`, `platform.registry`, `platform.transfer`, `platform.fluid`, `platform.config`), the `Platform` binder, and BC-owned value types that only the seam needs.
  - **Contract rule:** these files may reference only the JDK, vanilla, other `buildcraft.platform` types, `buildcraft.api.fluids` (since S14d: `BCFluidStack` is vanilla-only and the Fabric node compiles that package, so the fluid storage contracts carry it) and `buildcraft.api.capability` (since S14e: the `BCCapability` token, which the API's MJ and pipe surfaces name, so it cannot live in the platform). Never a loader type, and never any other BC class. That is what lets a Fabric node compile the contracts alone.
- `src/main/java/buildcraft/platform/client/`: client-only contracts plus the `ClientPlatform` binder. Reach them only from client init, so a dedicated server never loads them.
- `src/main/java/buildcraft/platform/neoforge/**` and `.../neoforge/client/**`: the NeoForge implementation. Each file keeps its `package` line live, and its whole body sits in `//? if neoforge {`. The precedent is `energy/compat/viewer/StirlingFuelSync.java`, which wraps its whole body in a `>=26.3` arm.
- `src/main/java/buildcraft/platform/fabric/**`: Phase 2, wrapped in `//? if fabric {`. On a NeoForge node it is an empty compilation unit.
- **Classes that are loader code by nature move whole into `platform/neoforge`.** Their logic is extracted to common code first. Examples: `RfEnabledCondition`, `AddOilBiomeModifier`, the `FluidType`/`BaseFlowingFluid` subclasses, `PipeBlockStateModel`, the `IClientBlockExtensions` implementations, and later the `@Mod` entrypoint. Do not wrap these in BC abstractions.
- **Tests:** NeoForge-specific tests (FML-JUnit pinning, `CapabilityHooks` behaviour, the override tags) go to `src/test/java/buildcraft/platform/neoforge/`. The rest stay where they are.

**Build wiring.**
- Root `gradle.properties` gets `loader=neoforge`. A Fabric node's `versions/<node>/gradle.properties` sets `loader=fabric`. The loader is never parsed out of the node name.
- `build.gradle.kts` reads `loader` and sets `stonecutter { constants.match(loader, "neoforge", "fabric") }`. Slice 1 must confirm with javap or a dry run that `match` sets exactly the matching key true and the others false; if not, use two `put` calls.
- **Node names:** the six NeoForge nodes stay unsuffixed. Fabric nodes are `<mc>-fabric`, declared as `version("26.3-fabric", "26.3")` (0.9.8 deprecates the short `vers()` at error level, so it does not compile). Renaming the existing nodes would churn every `:26.3:` task path, the skills, the hooks, the smoke runner and the release orchestrator, for nothing.
- **Phase 2 build shape (landed with the spike, #100):** everything NeoForge-specific (the `neoForge {}` block, its dependencies, mods.toml expansion, tests, game tests, the jar and `buildAndCollect`) lives in the buildSrc convention plugin `buildSrc/src/main/kotlin/bc.neoforge.gradle.kts`, its Loom twin is `bc.fabric.gradle.kts`, and `build.gradle.kts` keeps only the shared Stonecutter, toolchain and javac setup before `apply(plugin = "bc.$loader")`. ModDevGradle and Loom are `implementation` dependencies of `buildSrc/build.gradle.kts`, which is where their versions are pinned. Stonecutter flags reach the convention plugins as the `bcNode` extension (`buildcraft.gradle.BcNode`). Each convention plugin registers `nodeVerification`, the node's share of the root `fullTestSuite`. Never two plugin DSLs inline behind conditionals. Loom on 26.x does not remap, so shared source compiles as-is.
- **Phase 2 option, decided by the spike rather than now:** `git mv` `platform/neoforge` into a `neoforge` source set (`src/neoforge/java`, same package names, so no code edits). That would make "common never names an implementation" compiler-enforced. The cost is MDG `mods { sourceSet(neoforge) }`, test classpaths, the `buildcraft.test.mainSourceDirs` system property (`tasks.test` in `bc.neoforge.gradle.kts`), and a binder that is injected from the entrypoint, because main could no longer name the implementation. Until then the census enforces the rule.

**IDE.** The active node stays `26.3` (NeoForge). NeoForge implementation files are live; Fabric implementation files show as comments. To edit Fabric code, switch the active node to `26.3-fabric`.

**Rules for directives.**
- In common code, loader constants appear only on patched-override arms, and only in Phase 2 (see Patched-vanilla overrides).
- Everywhere else, loader differences live in loader packages, and directives in common code stay about MC version.
- Never use negation (`!fabric`); write the positive constant.
- Never nest a loader arm inside an MC arm in common code. Nesting MC arms inside a loader-gated implementation file is fine.

## Binding mechanism

**Compile-time binder.** `buildcraft.platform.Platform` has one static accessor per service, for example `Platform.loader()`. Each accessor reads a `static final` field in its own lazy holder class, so touching `loader()` never loads the network implementation.
- The only loader directive in the binder is inside each holder: the `neoforge` arm constructs the NeoForge implementation, and the `fabric` arm is commented on NeoForge nodes.
- `ClientPlatform` is the client-only twin.

**Rejected alternatives.**
- **ServiceLoader** (fabric-first): it needs a `META-INF/services` file per interface. It adds a failure mode inside FML's module layer and under FML-JUnit class-init ordering (`robotics/entity/UnreachableEntityCache.java` documents exactly such a static-init chain). It buys nothing when each jar ships one loader.
- **Same-name class per loader with no interface** (build-structure): nothing checks the Fabric copy against the NeoForge contract until the Fabric node compiles.

**Cost.** One `getstatic` of a constant static final, plus a monomorphic `invokeinterface`. The JIT inlines both, so the steady-state cost is zero. The real cost is adapter allocation at transfer boundaries:
- Implementations must cache foreign-handler adapters (`BlockCapabilityCache` on NeoForge, `BlockApiCache` on Fabric, or identity-keyed).
- They must never wrap a handler on every tick.

**Implementation rule.** Implementations read loader state per call and never cache loader statics in their own static initialisers.

**Registration timing (graft from fabric-first).** Seams are shaped like Fabric: plain register calls made during BC init, with no mod bus in sight.
- NeoForge implementations queue those calls and replay them inside the matching event (`RegisterCapabilitiesEvent`, `RegisterMenuScreensEvent`, `EntityRenderersEvent.RegisterRenderers` and so on).
- On Fabric the same calls run immediately. That keeps the Fabric side to one-liners and keeps the NeoForge replay ordering identical to today's registration order.

## Seam per category

Seam shapes, in order of preference:
1. Move the body of an existing BC helper into the implementation and leave its call sites alone.
2. Use a register-call sink (replayed on NeoForge).
3. Use typed hooks with vanilla-typed functional interfaces.
4. Use a BC-owned value type, only where no shared shape exists.

Never build a generic BC event bus. It is the Architectury trap the issue rejects.

**`api/` rule.** `buildcraft/api/**` never imports `buildcraft.platform`. BC-owned types that the API exposes live in `buildcraft.api.*`. No addon exists, so API signatures may change in Phase 1.

### Loader services
- **Shape.** `LoaderServices`: `isPhysicalClient()`, `isModLoaded(id)`, `modIds()`, `modVersion(id)` (an `Optional`), `modSourcePaths(id)` (`List<Path>`), `configDir()`, `gameDir()`, and `currentServer()` (nullable).
- **Typing.** BC-owned. Fabric maps to `FabricLoader` and `getOrigin().getPaths()`, and captures `currentServer` at `SERVER_STARTING` and clears it at `SERVER_STOPPED`.
- **Format risk.** None.
- **Directive interplay.** Done in S16: the 1.21.1 `getDist()` to `.dist` regex is gone from `build.gradle.kts`; `NeoForgeLoaderServices.isPhysicalClient` carries the `>=1.21.10` arm, and `api/core/NbtApiUtil.java` asks the source lib installs (`setRegistryOpsSource`) instead of FML.

### Config
- **Shape.** `ConfigRegistrar.register(BCConfigType, spec, fileName)`, where `BCConfigType` is a BC enum (COMMON, CLIENT, LOCAL), plus `ConfigHooks.onReload`.
- **Typing.** The 26.3 `LOCAL` versus `COMMON` directive (`core/BCCore.java:116-124`) moves into the implementation. `ModConfigSpec` itself stays NeoForge-typed in the 9 config classes and is allowlisted, pending the user's choice of Forge Config API Port or an in-house builder.
- **Format risk.** The toml file names and keys must not change. Diff the generated tomls before and after.
- **Unverified:** whether Forge Config API Port supports `LOCAL` on 26.3. That gates the open question below.

### Events and lifecycle
- **Shape.**
  - `GameHooks` has one typed method per event kind used today (about 35). Each takes a vanilla-typed functional interface; cancellable hooks return a boolean meaning "cancel".
  - `HookOrder` (EARLY, DEFAULT, LATE) appears only where BC depends on order today: `ContainedFluidDetonation` and `LocalBlockUpdateNotifier` at LOWEST, silicon `LoggingIn` at HIGH, and the `receiveCancelled` pair in `MachineRefusals`.
  - `LifecycleHooks` covers common setup with an `enqueueWork` equivalent, load complete, server about-to-start and stopped, and level save.
- **`PlatformProtection`.** `canBreak`, `canPlace`, `canAttack`, and the right-click and use-item simulations. It absorbs `BreakEventCompat`, `PlaceEventProbe`, the `AttackEntityEvent` posts (`RobotProtection`, `StripesHandlerMinecartDestroy`), `CommonHooks.onRightClickBlock`/`onItemRightClick` (`BlockUtil.java:404,448`) and `EventHooks.doPlayerHarvestCheck`/`onBlockPlace`. `useItemOn` is a machine's click with the placement capture a player's gets (NeoForge's patched `ItemStack.useOn`). On Fabric every check is also asked of Common Protection API (`eu.pb4:common-protection-api`, MIT, bundled jar-in-jar), the API Fabric claim mods answer through, about the fake player's owner; a refusal from it or from the Fabric API callback refuses. Fabric has no place event: `canPlace`/`canPlaceOver` ask the providers and `mayInteract`, `useItemOn` records the blocks the use sets (`LevelMixin`) and puts them back when a provider refuses one, and `GameHooks.onBlockPlace` listeners see these machine placements only. A claim mod guarding placements through its own mixins alone is not asked.
- **Fabric: mixin-fed hooks.** Where Fabric API has no event, a mixin in `platform.fabric.mixin` calls a `FabricGameHooks.fire...` method where NeoForge posts its event, with the same arguments: the chunk sent to a player (`PlayerChunkSender.sendChunk`, after the packet), the spawn finalize (`Mob.finalizeSpawn`'s head), the death drops (`LivingEntity.dropAllDeathLoot` collects what `spawnAtLocation`, `LivingEntity.drop`, `EntityEquipment.dropAll` and `Inventory.dropAll` drop, NeoForge's `captureDrops` sites, shows the list to the listeners, then puts it in the level), the neighbour notify (`ServerLevel.updateNeighborsAt`/`updateNeighborsAtExceptFromFacing` and `DiodeBlock.updateNeighborsInFront`), the explosion (`ServerExplosion.hurtEntities`, once the entity list is gathered: both lists are the ones the blast goes on to use), each player's own tick (`Player.tick`'s end), a fake player's entity interaction (`Player.interactOn`'s head, after the spectator branch, as NeoForge: Fabric API asks only a real player's click, in its packet handler; `fireFakePlayerEntityInteract` asks Common Protection API's `canInteractEntity`, then `UseEntityCallback`, and a refusal is what `interactOn` answers) and the client level's unload (`Minecraft.setLevel`, `disconnect`, `clearClientLevel`, client mixin). The game tests post the same `fire...` methods through `LoaderTestEvents`, and see game events through the game-test mod's `ServerLevelGameEventMixin` (NeoForge's `VanillaGameEvent` point). `HookOrder` EARLY/LATE are BuildCraft phases ordered before and after Fabric's default phase (`FabricGameHooks.register`, which the game tests' claims use too). A `receiveCancelled` `onEntityInteract` listener is told how a fake player's click ended: one before the refusing callback saw it allowed, the others are told it was refused, and a LATE one is told only once the callback has ended, so it sees every listener's answer whatever their registration order. Gaps: `syncRecipeTypes` (Fabric syncs recipes per serializer); a refused real-player entity click never reaches a `receiveCancelled` listener after the refusing callback (Fabric API ends the event in its packet handler; nothing in BuildCraft asks about real players); `onBlockPlace` sees machine placements only (a player's placement still reaches the neighbour-notify listeners); the spawn finalize is seen only through `Mob`'s own `finalizeSpawn` (an override that never calls it, or a spawner that skips the finalize, posts nothing).
- **Typing.** BC-owned hooks over vanilla types; listeners are wired in per-subsystem classes in the implementation.
- **`@EventBusSubscriber` classes.** The six of them become explicit hook registrations. `FakePlayerUtilSubscriptionTester` and `LevelUnloadSubscriptionTest` reflect over `@SubscribeEvent`, so they are rewritten to test registration or behaviour.
- **Entrypoint (late slice).** `@Mod` moves to `platform/neoforge/BCNeoForgeEntrypoint`, which captures the mod bus. The 51 `init(IEventBus)` signatures become `init()`.
  - **As built (S10).** The entrypoint sets `NeoForgeModBus` (the bus and container every NeoForge implementation used to find through `ModLoadingContext.getActiveContainer()`), subscribes the one `RegisterCapabilitiesEvent` listener, then runs `BCCore.init()` and, on a physical client, `BCCoreClient.init()`. The client init used to run in the middle of `BCCore`'s constructor; nothing after that point listens to an event it listens to, so no event's listener order changed. Capability registration is a NeoForge-typed stopgap until the transfer slice: `lib.misc.CapabilityListeners` collects the subsystems' listeners in init order and the entrypoint's listener runs them. The model events left in common code became `ClientRegistrationSinks.onModelsBaking`, `editBakedModels` (vanilla `ModelBakery.BakingResult`, the model map on 1.21.1) and `onModelsBaked(HookOrder, ...)`, each added straight to the bus at its call so every listener keeps its place. Fabric: `BCFabricEntrypoint` and `BCFabricClientEntrypoint` run the same inits (compiled since Phase 2a); `editBakedModels` cannot run there (Fabric has per-model after-bake modifiers only).
- **Format risk.** None. Order risk: record the observed order before changing any priority. Note that the comment at `transport/BCTransport.java:82` contradicts the LOW/LOWEST pair it sits next to.
- **Directive interplay.** The render-stage and other event-class cliffs move into the implementation with their directives.
- **BC's own events (S16).** `api/events/*` and `EventBuildCraftReload` are not posted on any loader bus. Each event class holds a `LISTENERS` constant, a `buildcraft.api.events.BCEventListeners` (one typed list per event, run in registration order; a cancelled `ICancellableBCEvent` skips the later listeners). It is a list, not a bus: no dispatch by type, no priorities.

### Networking
- **Shape.** `BCNetwork`:
  - Registration: `clientbound`, `serverbound` and `bidirectional`, each taking `(Type, StreamCodec, PacketHandler)` with an optional version string.
  - Sending: `sendToServer`, `sendToPlayer`, `sendToTrackingChunk(ServerLevel, ChunkPos, p)` and `sendToDimension`.
  - `registryBuf(ByteBuf, RegistryAccess)` absorbs `ConnectionType.NEOFORGE`.
  - The BC-owned `PacketContext` offers `player()`, `runOnMain(Runnable)` (NeoForge `enqueueWork`, Fabric `server.execute`) and `reply(payload)`.
- **Typing.** Payload records, codecs and `CustomPacketPayload` are vanilla and stay common.
- **Format risk.** None, provided the ids, codecs and `versioned("2")` on MessageMarker (`BCCore.java:323`) are preserved exactly. Three handlers skip `enqueueWork` today (`MessageMarker`, `MessageDebugRequest`, `MessageDebugResponse`), and the seam must keep that difference.
- **Directive interplay.** The `ClientPacketDistributor` directive (5 sites) and the 3-arg versus 4-arg `playBidirectional` directive (`BCCore.java:339-352`) collapse into the implementation.

### Menus
- **Shape.** `BCMenus.create(BufferMenuFactory<T>)` takes `(id, Inventory, RegistryFriendlyByteBuf)` and covers the 26 `IMenuTypeExtension.create` calls. `BCMenus.open(ServerPlayer, MenuProvider, Consumer<RegistryFriendlyByteBuf>)` covers the 14 `openMenu` calls.
- **Keep the raw writer, not typed opening data.** That keeps the bytes identical. On Fabric the writer fills a `byte[]` sent through `ExtendedMenuProvider` with `ByteBufCodecs.BYTE_ARRAY`.
- **Typing.** `IBCMenuProvider` becomes BC-owned. The `writeClientSideData` override stays as a tagged hook, because the spectator path (`ServerPlayerGameMode`, `openMenu(provider)` with no writer) relies on it. The five anonymous two-argument providers (list, gate, diamond, Emzuli and wood-diamond pipes) move onto `IBCMenuProvider` in the same slice.
- **Format risk.** Low.

### Registries and data
- **Shape.**
  - `BCRegistrar<T>`: `create(ResourceKey<Registry<T>>, modid)`, `register(name, Supplier)`, and block and item helpers that absorb `RegistrationUtilBC` (its eager versus supplier cliff) plus the 42 direct `registerSimple*` bypasses.
  - One helper for the `>=26.3` feature-registry split, which is copied three times today.
  - `BCHolder<T>` implements `Supplier<T>` and offers `id()`, `key()` and `holder()`. `BCBlockHolder` and `BCItemHolder` also implement `ItemLike`.
- **Typing.** On NeoForge, `BCHolder` wraps `DeferredHolder`. The 205 holder uses are a scripted type swap.
- **As built (S9a-f).** `BCHolder<R, T extends R>` keeps `DeferredHolder`'s two type parameters, because `key()` and `holder()` need the registry type; the swap is then one-to-one (`BCRegistrar.Blocks`/`Items`/`DataComponents` mirror the `DeferredRegister` nested types). The helpers are built in the contract on the single `register(name, Function<Identifier, ?>)` primitive, keeping 1.21.1's eager properties. `subscribe()` replaces `register(modBus)` at the same call site, so NeoForge's per-registry order is unchanged (on Fabric it is a no-op). `BCFeatureRegistrar.register(name, Feature::new)` fits both sides of the 26.3 split. S9i moved `RfEnabledCondition` and `AddOilBiomeModifier` whole into `platform/neoforge` as the generic `NeoForgeFlagCondition` and `NeoForgeAllBiomesFeatureModifier` behind `Platform.data()` (`DataServices`): an implementation names no BC gameplay class, so the test (`BCTransportConfig.rfPipeRecipesEnabled`) and the step and feature stay in common code. Still on `DeferredRegister`: the NeoForge-keyed fluid types (fluid slice).
- **Format risk.** None, since ids do not change. Ordering risk exists on Fabric only: registration is eager there, so the init order (`BCCore.java:94-113`) and `LegacyAliases` timing matter.
- **Creative tabs.** Vanilla builders stay. The two `BuildCreativeModeTabContentsEvent` listeners become a BC-owned ordered contribution list read by the owning tab's `displayItems`. Pin the current order with the existing `RoboticsCreativeTabTester` and a dump before changing it.
- **Attachments.** `BCAttachment<T>` (`get`/`set`, `copyOnDeath`). The payloads stay on vanilla `ValueInput`/`ValueOutput` (NBT on 1.21.1). Format risk is high: the NeoForge adapter must write today's bytes, so golden fixtures come first. Cross-loader world portability is explicitly out of scope.
  - **As built (S9h).** `BCAttachmentRegistrar.create(ns).register(name, factory, copyOnDeath)` returns `BCAttachment<T>` with `get(Entity)`/`set(Entity, T)`; payloads implement `BCAttachmentData`, which carries NeoForge's own method names (`serialize`/`deserialize`, `serializeNBT`/`deserializeNBT` on 1.21.1), and the NeoForge serializer is the one `AttachmentType.serializable` builds. `AttachmentGoldenTest` (written first) pins the bytes, the round trip and copy-on-death.
- **Data JSON.** `neoforge:conditions`, `neoforge:components`, `neoforge:ingredient_type`, biome modifiers and `neoforge:fluid_container` item models stay canonical NeoForge. Phase 2 adds a buildSrc Fabric data transform, following the precedent of `DataDownport1211`.
  - **As built (Phase 2).** `buildcraft.gradle.DataFabric` runs as a `doLast` of the Fabric node's `processResources`. Conditions become `fabric:load_conditions` (`mod_loaded` → `all_mods_loaded`, `always`/`never` → `true`/`false`, `not`/`and`/`or`, `tag_empty` → `not` of `tags_populated`, `registered` → `registry_contains`, `feature_flags_enabled` → `features_enabled`); BC's own types (`buildcraftunofficial:rf_enabled`) pass through, since `DataServices.registerCondition` registers them on Fabric too. Non-strict `neoforge:components` ingredients become `fabric:components` (`items` → `base`); a strict one fails the build, since Fabric has no exhaustive mode. `data/neoforge/**` and `neoforge/biome_modifier/**` are dropped against a known-file list: `add_oil` is `registerAllBiomesFeature`, the two `add_features` modifiers are `FabricDataServices.addTaggedBiomeFeatures`, and the build fails if a dropped modifier changes or names a feature that file does not add. `fluid_container` item models become `buildcraftunofficial:fluid_container`, the Fabric twin of NeoForge's model type with the same codec (`platform.fabric.client.FabricFluidContainerModel`: the base as an extruded layer, the fluid's still sprite cut to the mask's opaque pixels and tinted through `ClientPlatform.fluids()`, an optional cover; the fluid is the stack's `FluidStorage.ITEM` content, else the JSON's), and NeoForge's bucket mask becomes the Fabric node's own `buildcraftunofficial:item/mask/bucket_fluid` (`src/main/resources-fabric`); any other `neoforge:` texture fails the build. A final check fails on any `"neoforge:` left in the output. Gaps on Fabric: the mask and cover layers have no side faces and a glowing fluid has no unlit render type; no recipe priorities, so the shared 8-glass tank grid is not settled in IronTanks' favour.

### Transfer and capabilities (a sub-epic; give it its own issue)
- **Shape.**
  - Tokens and lookup: BC typed `BCCapability<T>` tokens replace the `Object` tokens in `CapUtil` and the pipe API. `CAP_ITEMS` (S14c) and `CAP_FLUIDS` (S14d) are typed. The `==` identity checks in `PipeFlowFluids`, `PipeFlowItems` and `PipeFlowRedstoneFlux` must keep working. `TransferAccess.find{Fluid,Item,Energy}(level, pos, side)` returns cached BC views, and a `CapabilityRegistrar` sink is replayed into `RegisterCapabilitiesEvent`.
  - Transactions: `BCTransaction` (`openRoot`, `openNested`, `commit`, AutoCloseable) and `BCSnapshotJournal<S>`. These map onto NeoForge `Transaction`/`SnapshotJournal`, Fabric `Transaction`/`SnapshotParticipant`, and the 1.21.1 simulate flag.
  - Storage: BC storages implement `BCFluidStorage`, `BCItemStorage` and `BCEnergyStorage`, with `int` amounts.
- **Typing.** BC-owned. The implementation adapts both ways.
- **Format risk is high.**
  - On 1.21.10 and later, `BCFluidTank` inherits `serialize` (the `"stacks"` key) from `StacksResourceHandler`, and `TileTank.removeComponentsFromTag` depends on that key.
  - BC must take over `serialize` byte-for-byte, behind golden fixtures, before it decouples from the parent. The 1.21.1 `fluid<i>`/`amount<i>` form stays.
- **API drift inside the `>=1.21.10` arm.**
  - `ItemAccess.exchange` takes a nullable transaction only on 26.3.
  - `SnapshotJournal.isInTransaction` and `ResourceHandlerUtil.moveStacking` exist only on 26.3.
  - `ResourceHandlerUtil.insertStacking(..., null)` is called at `TileChute.java:210` and `InventoryUtil.java:147`, and its null-transaction behaviour is unread. Characterise it before wrapping it.
- **Directive interplay.** This is the main payoff: about 285 `>=1.21.10` arms leave common code.
- **Energy.** On Fabric, energy is Team Reborn Energy, optional and behind the existing RF config gate.
  - **As built (S14e).** `BCCapability` moved to `buildcraft.api.capability` (the API may not name the platform, and the MJ tokens and pipe API must name the token type); it is interned by id and carries the id and type. The storage tokens are `platform.transfer.StorageTokens` (`ITEM`, `FLUID`, `ENERGY`; `CapUtil.CAP_*` alias them, `CAP_ENERGY` included); `MjAPI.CAP_CONNECTOR`/`CAP_RECEIVER`/`CAP_REDSTONE_RECEIVER` are `BCCapability`s of the old ids, which the impl turns into a sided NeoForge `BlockCapability` (`createSided` interns by id, so an addon creating the same capability shares it) or a Fabric `BlockApiLookup<_, Direction>`, handing the objects out unadapted (`CapabilityRegistrar.block`/`blockDirect`, `TransferAccess.find`). Energy storages are `BCEnergyStorage` (`int` transfers, `long` amount/capacity, `canInsert`/`canExtract` hints for the 1.21.1 classic flags); `BCFeStorage` (the `SimpleEnergyHandler` semantics), `MjBatteryEnergyHandler`, `MjReceiverEnergyHandler` and the RF pipe sections implement it on every node, journalled, so the 1.21.1 simulate arms are gone. `NeoForgeEnergyStorages` adapts both ways (`EnergyHandler` / 1.21.1 `IEnergyStorage`, classic views replaying at root commit as for items). `MjToRfAutoConvertor` moved from the API to `lib.mj` (it wraps a `BCEnergyStorage`). `lib.misc.CapabilityListeners` is now `platform.neoforge.CapabilityListeners`: every registration, the fragile fluid shard's item capability included (`CapabilityRegistrar.itemFluidsDrainOnlyConsumable`, its body moved verbatim into `NeoForgeFluidContainers`), goes through the registrar in the old order. Fabric: Team Reborn Energy 5.0.0 compile-only (built against transfer-api 7.0.0; its `EnergyStorage`/`SIDED` signatures resolve against 8.x), used only when `team_reborn_energy` is loaded (`FabricEnergyStorages`); the fragile shard is a `FluidStorage.ITEM` storage over its `FabricFluidContent` (`FabricDrainOnlyFluidItem`: drain-only, used up once empty, whole millibuckets only), pinned on both loaders by the `fragile_shard_drain_only_container` game test.
  - **As built (S14f).** `buildcraft/api/**` names no loader and no platform type. The robot and pipe API's fluid surface is BC-owned in the old BuildCraft style: `IFluidHandlerAdv` is a plain interface over `BCFluidStack` with `simulate` flags (`getTanks`, `getFluidInTank`, `getTankCapacity`, `fill`, `drain(resource)`, `drain(max)`, filtered `drain`), and `DockingStation.getFluidInput/Output` return it. `lib.transfer.FluidHandlerAdvs` adapts it both ways (the contract may not name `api.core`, so the adapter is common code over the contracts): a storage seen as a handler runs each call in `BCTransaction.openInCurrent()` (nested in the thread's open transaction, else a root; NeoForge `getCurrentOpenedTransaction`, Fabric `getCurrentUnsafe`, 1.21.1 `LegacyTransactions.openInCurrent`), committed unless simulating; a foreign handler seen as a storage simulates and replays at root commit. The robot entity and the station hand out views of their `BCFluidStorage`s, which the fluid AIs unwrap back to the storages, so `robotics.RobotFluids` and `TransferServices.to/fromLoaderFluidHandler`/`toLoaderItemHandler` are gone. The pipe API's tokens are typed: `PipeFlow`/`PipeBehaviour.getCapability`, `PipePluggable.getCapability`/`getInternalCapability` and `IPipeHolder.getCapabilityFromPipe` take a `BCCapability<T>` and return `T`, and `PipeApi.CAP_PIPE_HOLDER`/`CAP_PIPE`/`CAP_PLUG`/`CAP_INJECTABLE` (null `Object`s before, which matched only by `null == null`) are real pipe-internal tokens; the `==` checks are unchanged. The census's `api-transfer` allowlist entry is deleted.

### Fluids (value type)
- **Shape.** `buildcraft.api.fluids.BCFluidStack` mirrors NeoForge `FluidStack`'s method names (`EMPTY`, `getFluid`, `getAmount`, `grow`/`shrink`, `copy`, `copyWithAmount`, `isSameFluid`, `isSameFluidSameComponents`, `getComponents`, `CODEC`, `OPTIONAL_CODEC`, `STREAM_CODEC`, `getHoverName`). The 477 uses then migrate as an import swap.
- **Typing.** BC-owned. Conversion happens only in `platform/neoforge`.
  - Built (S13a): `NeoForgeFluids.toNeo`/`fromNeo` and Fabric's `FabricFluids` (`FluidVariant`, x81 droplets, floored back). `getHoverName` goes through a provider that `BCLib` installs from `Platform.fluids()` (`FluidServices`), since the api may not name the platform. The Fabric node also compiles `buildcraft/api/fluids/**` to keep the type loader-neutral.
  - Swapped (S13b): every BC signature, field and local now holds `BCFluidStack` (api, lib, factory, energy, transport, builders, robotics, core, client and compat code, and the tests). NeoForge `FluidStack` stays only where a NeoForge signature demands it: the 1.21.1 `IFluidHandler` implementations, `BCFluidTank`'s inherited `onContentsChanged`, `FluidType.canFillFluidType` in `BCCore`, and `FluidUtil` calls. Those sites and every `FluidResource`/`toStack` boundary convert through `lib.fluid.NeoFluidStacks`, the common twin of `NeoForgeFluids` (common code may not name `platform.neoforge`). S14 moves them into `platform/neoforge` and deletes it.
  - Done (S14d): `NeoFluidStacks` is gone. Fluid storages are `BCFluidStorage` (`platform.transfer`, `int` mB, `BCFluidStack` resources) on every node; `BCFluidTank` implements it itself (the `FluidStacksResourceHandler` semantics, journalled per slot) and keeps both save formats. `NeoForgeFluidStorages` adapts both ways (`ResourceHandler<FluidResource>` / 1.21.1 `IFluidHandler`), `FabricFluidStorages` over `Storage<FluidVariant>`. Fluid container items (the `ItemAccess` / classic `FluidUtil` swaps) are whole operations on `TransferServices`, the client sprite/tint lookup is `ClientPlatform.fluids()`, and JEI takes fluids through its platform fluid helper. Left NeoForge-typed: the fragile fluid container's `SimpleFluidContent` component and its item capability (BCCore), the `FluidType` subclasses in `BCEnergyFluids`, and the robot API's fluid surface (`IFluidHandlerAdv`, `DockingStation` getters), which crosses through `TransferServices.to/fromLoaderFluidHandler` until S14f.
- **Format risk is high.** `CODEC` must emit NeoForge's exact shape (`id`, `amount`, `components`). It is used in the `SimpleFluidContent` component (`BCCore.java:63-65`), `RequiredExtractorTank` snapshots, and the distiller, heat-exchanger and robot NBT. A codec-parity test is written first.
- **Properties.** `FluidType` reads (density, viscosity, temperature, lighter-than-air, bucket, sounds) go through a BC `FluidProperties` record. The NeoForge implementation builds the `FluidType` from it, and `BCEnergyFluids`' subclasses move whole.
  - Built (fluid-properties slice): BC fluids register through `platform.registry.BCFluidRegistrar` (`BCFluidRegistrar.create(ns)`, from `Platform.fluids().createRegistrar`). It takes a `FluidProperties` record (the type properties plus the flow parameters; `motionScale` is a `DoubleSupplier`, so the sticky-oil config is read on every use) and a `platform.FluidBehaviour`. The behaviour is the common half of the old subclasses: their tick, spread and replace rules (gas detonation, dense oils sinking, spreading over water, sources refusing water) live in `BCEnergyFluids.OilBehaviour` and reach vanilla's protected flow steps through `platform.FluidFlow`, which every registered fluid implements. `NeoForgeFlowingFluids` (moved from `BCEnergyFluids`) builds the `FluidType` and the `BaseFlowingFluid` pair and calls the hooks. Ids and registration order are unchanged: the type under the fluid's name, then the source, then `_flowing`.
  - Fabric: `FabricFlowingFluids` is a vanilla `FlowingFluid` pair matching `BaseFlowingFluid`, plus a `FluidVariantAttributeHandler` (temperature, viscosity, gas flag, bucket sounds). Fabric has no fluid type, and every BC fluid is in `minecraft:water` (the spread rules need it), so vanilla's entity physics treated them all as water. `FabricFluidPhysics` and its mixins (`EntityFluidInteractionMixin`, `EntityMixin`, `LivingEntityMixin`, `FloatGoalMixin`) answer at NeoForge's patch points from the declared properties: in water and eyes in water (`baseTick`, `updateFluidInteraction`) mean a water-like fluid, drowning (`baseTick`'s breathe test) follows `canDrown`, each fluid pushes with its own current and `motionScale` where `canPushEntity`, a fluid that is not water-like halves the fall distance each tick (NeoForge's default modifier), and the fluid's `entityInside` extinguishes where `canExtinguish`. A mob's `FloatGoal` floats only where `canSwim` (`FloatGoalMixin`), and a living entity in a fluid that is not water-like (crude oil, residue) wades through it by the common `platform.ThickFluidMotion` on both loaders: NeoForge's `travelInFluid` has moved a non-vanilla type that implements no `FluidType.move` neither as water nor as lava since 26.1.2 (issue #117: it held an entity in crude oil in place), so the NeoForge type overrides `move` on >=26.1.2 (1.21.x keep NeoForge's own water/lava fallback), and `LivingEntityMixin` takes the same step plus NeoForge's `jumpInFluid` lift. Pinned by the four `FluidPhysicsTest` game tests on both loaders.
  - Reads of any fluid are `FluidServices` accessors (`viscosity`, `isLighterThanAir`, `isSameKind`, `description`) rather than a `FluidProperties` per call, since another mod's fluid has only those to give. Fabric answers BC fluids from their declared properties.
- **Units.** Integer mB internally. The Fabric adapter multiplies by 81, floors offers, and extracts in whole mB. That is an accepted Fabric-only gap.
- **Client.** `IClientFluidTypeExtensions` and `RegisterFluidModelsEvent` sit behind a client fluid sink, and `FluidSprites` delegates to it.

### Client render
- **Shape.**
  - `ClientRegistrationSinks` for menu screens, entity renderers and BERs, particles, GUI layers, the config screen, tint sources, render pipelines, sprite sources, reload listeners and PiP renderers. All replayed on NeoForge.
  - `ClientHooks` for tick, login and logout, recipes received, tooltip pre and block outline.
  - One `onLevelRender(Consumer<BCLevelRenderContext>)`. The context carries the pose stack, camera, partial tick and either the submit collector or the buffer source.
- **Typing.** BC-owned sinks over vanilla types.
- **Format risk.** None.
- **Directive interplay.**
  - The level-hook directive triple, copied into 8 files, collapses into one implementation. That also removes the double `SubmitCustomGeometryEvent` subscription in `BCBuildersClient`.
  - Model work comes last. `TilePipeHolder` exposes a BC `PipeRenderData` snapshot, which the NeoForge side wraps in one `ModelProperty`. That fixes the common tile importing the client-package `ModelData` on 1.21.1.
  - The dynamic models and the 1.21.1 `IDynamicBakedModel` classes move whole; quad generation (`MutableQuad`, `PipeRegistryClient`) stays common.
  - Fabric's 26.x model and renderer APIs are still moving, so this slice may slip into Phase 2.
- **As built (S11e).**
  - `TilePipeHolder` implements the marker `platform.render.PipeRenderData` and hands itself over (`getPipeRenderData()`). It is a view, not a copy: the quads are still read from the tile while the section meshes, as before, so a re-mesh without a model-data refresh still draws the current state. `getModelData` is a one-line delegate to `Platform.renderData().blockEntityRenderData(...)` (NeoForge: `ModelData` with the one `ModelProperty<PipeRenderData>` in `NeoForgeRenderData`; Fabric: the data itself, what `RenderDataBlockEntity#getRenderData` returns). The tile's `requestModelDataUpdate()` calls became `requestRenderDataUpdate` (a no-op on Fabric, which re-reads render data on every re-mesh). Phase 2 adds the tile's `getRenderData` arm.
  - `ClientRegistrationSinks.pipeBlockModel(block, PipeModelQuads)` swaps every state's model. `PipeModelQuads` (client contract) turns the data into the cutout and translucent quads plus the geometry key; common `transport.client.model.PipeBlockModelQuads` implements it over `PipeModelCacheAll`. NeoForge: `PipeBlockStateModel` moved whole as `NeoForgePipeBlockStateModel` (both its `DynamicBlockStateModel` and 1.21.1 `IDynamicBakedModel` arms), installed from its own `ModifyBakingResult` listener added just before transport's item-model editor. Fabric: `FabricPipeBlockModel` (`WrapperBlockStateModel`, `emitQuads` via `QuadEmitter.fromBakedQuad`, `createGeometryKey`), installed by a `modifyBlockModelAfterBake` modifier; Fabric API 26.3 offers everything this needs.
  - `ClientPlatform.models()` (`ClientModels`): `withVertexColours` (>=1.21.11) is NeoForge's `BakedColors` on its patched `BakedQuad`, so `MutableQuad` builds vanilla's quad and colours it through the seam. Fabric gap: vanilla's `BakedQuad` has no per-vertex colour, so the quad stays uncoloured there. The 1.21.1-only `QuadItemBakedModel` and `FragileFluidShardModel` moved whole (`NeoForgeQuadItemModel`, `NeoForgeFluidShardModel`) behind `fixedQuadItemModel` and `layeredFluidContainerModel`.
  - Left in common code on 1.21.1 only (no Fabric line there): the `IDynamicBakedModel` arms of the facade, gate, lens and pipe item models (on >=1.21.10 they are vanilla `ItemModel`s and stay common), and `ModelData.EMPTY` in the builders' preview renderers.

### Patched-vanilla overrides
- **Scope.** About 15 methods: `getFlammability`, `getFireSpreadSpeed`, `ignitedByLava`, `onCaughtFire`, `getCloneItemStack` (5-arg), `onDestroyedByPlayer`, `getSoundType` (4-arg), `addRunningEffects`/`addLandingEffects`, `doesSneakBypassUse`, `onItemUseFirst`, `supportsEnchantment`, `getModelData` and `writeClientSideData`.
- **Phase 1.** Tag each one `@LoaderHook("neoforge:IBlockExtension#getFlammability")` (source retention) and keep `@Override`. Move any non-trivial body into a common method, so the override is a one-line delegate.
- **Phase 2.** The Fabric node fails `@Override` loudly on each one. Then wrap each in `//? if neoforge {` and add the Fabric route: a mixin, `FlammableBlockRegistry`, or a callback.
  - **As built (Phase 2).** Every tagged override (`EntityRobot`'s four since Phase 2a, below) sits in a `//? if neoforge {` arm; an override already in an MC chain joins it as `neoforge && >=X` arms (never a loader arm inside an MC arm). Each delegates to a public common method declared by a small loader-neutral interface in `platform.hook` (`FireReactiveBlock`, `PlayerBreakGuard`, `PickStackBlock`, `BlockParticleEffects`, `ExplosionArmouredBlock`, `PositionalSoundBlock`, `SneakBypassItem`, `UseFirstItem`, `EnchantmentFilterItem`, `LevelLifecycleBlockEntity`) or `platform.menu.MenuOpeningData`, which the block, item or tile implements on every node. The Fabric route finds the interface and calls the same method: Fabric API events in `FabricLoaderHookRoutes` / `FabricClientLoaderHookRoutes` (`UseBlockCallback`, `PlayerBlockBreakEvents.BEFORE`, `PlayerPickItemEvents.BLOCK`, `EnchantmentEvents.ALLOW_ENCHANTING`, block-entity load queued to the next tick as NeoForge does, chunk unload), otherwise mixins in `platform.fabric.mixin` (`buildcraftunofficial.mixins.json`): `FireBlock` burn/ignite odds and the catch-fire point (not `FlammableBlockRegistry`, whose fixed numbers cannot follow the `canBurn` option), `LavaFluid.isFlammable`, the sneak test in both game modes' `useItemOn`, sprint and landing particles, `ExplosionDamageCalculator`, `ServerPlayer.openMenu`, which closes the spectator gap, and the sound type where NeoForge passes the position (the step and fall sounds, `BlockItem.place`, and the client's block-break level event; a `PositionalSoundBlock` only, so other blocks keep vanilla's call). Two overrides need a differently named Fabric arm: `TilePipeHolder#getRenderData` (`RenderDataBlockEntity`) and its client-side `loadAdditional` (vanilla's path for the update tag and the data packet). `RenderFiller#getRenderBoundingBox` needs none (vanilla has no per-renderer frustum test). Fabric gaps: the player-break guard runs server side before `playerWillDestroy` (no part-break particles, and the client predicts the break until the server restores it); a dispensed flint and steel does not set off a gas; chunk-unload hooks run before the chunk saves, not after.
- **Overrides returning NeoForge types** (`getModelData`) go through the render-data seam above.

## Phase 1 slice plan

**Baseline check for every slice.**
- `:<node>:compileJava` and `:<node>:compileTestJava` on all six nodes.
- `:26.3:test` and `:1.21.1:test`.
- The census, posting its delta to #98.

| # | Slice | Files (approx) | Risk | Extra verification |
|---|---|---|---|---|
| S1 | Loader services, binder, loader constants, guard, census | ~30 | Low | Game tests on 26.3 and 1.21.1; runServer to Done |
| SP | Fabric spike: compile-only `26.3-fabric` node building `buildcraft.platform` plus a Fabric `LoaderServices` (needs the user's go-ahead) | ~10 build | Medium (build) | All NeoForge jars byte-identical |
| S2 | `PlatformProtection` and fake players (probes, CommonHooks/EventHooks, RobotProtection, stripes, FakePlayerUtil) | ~18 | Medium | Game tests on all nodes |
| S3 | Network registration and `PacketContext` (18 payloads, BCCore, BCTransport, StirlingFuelSync) | ~25 | Medium | NetCodecSanityTester; dedicated-server connect; smoke |
| S4 | Send sites and `registryBuf` | ~16 | Low | Same as S3 |
| S5 | Menus: 7 MenuTypes files, 14 open sites, IBCMenuProvider, the anonymous providers | ~25 | Medium | Smoke opening every GUI, including as a spectator |
| S6 | Config registration and reload hooks | ~5 | Low | Toml diff |
| S7 | Small NeoForge types: Tags to `c:` TagKeys, SoundActions, DeferredSoundType, loot params, DataComponentIngredient, IShearable, IEntityWithComplexSpawn, TicketController | ~25, two commits | Low | Game tests |
| S8a-e | Game and lifecycle hooks per subsystem (lib, core, transport, robotics, then the other four), including `@EventBusSubscriber` conversion | 10-15 each | Medium | Game tests on all nodes |
| S9a-f | `BCRegistrar`/`BCHolder`, one subsystem per slice (scripted) | 6-15 each | Low | Registry-id dump diffed per node |
| S9g | Creative-tab contribution list | ~6 | Low | RoboticsCreativeTabTester |
| S9h | Attachments (golden fixtures first) | ~5 | High | Fixture tests; game tests |
| S9i | Conditions, biome modifier and features moved whole | ~8 | Low | Release boot matrix |
| S10 | Entrypoint move; `init(IEventBus)` becomes `init()` (scripted) | ~60 | Medium | fullTestSuite; boot matrix |
| S11a-e | Client: registration sinks, level-render hook, tint/outline/tooltip/session, client extensions, models with PipeRenderData (last) | 6-15 each | Medium | Smoke on 26.3, 26.2, 1.21.10 and 1.21.1 |
| S12 | Golden save-format characterisation: tanks, distiller, heat exchanger, robot, snapshot fluid, `"stacks"` key | ~6 tests | n/a | Pass on current code first |
| S13a-f | `BCFluidStack` (codec-parity test first), then the import swap per package | 15-20 each | High | S12 tests; fullTestSuite |
| S14a-f | Transfer: BCFluidTank owns serialize, BCTransaction core, items, fluids, energy/FE, pipe flows, robots/DockingStation/API | 15-25 each | High | S12 tests; transaction tests; fullTestSuite. Start at `impl-opus` |
| S15 | `@LoaderHook` tagging and common-method extraction | ~12 | Low | Compile |
| S16 | API slice: NbtApiUtil, api/events decision, delete the getDist regex | ~30 | Medium | fullTestSuite |

The transfer and fluid work (S12-S14) is roughly half of Phase 1 and carries most of the risk. It must not block the cheaper slices above it.

## First slice

**S1: loader services, binder, loader constants, guard, census.**

**New files.** Each gets the 2026 BuildCraftUnofficial-contributors MPL header.
- `src/main/java/buildcraft/platform/package-info.java`: the contract rule, the binder rule and the implementation-package rule.
- `src/main/java/buildcraft/platform/Platform.java`: the binder, with one accessor, `loader()`, and its lazy holder. The holder has the `//? if neoforge {` arm live and the `//? if fabric {` arm commented.
- `src/main/java/buildcraft/platform/LoaderServices.java`: the interface from Seam per category.
- `src/main/java/buildcraft/platform/neoforge/package-info.java`
- `src/main/java/buildcraft/platform/neoforge/NeoForgeLoaderServices.java`: body inside `//? if neoforge {`. It holds the only `FMLEnvironment.getDist()`, `ModList`, `IModInfo`, `getModFileById`, `FMLPaths` and `ServerLifecycleHooks` references outside `api/`.
- `scripts/platform-census.py`: see Progress metric.

**Build and guard edits.**
- Root `gradle.properties`: `loader=neoforge`.
- `build.gradle.kts`: read `loader` and set the Stonecutter constants. Keep the `getDist` regex; it still serves the implementation and `NbtApiUtil`.
- `src/test/java/buildcraft/StonecutterDirectiveGuardTester.java`:
  - `TERM` (line 108) and the evaluator must accept the bare identifiers `neoforge` and `fabric`, with the active node's loader read from `versions/<active>/gradle.properties`, falling back to the root `loader`.
  - `ACTIVE_NODE` (line 104) must tolerate a `-<loader>` suffix.
  - Fold one case into the existing hazards test (around line 141): on the 26.3 NeoForge active node, a `fabric` arm is inactive and a `neoforge` arm is live.
  - What it protects: the guard can judge loader arms. What breaks it: a loader constant added without teaching the guard, which today throws "cannot evaluate".
- `AGENTS.md`: a short "Platform layer" paragraph pointing here.

**Call sites moved** (one-to-one; drop the imports that become unused). Paths are under `src/main/java/buildcraft/`.
- Client-dist gate: `builders/BCBuilders.java:73`, `core/BCCore.java:125`, `energy/BCEnergy.java:43`, `factory/BCFactory.java:45`, `lib/BCLib.java:52`, `robotics/BCRobotics.java:155`, `silicon/BCSilicon.java:51,176`, `transport/BCTransport.java:65`, `builders/item/ItemSnapshot.java:138`, `lib/client/guide/parts/recipe/ClientGuideRecipeCache.java:79`.
- Dist gate and current server: `lib/misc/NBTUtilBC.java:56,60`, `robotics/zone/ZoneMapSavedData.java:175,177`.
- Current server: `lib/marker/MarkerSubCache.java:319`.
- Game dir: `builders/snapshot/GlobalSavedDataSnapshots.java:109`, `lib/script/SimpleScript.java:540`.
- Config dir: `lib/client/BCLibClient.java:41`, `lib/script/ScriptableRegistry.java:142`.
- Mod list:
  - `builders/snapshot/RulesLoader.java:81` (`modIds()`) and `lib/client/guide/parts/contents/GuidePageContents.java:290` (`modVersion(BCCore.MODID)`).
  - `lib/script/ScriptableRegistry.java:115-117` (`modSourcePaths(id)`; the dev `classes`-to-`resources` sibling fix stays in common code).
  - `lib/script/SimpleScript.java:218`, `lib/compat/AccessorySlots.java:28` (since Phase 2a `platform/neoforge/NeoForgeAccessorySlots`; read once, when the binder first builds it) and `core/BCCoreClient.java:136`.
- **Out of scope:**
  - `api/core/NbtApiUtil.java`, because of the `api/` rule (S16).
  - `api/core/BuildCraftAPI.java`'s dead `ModContainer` local (S16).
  - The comment-only mentions in `IRobotAccess`, `CuriosHeadItemRenderer` (since Phase 2a `NeoForgeCuriosHeadRenderer`) and `UnreachableEntityCache`.
  - `FmlJunitEnvironmentTest`, which stays NeoForge-specific.

**Acceptance check.**
1. `:<node>:compileJava` and `:<node>:compileTestJava` pass on all six nodes. The 1.21.1 pass proves the regex reached the implementation and the constant arm resolved.
2. `:26.3:test` and `:1.21.1:test` pass, including the guard.
3. `:26.3:runGameTestServer` and `:1.21.1:runGameTestServer` pass (dedicated-server dist gating).
4. `:26.3:runServer` reaches Done.
5. A grep for `FMLEnvironment|FMLPaths|ServerLifecycleHooks|ModList|IModInfo` in `src/main/java` hits only `platform/neoforge/`, `api/core/NbtApiUtil.java` and comments.
6. The census shows the FML group gone outside the implementation and reports no impl leaks.

No new behavioural test, since the slice is a neutral move, and no changelog entry.

## Progress metric

`scripts/platform-census.py`, run with `python -I`, read-only.

**Parsing.**
- It strips `//` and `/* */` comments and handles the `*///?}` closer (~218 uses), which gives the active-node view.
- With `--node <n>` it reads `versions/<n>/build/generated/stonecutter/main/java` instead.
- It skips `.disabled` files.

**What it reports**, per API group, per subsystem, and for the top files:
1. Live `net.neoforged` imports and fully qualified references outside `buildcraft/platform/neoforge/**`. This is the headline number.
2. All-node lines: the `net.neoforged` lines in commented arms. These are informational; 1.21.1-only arms are listed separately.
3. Allowlisted references, each with a reason, kept in the script: `ModConfigSpec`/`ModConfig` pending the config decision (the API's transfer and capability files left in S14f, the rest of `buildcraft/api/**` in S16). The allowlist should end empty.
4. Patched overrides: NeoForge extension-method names harvested from `.neoforge-ref/<26.3 dir>/.../common/extensions/I*Extension.java`, matched against BC overrides, with tagged and untagged counts.
5. Impl leaks: any common file naming `buildcraft.platform.neoforge` or `.fabric` (other than the binders), any loader impl package naming the other loader's package, and any `buildcraft.platform` contract file naming a loader or BC gameplay type. Any leak means a nonzero exit.
6. Directive lines remaining in common code. This is the secondary payoff metric.
7. Test-tree figures, informational only.

**Output.** A one-line summary plus `--json`. After each slice the orchestrator posts the line with `issues.sh note 98`. Nothing in the tree records history, and no test pins a count.

**Baseline** (26.3 view, before S1): about 317 main files with live references, 673 live import lines and 204 live fully qualified references.

## Fabric node (Phase 2) prerequisites

- The spike (SP) has answered these questions:
  - Loom and MDG side by side via the `bc.neoforge`/`bc.fabric` convention plugins inside one Stonecutter tree.
  - Constants resolving on `26.3-fabric`.
  - `version("26.3-fabric", "26.3")` giving `stonecutter.current.version == 26.3` in the build scripts.
  - The guard working with a suffixed active node.
  - Whether to move `platform/neoforge` into a `neoforge` source set.
- Phase 1 finish line met on the 26.3 view.
- Resource twins:
  - `fabric.mod.json` beside `META-INF/neoforge.mods.toml`, each expanded by `processResources` per loader.
  - An access widener translated from `META-INF/accesstransformer.cfg`, and the mixins json.
  - The Fabric data transform for conditions and components (built: `DataFabric`, see Data JSON).
  - Fabric code for biome modifiers and the `fluid_container` item models (both built).
- A mixin or registry route for each `@LoaderHook` tag.
- Config route decided (open question 1).
- Energy: Team Reborn Energy as an optional dependency, behind the RF gate.
- Test plan:
  - FML-JUnit has no Fabric twin; `fabric-loader-junit` only bootstraps vanilla.
  - Unit tests stay NeoForge-node-only. Gate the whole test set by loader in the build; never add per-file `//? if neoforge` wrappers.
  - The Fabric node runs a curated `fabric-gametest` registration over the common game-test bodies (built: Phase 2c, below).

**As built (Phase 2a).** `26.3-fabric` compiles all of `src/main/java` (no source filter; `platform/neoforge` files are empty there), builds `BuildCraftRedux-<v>+mc26.3-fabric.jar` (the NeoForge jar names are unchanged) into the same `build/libs/<mod_version>/` through its own `buildAndCollect`, and its `nodeVerification` is compile, `validateAccessWidener`, `processResources`, `jar` and `buildAndCollect`. It boots a dedicated server to "Done" and a dev client to the title screen. What it took:
- **Registration.** `FabricRegistryServices` queues each entry at its register call and builds it on first `BCHolder.get()` or in `registerPending()`, which `BCFabricEntrypoint` calls after `BCCore.init()` (NeoForge's registry order, call order within one). Building at the call ran a block-entity factory while the block holder class that triggered it was still in static init. `BCHolder` takes a `Supplier<Holder>` for this; NeoForge passes its holder unchanged. Aliases (`LegacyAliases`) go through Fabric API's `FabricRegistry.addAlias` (interface-injected on `Registry`; its `MappedRegistry` mixin resolves them in the lookups by id and by key, as NeoForge's `resolve` does), queued like the entries and added once they are built, as Fabric warns about an alias whose target is missing; pinned on both loaders by `legacy_aliases_resolve`.
- **Recipe viewers.** The NeoForge-only REI annotations (`@REIPluginClient`/`@REIPluginCommon`) sit in `//? if neoforge {` arms: a loader-metadata exception to the patched-override-only rule, like the robot's class header. On Fabric the plugins are the `rei_client`, `rei_common` and `jei_mod_plugin` entrypoints in `fabric.mod.json`. REI's Fabric POM puts its own dependencies at runtime scope, so `basic-math` and `architectury-fabric` are compile-only deps.
- **New seams.**
  - `Platform.extensions()` (`VanillaExtensions`) holds the vanilla calls NeoForge patches a context-aware twin into: 4-arg `getSoundType`, player dig speed, explosion resistance, `getExpDrop`, `CustomData.contains`, `FluidState.canConvertToSource`, `getPersistentData`, and the level-aware `SavedDataType`. `ClientPlatform.extensions()` (`ClientVanillaExtensions`) holds the client ones: position-aware `shouldRenderFace`, the sprite pixel read, PiP submit and scissor peek.
  - `ClientModels.materialInfo`. NeoForge's AO-flag `MaterialInfo` constructor.
  - `RegistryServices.creativeTabBuilder`. NeoForge's no-arg builder plus `withTabsBefore`; Fabric uses `FabricCreativeModeTab.builder()`.
  - `CraftingServices.recipeMap` and `FluidServices.registerFluidContent`/`getFluidContent`/`setFluidContent`. The fragile shard's component is `SimpleFluidContent` on NeoForge and `FabricFluidContent` on Fabric, both with the same saved shape.
  - `Platform.accessorySlots()` plus `ClientRegistrationSinks.headAccessoryRenderer`. Curios and Accessories, moved whole as `NeoForgeAccessorySlots`/`NeoForgeCuriosHeadRenderer`.
  - `platform.hook.LevelRemovalEntity`. The robot's `onRemovedFromLevel`; Fabric uses `ServerEntityEvents.ENTITY_UNLOAD`.
- **Fabric routes.**
  - `getExpDrop`: the block's own `spawnAfterBreak` with `Block.popExperience` caught (`BlockExperienceMixin`; infested blocks skipped).
  - Level-aware saved data: `ServerChunkCacheMixin` tells each dimension's `SavedDataStorage` its level, and `SavedDataStorageMixin` wraps `computeIfAbsent`/`get`/`collectDirtyTagsToSave`.
  - `canConvertToSource`: an invoker.
  - The entity item lookup (`TransferAccess.findItem(Entity)`): Fabric API has none, so `FabricEntityItems` is BuildCraft's own `EntityApiLookup` (`buildcraftunofficial:item_storage`, `Storage<ItemVariant>`, no context) with NeoForge's registrations (the chest boats and raft and the chest and hopper minecarts as their `Container`, the player as its inventory); another mod offers its entity's items with a provider on it. Then NeoForge's fallback, as an `EntityApiLookup` fallback provider (asked only when no provider for the type answers, as NeoForge registers it last): a horse, donkey, mule or llama as its inventory, any other living entity as its two hands then its four armour slots, each slot taking what the entity can equip there and calling `onEquipItem` once the outer transaction commits (NeoForge's `LivingEntityEquipmentWrapper`). `LivingEntity.equipment` and `AbstractHorse.inventory` are class-tweaker lines (NeoForge reaches them through its own patches).
  - Shearing (`ShearServices`): the vanilla blocks NeoForge marks `IShearable` (leaves, seagrass, tall grass, vines, cobwebs, with its defaults: always shearable, no drops) plus `platform.hook.ShearableBlock`, BuildCraft's loader-neutral twin of the block `IShearable`, which `NeoForgeShearServices` also accepts (no main BuildCraft block implements it; the test-only `UnshearableBlock` does, so both stripes shears tests run on both loaders).
  - Persistent data: a `buildcraftunofficial:persistent_data` attachment.
  - The break-speed event (`GameHooks.onBreakSpeed`, NeoForge's `PlayerEvent.BreakSpeed`): `PlayerMixin` fires it at the end of `Player.getDestroySpeed`, where NeoForge posts its event, for real and fake players on both sides; `BlockBehaviourMixin` tells it the dug position from `getDestroyProgress` (NeoForge's position-aware `getDestroySpeed(state, pos)`), as does `VanillaExtensions.destroySpeed`; another caller of the plain `getDestroySpeed` fires it with no position, as NeoForge's deprecated overload does. The contract does not carry NeoForge's cancel (a speed of -1).
  - Vanilla members NeoForge opens itself: class-tweaker lines (`ShapedRecipe.pattern`, `Block.popExperience`, `LiquidBlock.fluid`, `Item.getPlayerPOVHitResult`, `GhostSlots`, `AbstractButton.SPRITES`, `CustomData.tag`, `RecipeManager.recipes`, `GuiGraphicsExtractor` state and scissor stack, `SpriteContents` frames).
  - The robot's `isPushedByFluid(FluidType)` has a `fabric` arm on vanilla's `isPushedByFluid()`.
- **Fabric gaps.**
  - No accessory slots: Curios and Accessories have no Fabric 26.3 build, so the goggles count in the head slot only and are not drawn on a curio.
  - Extra spawn data (`platform.hook.SpawnDataEntity`, the robot's wearables list) arrives one packet after the spawn, not inside it: `FabricSpawnData` sends it from `EntityTrackingEvents.START_TRACKING` and applies it to the client entity by id.
  - No creative-tab ordering: tabs follow registration order.
  - The sound type and explosion resistance answer BuildCraft's hook interfaces (`PositionalSoundBlock`, `ExplosionArmouredBlock`), so a pipe sounds like its material and a facade armours it; another mod's block answers without its position. Face culling (`shouldRenderFace`) stays vanilla's state-only test: no BuildCraft block overrides NeoForge's `hidesNeighborFace`/`supportsExternalFaceHiding`, so it gives BuildCraft's own blocks the same answer. The step on a carpet or snow layer over a BuildCraft block (`playCombinationStepSounds`/`playMuffledStepSound`, which vanilla gives no position) gets NeoForge's positions from `PlayerMixin`, which hands them to `EntityMixin` around those calls in `Player.playStepSound`; another entity calling them answers without its position.
  - Experience from a modded block whose `spawnAfterBreak` also does other things runs those things too.
  - Plus the gaps listed under each seam above.

**As built (Phase 2c): game tests on Fabric.** `:26.3-fabric:runGameTest` (Loom's `fabricApi.configureTests`, Fabric API's runner under `-Dfabric-api.gametest`) runs the shared game-test bodies and is part of the node's `nodeVerification`.
- **Registration is shared.** `BuildCraftGameTests.register(gameTestRun)` registers the test-only blocks and every `registerAll` entry through `BCRegistrar` on `Registries.TEST_FUNCTION`. The loader hooks are test files: `platform/neoforge/NeoForgeGameTestHooks` calls it from `FMLConstructModEvent` (the 1.21.1 `GameTestRegistry` reflection moved there whole), and `platform/fabric/BCFabricGameTestEntrypoint` is the `main` entrypoint of a second dev-only mod, `buildcraftunofficial_gametest` (`src/test/resources-fabric/fabric.mod.json`). `FabricRegistryServices` builds the entries of a registrar subscribed after BuildCraft's `registerPending` at its `subscribe()` (a late initializer's entries were never built before).
- **The Fabric set** is bc.fabric's `gametest` source set over the TEST source set's Stonecutter copy, minus every JUnit file (by content), `buildcraft/platform/neoforge/**`, and the NeoForge-typed files on its `fabricGameTestExcludes` list, each with its reason. Bodies reach the loader through seams (`Platform.fakePlayers()`, `Platform.extensions()`, `Platform.protection()`, `TransferAccess`) and fire loader events through `lib.test.LoaderTestEvents` (NeoForge's bus, or the Fabric event's invoker), which also stands up claim mods and observers on the loader's own events (`claimEntityInteractions` and `claimEntityJoins` at a `HookOrder`, `claimPlacements` as a Common Protection API provider on Fabric, `claimAttacks`, `observeRightClickBlocks`, `observeLivingDrops`), so the robot protection, owner identity and stripes entity-claim tests run on both; a few bodies keep a `neoforge`/`fabric` arm where the loader's route itself differs (a patched-vanilla override against its Fabric hook interface). `WorkClaimMod` and `LoaderTestEvents.claimAttacks` stand claims up on `UseBlockCallback`/`UseItemCallback`/`AttackEntityCallback` on Fabric. A test the node cannot run sits in a `//? if neoforge {` arm of `registerAll` under `// Not on Fabric: <reason>`; `buildcraft.gradle.FabricGameTests` drops the manifests of tests the node does not register. Team Reborn Energy is on the game-test runtime, so the FE tests run against `FabricEnergyStorages`.
- **Pass check.** Stdout goes to `build/run/gameTest/console.log` (`-PbcLoudGameTestLogs` keeps it on the console), and the run fails unless `latest.log` ends with "All N required tests passed" (N > 0). `-PbcTests=<glob>` filters as on NeoForge. Two hang guards: vanilla's `GameTestInfo` waits for its structure chunks to be entity-ticking with no timeout of its own, so the game-test mod's `GameTestInfoChunkWaitMixin` fails a test still waiting after 120 seconds of wall-clock time spent ticking (naming it; ticks would not do, the server sprints and chunk generation can take thousands of them; a gap between two of the test's ticks counts for at most a second, so a blocked server thread is not charged to the tests it blocks), and the task has a 20-minute wall-clock timeout. Two early full runs stalled that way for close to an hour: the chunk-ticket bug below, BuildCraft's release unforcing a structure chunk the framework had forced. The later watchdog failures (2 tests in 1 of 6 combined runs, #114) were the watchdog counting a blocked server thread: `chunk_ticket_release_keeps_other_forced_chunk`, the test pinning that fix, forced a fresh chunk 64 chunks away, and `setChunkForced` loads it on the server thread behind the generation of the 27x27 chunks its ticket reaches; next to the NeoForge game-test servers that blocked every test for 10 to 109 s (measured), all of it charged to the waiting tests of the batch. The test now uses its own framework-forced structure chunk. The other synchronous loads (vanilla placing a batch's structures on fresh ground, `FarChunk`) still block for up to about 15 s under load, which the gap cap absorbs.
- **Fabric bugs fixed on the way.** `FabricVanillaExtensions.explosionResistance` ignored `ExplosionArmouredBlock` (only real blasts, through the mixin, saw a facade's armour); `FabricRegistryServices` never built the entries of a registrar subscribed after BuildCraft's init; `FabricChunkTickets` forced and unforced chunks in vanilla's shared forced set, so releasing a chunk BuildCraft had loaded unforced it for /forceload, other mods and the game-test framework too; it is now a `buildcraftunofficial:chunkloader` ticket type (pinned by `chunk_ticket_release_keeps_other_forced_chunk`); its owner map, keyed by dimension, was never cleared on unload, and dimension keys are shared between worlds, so a stale set from an integrated server's last world made every ticket in the next look already held — none was added and every BuildCraft chunk loader silently stopped — until the map was dropped on the level-unload hook (pinned by `FabricChunkTicketsGuardTester`, a source pin: no test server can stage an unload through the seam).
- **Placement claims** (#116): `WorkClaimMod` answers placements on Fabric as a Common Protection API `ProtectionProvider` each claim registers and removes, so the Builder, quarry frame, mining well, flood gate, planter and stripes placement tests run there.
- **Not on Fabric**: only the tests that load client classes (Fabric's dedicated server refuses them; the client game test runs their bodies instead, below). The level-aware saved-data tests reach NeoForge's patched `SavedDataType`/`SavedDataStorage` through loader arms (on Fabric the level `SavedDataStorageMixin` hands a `levelSavedDataType`). Both loaders see a robot's swing through `GameHooks.onBreakSpeed` (`LoaderTestEvents.observeBreakSpeeds`), so the owner-identity break and the deployer's swing-identity check run on both.
- **Client game test (in-world visual check).** `:26.3-fabric:runClientGameTest` (Loom's `enableClientGameTests`, Fabric API's `fabric-client-gametest-api-v1` under `-Dfabric.client.gametest`) runs `platform/fabric/BCFabricClientGameTest`, the game-test mod's `fabric-client-gametest` entrypoint: a 1600x900 window, a flat singleplayer world, a command-built rig (every pipe kind, a redstone engine powering kinesis pipes, a tank of oil, distiller, heat exchanger, Stirling and combustion engines, volume marker, frames, quarry), the oil bucket and fragile shards in hand, the tank and Stirling GUIs and the guide book, each screenshotted to `versions/26.3-fabric/build/run/clientGameTest/screenshots/` for a person to look at. It then runs the seven game-test bodies that load client classes (the guide's recipe lookups, the Zone Planner overlay geometry) on the singleplayer server's thread, each through a `DetachedGameTestHelper` (a `GameTestHelper` answering only the level, relative positions, assertions and success), and fails on any that fails; on NeoForge they stay ordinary game tests. It fails on a GUI that does not open as its BuildCraft screen, a shot with a patch of missing-texture magenta, or a `latest.log` line naming BuildCraft that is an ERROR or says missing/unable to/failed/could not/exception; the task then requires the test's pass line. It needs a GL context (it runs on a machine without an interactive desktop session, as long as the GPU driver gives the window one), which CI may lack, so it is **not** in `nodeVerification` and Loom does not hang it on `check`. Fabric's runner can hang at the title screen before the test begins (#119): the render thread starts the test thread from a tick and, if that thread has not yet registered on the runner's `Phaser` when the render thread reaches the end of the frame, the render thread arrives alone and parks on its task semaphore while the test thread, registered one phase late, parks waiting for it (thread dump: `Semaphore.acquire` in `Minecraft.postRunTasks` against `Phaser.arriveAndAwaitAdvance` in `ThreadingImpl.enterPhase` from `lambda$runTestThread$0`; a 3 s delay before the registration reproduces it every run). The game-test mod's `ClientGameTestThreadStartMixin` holds the render thread at the end of `ThreadingImpl.runTestThread` until the test thread has registered. The task's 15-minute timeout stays as a backstop.

## Hidden tooling costs

These are NeoForge-only today and need Fabric twins or loader-awareness:
- **Tests:** FML-JUnit (`neoForge { unitTest }`). The game tests run on Fabric since Phase 2c.
- **Guard testers:**
  - `StonecutterDirectiveGuardTester`: loader terms, suffixed active node.
  - `MainSourceSet` and the `buildcraft.test.mainSourceDirs` property: every source set must be passed.
  - `ForRemovalApiGuardTester`: runs on the NeoForge nodes only. **Built (Fabric):** the `26.3-fabric` node's main and gametest compiles lint `deprecation,removal` with `-Werror` (`checkDeprecations` in `bc.fabric`, part of `nodeVerification`), so any `[removal]` and any unsuppressed `[deprecation]`, Fabric API's or vanilla's, fails the build. `-PbcLint` turns `-Werror` off for an audit.
  - `CopyrightHeaderTester`: new files.
  - `FabricChunkTicketsGuardTester`: a source pin where no run can stage the event (a level unload), see Fabric bugs below.
- **Reference sources:** `.neoforge-ref` and `scripts/neoforge-sources-sync.sh`. Fabric needs a Fabric API sources dir-set and `INDEX.txt` rows.
- **Version tracking:** the `neoforge-bump` skill and the SessionStart version-check hook, for Fabric Loader and Fabric API pins.
- **Smoke:** the smoke runner (`scripts/smoke/`, which hard-codes `config/neoforge-client.toml`) and McDevBridge, if it is a NeoForge mod.
- **Release:** the boot and connect matrix, the release orchestrator, Modrinth and CurseForge loader tags, the jar name (`tasks.jar` in `bc.neoforge.gradle.kts` carries no loader component), and the update manifest.
- **Build hygiene:** `DuplicatesStrategy.EXCLUDE` in `processResources` would silently hide a resource present in both loader trees. Switch to FAIL when the loader resources split.
- **Census upkeep:** keep the census script in step with directive syntax (`elif`, `*///?}`).

## Open questions for the user

1. **Config on Fabric:** Forge Config API Port, which keeps `ModConfigSpec` in common code allowlisted but is unverified for `LOCAL` on 26.3, or an in-house builder over `ModConfigSpec` (about 9 files, which ends the exemption)? Recommendation: decide after the spike checks FCAP `LOCAL` support. Phase 1 S6 only seams registration, either way. **Taken (2026-11-04, orchestrator, reversible): Forge Config API Port.** FCAP 26.3.1 for Fabric ships `ModConfigSpec`/`ModConfig` and `ConfigRegistry`/`ModConfigEvents`; it has no `LOCAL` type (its set is COMMON, CLIENT, SERVER, STARTUP), so `FabricConfigRegistrar` maps LOCAL and COMMON to its COMMON (both sides, one file, never synced). The only loss is that a NeoForge 26.3 SYNCED spec is not synced on Fabric. `ModConfigSpec` stays allowlisted in common code.
2. **Fabric spike timing:** may a compile-only, non-shipping `26.3-fabric` node land right after S1, so build-level and seam-shape risks surface before the expensive slices? Recommendation: yes.
3. **BC's own events** (`api/events/*`, `EventBuildCraftReload`, `RobotEvent`): move them to BC-owned listener lists, in the style of `PipeEventBus`, so third-party mods stop seeing them on the NeoForge bus? Or keep posting them on the NeoForge bus through the platform? Recommendation: BC-owned, since no addon consumes them.
4. **Accept the Fabric fluid granularity gap?** BC stays integer mB; on Fabric, transfers below 81 droplets are rejected.
5. **Accept that worlds do not move between loaders?** Attachments and some NBT roots differ.
