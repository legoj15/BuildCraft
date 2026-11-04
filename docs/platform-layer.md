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
  - **Contract rule:** these files may reference only the JDK, vanilla and other `buildcraft.platform` types. Never a loader type, and never a BC gameplay class. That is what lets a Fabric node compile the contracts alone.
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
- **Directive interplay.** The 1.21.1 `getDist()` to `.dist` regex (`build.gradle.kts`, the `isPre1_21_10` `getDist` pair) then applies only to the implementation file, plus `api/core/NbtApiUtil.java` until the API slice. The regex is deleted in the API slice, replaced by a `>=1.21.10` arm inside the implementation.

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
- **`PlatformProtection`.** `canBreak`, `canPlace`, `canAttack`, and the right-click and use-item simulations. It absorbs `BreakEventCompat`, `PlaceEventProbe`, the `AttackEntityEvent` posts (`RobotProtection`, `StripesHandlerMinecartDestroy`), `CommonHooks.onRightClickBlock`/`onItemRightClick` (`BlockUtil.java:404,448`) and `EventHooks.doPlayerHarvestCheck`/`onBlockPlace`. Fabric has no place event, so its `canPlace` falls back to `mayInteract`. That is a known protection gap.
- **Typing.** BC-owned hooks over vanilla types; listeners are wired in per-subsystem classes in the implementation.
- **`@EventBusSubscriber` classes.** The six of them become explicit hook registrations. `FakePlayerUtilSubscriptionTester` and `LevelUnloadSubscriptionTest` reflect over `@SubscribeEvent`, so they are rewritten to test registration or behaviour.
- **Entrypoint (late slice).** `@Mod` moves to `platform/neoforge/BCNeoForgeEntrypoint`, which captures the mod bus. The 51 `init(IEventBus)` signatures become `init()`.
- **Format risk.** None. Order risk: record the observed order before changing any priority. Note that the comment at `transport/BCTransport.java:82` contradicts the LOW/LOWEST pair it sits next to.
- **Directive interplay.** The render-stage and other event-class cliffs move into the implementation with their directives.

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

### Transfer and capabilities (a sub-epic; give it its own issue)
- **Shape.**
  - Tokens and lookup: BC typed `BCCapability<T>` tokens replace the `Object` tokens in `CapUtil` and the pipe API. The `==` identity checks in `PipeFlowFluids`, `PipeFlowItems` and `PipeFlowRedstoneFlux` must keep working. `TransferAccess.find{Fluid,Item,Energy}(level, pos, side)` returns cached BC views, and a `CapabilityRegistrar` sink is replayed into `RegisterCapabilitiesEvent`.
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

### Fluids (value type)
- **Shape.** `buildcraft.api.fluids.BCFluidStack` mirrors NeoForge `FluidStack`'s method names (`EMPTY`, `getFluid`, `getAmount`, `grow`/`shrink`, `copy`, `copyWithAmount`, `isSameFluid`, `isSameFluidSameComponents`, `getComponents`, `CODEC`, `OPTIONAL_CODEC`, `STREAM_CODEC`, `getHoverName`). The 477 uses then migrate as an import swap.
- **Typing.** BC-owned. Conversion happens only in `platform/neoforge`.
- **Format risk is high.** `CODEC` must emit NeoForge's exact shape (`id`, `amount`, `components`). It is used in the `SimpleFluidContent` component (`BCCore.java:63-65`), `RequiredExtractorTank` snapshots, and the distiller, heat-exchanger and robot NBT. A codec-parity test is written first.
- **Properties.** `FluidType` reads (density, viscosity, temperature, lighter-than-air, bucket, sounds) go through a BC `FluidProperties` record. The NeoForge implementation builds the `FluidType` from it, and `BCEnergyFluids`' subclasses move whole.
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

### Patched-vanilla overrides
- **Scope.** About 15 methods: `getFlammability`, `getFireSpreadSpeed`, `ignitedByLava`, `onCaughtFire`, `getCloneItemStack` (5-arg), `onDestroyedByPlayer`, `getSoundType` (4-arg), `addRunningEffects`/`addLandingEffects`, `doesSneakBypassUse`, `onItemUseFirst`, `supportsEnchantment`, `getModelData` and `writeClientSideData`.
- **Phase 1.** Tag each one `@LoaderHook("neoforge:IBlockExtension#getFlammability")` (source retention) and keep `@Override`. Move any non-trivial body into a common method, so the override is a one-line delegate.
- **Phase 2.** The Fabric node fails `@Override` loudly on each one. Then wrap each in `//? if neoforge {` and add the Fabric route: a mixin, `FlammableBlockRegistry`, or a callback.
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
  - `lib/script/SimpleScript.java:218`, `lib/compat/AccessorySlots.java:28` (it stays a static-final initialiser, so its timing is unchanged) and `core/BCCoreClient.java:136`.
