# Architecture — long-form reference

This file is the detailed companion to the compressed Architecture section in
[AGENTS.md](../AGENTS.md): the full prose, reasoning, and trap explanations behind the bullet
rules. AGENTS.md holds the rules an agent needs in every session; this file holds the why.
When the architecture changes, update AGENTS.md's rules (and the relevant class javadocs);
update this file when the reasoning itself changes.

---

Avoid using deprecated functions or API systems that throw warnings. This may result in
*technically* unnecessary node separation, but ideally keeps the code compatible with newer
NeoForge versions/Minecraft releases.

## History — this is one mod, not eight

The 1.12.2 (8.0.x) and 1.7.10 (7.1.x) versions we're based on used a coremod and submod
architecture. This is no longer clean in modern NeoForge (all 9 mods would show up
separately), so all the submods were collapsed into subsystems of one monolithic mod. So
submods such as `energy`, `silicon`, `transport`, `api`, `lib`, `factory`, `builders` and
`robotics` are all just organized compartments of the same "core" mod now.

## Initialization Pattern

`BCCore` is the single `@Mod` entry point. It calls a per-subsystem `BC{Name}.init(modEventBus)`
(e.g. `BCTransport.init`, `BCEnergy.init`) which registers that subsystem's
`DeferredRegister<T>` instances onto the mod event bus. These `BC{Name}` classes are
registration helpers grouped by topic — they are *not* sub-mods. Client-only code lives in
`BC{Name}Client` classes, instantiated only on `Dist.CLIENT`.

Initialization order: `BCLib` → core registries → per-subsystem registries →
`FMLCommonSetupEvent` → `FMLLoadCompleteEvent`.

## Key Subsystems

**Tiles**: every BC block entity sits on a thin shared hierarchy in `lib.tile`.
`AbstractBCBlockEntity` hosts the cross-version save/load directive (subclasses override
version-neutral `writeData`/`readData` — never `saveAdditional` directly), owner persistence
(`OwnerData` + `onPlacedBy`), the menu-open/`canInteractWith` machinery, and the
`dropContentsOnRemoval` hook. `AbstractBCSyncedBlockEntity` adds the standard
`getUpdateTag`/`getUpdatePacket` sync pair (with the `<1.21.10 onDataPacket` fix) plus
`markForRenderUpdate()`/`markForGuiUpdate()` — the no-re-mesh data push; use these for
BER/GUI-only state, and `sendBlockUpdated` only when the *block model* genuinely changes.
`TileBC_Neptune` (item handler management, player tracking) and the bespoke-state machines
(engines via `TileEngineBase_BC8`, `TileTank`, `TileDistiller_BC8`, `TileHeatExchange`,
`TileFloodGate`, `TileLaser`) extend the synced base; `TileMarker`, `TileSpringOil`, and
`TilePipeHolder` stay on the plain base because they sync through their own channels — do not
give them the standard pair. Block-side boilerplate (owner forward, menu open, ticker,
`<1.21.10 onRemove` catch-all) lives once in `lib.block.BlockBCTile_Neptune` /
`BlockBCTile_Directional` (+ shared `BlockBCTileSupport`); engines have their own
`BlockEngineBase_BC8`. Every `BlockBCTile_Neptune` subclass must declare its own
`getRenderShape -> RenderShape.MODEL` — the base doesn't supply it, and `BaseEntityBlock`
defaults it to `INVISIBLE` on the 1.21.1 node only (dropped at 1.21.10), so omitting it
compiles clean and looks correct everywhere except that one node; enforced by the
`BlockRenderShapeTester` game test, which walks the live block registry. Capability attachment
uses NeoForge's `RegisterCapabilitiesEvent`; MJ-consumer machines register through the
`MjCapabilities.registerMjConsumer` bundle.