- **Out of scope:**
  - `api/core/NbtApiUtil.java`, because of the `api/` rule (S16).
  - `api/core/BuildCraftAPI.java`'s dead `ModContainer` local (S16).
  - The comment-only mentions in `IRobotAccess`, `CuriosHeadItemRenderer` and `UnreachableEntityCache`.
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
3. Allowlisted references, each with a reason, kept in the script: `ModConfigSpec`/`ModConfig` pending the config decision, and `buildcraft/api/**` until S16. The allowlist should end empty.
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
  - The Fabric data transform for conditions and components.
  - Fabric code for biome modifiers and the `fluid_container` item models.
- A mixin or registry route for each `@LoaderHook` tag.
- Config route decided (open question 1).
- Energy: Team Reborn Energy as an optional dependency, behind the RF gate.
- Test plan:
  - FML-JUnit has no Fabric twin; `fabric-loader-junit` only bootstraps vanilla.
  - Unit tests stay NeoForge-node-only. Gate the whole test set by loader in the build; never add per-file `//? if neoforge` wrappers.
  - The Fabric node runs a curated `fabric-gametest` registration over the common game-test bodies.

## Hidden tooling costs

These are NeoForge-only today and need Fabric twins or loader-awareness:
- **Tests:** FML-JUnit (`neoForge { unitTest }`) and `neoforge.enableGameTest`, plus the game-test registration through `RegisterEvent` on `TEST_FUNCTION` (`BuildCraftGameTests.java:35`).
- **Guard testers:**
  - `StonecutterDirectiveGuardTester`: loader terms, suffixed active node.
  - `MainSourceSet` and the `buildcraft.test.mainSourceDirs` property: every source set must be passed.
  - `ForRemovalApiGuardTester`: the Fabric API needs its own `forRemoval` scan.
  - `CopyrightHeaderTester`: new files.
- **Reference sources:** `.neoforge-ref` and `scripts/neoforge-sources-sync.sh`. Fabric needs a Fabric API sources dir-set and `INDEX.txt` rows.
- **Version tracking:** the `neoforge-bump` skill and the SessionStart version-check hook, for Fabric Loader and Fabric API pins.
- **Smoke:** the smoke runner (`scripts/smoke/`, which hard-codes `config/neoforge-client.toml`) and McDevBridge, if it is a NeoForge mod.
- **Release:** the boot and connect matrix, the release orchestrator, Modrinth and CurseForge loader tags, the jar name (`tasks.jar` in `bc.neoforge.gradle.kts` carries no loader component), and the update manifest.
- **Build hygiene:** `DuplicatesStrategy.EXCLUDE` in `processResources` would silently hide a resource present in both loader trees. Switch to FAIL when the loader resources split.
- **Census upkeep:** keep the census script in step with directive syntax (`elif`, `*///?}`).

## Open questions for the user

1. **Config on Fabric:** Forge Config API Port, which keeps `ModConfigSpec` in common code allowlisted but is unverified for `LOCAL` on 26.3, or an in-house builder over `ModConfigSpec` (about 9 files, which ends the exemption)? Recommendation: decide after the spike checks FCAP `LOCAL` support. Phase 1 S6 only seams registration, either way.
2. **Fabric spike timing:** may a compile-only, non-shipping `26.3-fabric` node land right after S1, so build-level and seam-shape risks surface before the expensive slices? Recommendation: yes.
3. **BC's own events** (`api/events/*`, `EventBuildCraftReload`, `RobotEvent`): move them to BC-owned listener lists, in the style of `PipeEventBus`, so third-party mods stop seeing them on the NeoForge bus? Or keep posting them on the NeoForge bus through the platform? Recommendation: BC-owned, since no addon consumes them.
4. **Accept the Fabric fluid granularity gap?** BC stays integer mB; on Fabric, transfers below 81 droplets are rejected.
5. **Accept that worlds do not move between loaders?** Attachments and some NBT roots differ.