**Menus/GUI**: `ContainerBC_Neptune` is a plain `AbstractContainerMenu`; the shared
shift-click/phantom-slot/widget-message machinery lives in `BCContainerSupport` behind the
`BCContainer` interface. Tile-backed menus extend
`ContainerBCTile<T extends AbstractBCBlockEntity>` (resolves the tile via `resolveTile`,
reach-checks via the tile's `canInteractWith`). Only the two real recipe-book tables
(AutoWorkbench, Advanced Crafting) extend `ContainerBCCrafting` (`RecipeBookMenu`-rooted; hosts
the version-forked recipe-book stub block once). Menu-opening tiles implement
`IBCMenuProvider` — `writeClientSideData` writes the `BlockPos` on every open path
(spectator-safe); never route the pos through the two-arg `openMenu(provider, pos)` overload
(double-write).

**Buttons**: one implementation. Plain text buttons are vanilla `Button`; everything else is a
`BCButton` configured through `BCButton.builder(x, y, w, h)` — vanilla's `widget/button*` face
(resource-pack styled) plus an optional `ButtonIcon` (a `ButtonSprite` GUI-atlas icon under
`textures/gui/sprites/button/`, an item render, or a live choice), label, `latched` state (a
switched-on toggle / chosen radio option shows vanilla's pressed-in disabled face but stays
clickable), live `activeWhen` and tooltip, and a `MouseButtons` mask (the action receives the GLFW
button). All per-line button API divergence lives in `BCButton`. Where the art is the whole
button and a grey face would be wrong (the guide book: page-turn and back arrows, the contents
page's sort-order radio group), `BCButton.builder(x, y, ButtonImage)` draws a `ButtonImage` in
place of the face — GUI-atlas sprites `<name>` + `<name>_highlighted` (+ `<name>_selected` /
`<name>_selected_highlighted` for a radio option, picked by `latched`) under
`textures/gui/sprites/` — and keeps everything else (tooltip-as-narration, sound, keyboard). A
screen that paints everything itself draws such widgets in its own order with
`BCGraphics.widget(...)` and registers them with `addWidget` (input/narration only); a guide page,
which is not a screen, forwards clicks to them from its `mouseClicked`. Screens should not paint
buttons from their background sheet, hit-test them, or play the click sound themselves.
`ButtonUnificationGuardTester` is a source scan and only catches the fingerprints of that
pattern: a screen playing `UI_BUTTON_CLICK` (the gate's wiring-diagram connectors are the one
allowlisted exception), a hover-variant `GuiIcon` (`FOO_HOVERED`), and a button widget subclassed
outside `lib/gui/button`; a hotspot with none of those passes it. Deliberately not buttons: the
guide's chapter tabs and contents entries (navigation links) and its search tab (the search
box's own decoration). `ButtonSpriteTester` / `ButtonImageTester` pin every icon / image
constant to its PNG(s) and vice versa.

**Pipes/Transport**: Each pipe is a composition of a `PipeBehaviour` (determines pipe type
logic) and a `PipeFlow` (handles what flows through it — items, fluids, power). `PipeRegistry`
and `PipeDefinition` manage pipe types.

**Statements/Logic** (Gates): `IStatement`, `ITrigger`, and `IAction` interfaces with
`StatementManager` as the central registry. Used by silicon gates to wire conditions to
actions. Note: statements do **not** use `DeferredRegister` — `StatementManager` is a
hand-rolled static registry and each `BCStatement` self-registers in its constructor (do not
add redundant `registerStatement` calls). This is correct: statements aren't registry content.

**Snapshots/Blueprints**: The builders module uses a snapshot system under
`buildcraft.builders.snapshot`. Schematics (`ISchematicBlock`, `ISchematicEntity`) define how
blocks/entities are saved and placed. `SchematicBlockFactoryRegistry` and
`SchematicEntityFactoryRegistry` map block/entity types to their schematic factories.
`GlobalSavedDataSnapshots` persists snapshot data server-side.

**Networking**: Custom payloads using NeoForge's `RegisterPayloadHandlersEvent`. Payloads use
`StreamCodec<RegistryFriendlyByteBuf, T>` for serialization. Handlers call `ctx.enqueueWork()`
to execute on the logical thread.

**Config**: Two specs — `BCUnifiedConfig` (COMMON) and `BCUnifiedClientConfig` (CLIENT) — each
wrap the per-subsystem configs into one `ModConfigSpec` using `.push()/.pop()` sections. The
COMMON/CLIENT split is load-bearing (client-only render/display options must live on the CLIENT
spec).

**Recipes**: `BuildcraftRecipeRegistry` for refinery recipes; JEI integration for display.

**JEI**: one `@JeiPlugin` per subsystem under `<subsystem>/compat/jei` (core, energy, factory,
silicon, robotics), shared machinery in `lib/compat/jei`. Each category follows the Assembly Table
shape: a JEI-free record + collector (unit-testable — `ProgrammingRecipeCollectorTester` is the
model), a `*JeiTypes` holder, and a category that crops the machine's own GUI texture and prints the
MJ cost through `JeiMjLabel`. Cross-node API rules, all enforced by a clean
`-PbcLint=deprecation` sweep since 2026-09-26: recipe types are `IRecipeType` on the JEI 20+ nodes
(`>=1.21.10`) and `RecipeType` only on 1.21.1 (its JEI 19.x predates `IRecipeType`) — one
`//? if >=1.21.10` directive per holder/handler; hand-written item subtype keys register only
through `JeiSubtypes.register(registration, item, stack -> key)` (it hides the 1.21.1
`ISubtypeInterpreter` fork; return null for "no subtype"; the key needs structural `equals`),
while a key that is exactly one data component uses `registerFromDataComponentTypes` on the JEI 20+
nodes (the paintbrush — with `JeiSubtypes` as its 1.21.1 fallback); put single stacks in slots with
`addItemStacks(List.of(stack))` — `addItemStack` is deprecated for removal on JEI 20+ and its
`add(ItemStack)` successor does not exist on 19.x.
