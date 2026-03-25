# BuildCraft Migration: 1.21.1 → 26.1

> [!IMPORTANT]
> Minecraft Java Edition 26.1 ("Tiny Takeover") released March 24, 2026.
> NeoForge **26.1.0.1-beta** is now available.

## Build Tooling Changes (Done)

| Setting | Old | New |
|---|---|---|
| Minecraft | 1.21.1 | 26.1 |
| NeoForge | 21.1.11 → 21.11.38-beta | 26.1.0.1-beta |
| ModDevGradle | 2.0.140 | 2.0.141 |
| Java | 21 | **25** |
| Gradle | 9.3.1 (no change) | ≥ 9.1.0 ✓ |
| Parchment | 2024.08.02 | **Removed** (MC is now unobfuscated) |

---

## Breaking Changes Impact Assessment

### 🔴 HIGH — Transfer Rework (`IItemHandler` / `IFluidHandler` → `ResourceHandler`)

NeoForge 21.9+ replaced the entire item/fluid/energy transfer API:
- `IItemHandler` → `ResourceHandler<ItemResource>`
- `IFluidHandler` → `ResourceHandler<FluidResource>`
- `IFluidHandlerItem` → `ResourceHandler<FluidResource>` + `ItemAccess`
- All associated capabilities replaced

**BuildCraft impact: ~50+ source files** use these APIs extensively, including:
- `buildcraft-lib`: `ItemHandlerSimple`, `IItemHandlerAdv`, `DelegateItemHandler`, inventory utils
- `buildcraft-factory`: `TileTank`, `TilePump`, `TileDistiller`, `TileHeatExchange`, `TileFloodGate`
- `buildcraft-transport`: `PipeFlowFluids`, `PipeFlowItems`, pipe behaviours
- `buildcraft-energy`: `TileEngineIron_BC8`, `TileEngineStone_BC8`
- `buildcraft-silicon`: `TileAdvancedCraftingTable`
- `buildcraft-api`: `IItemHandlerFiltered`, `IFlowFluid`, `IFluidHandlerAdv`

> [!CAUTION]
> This is the single largest migration effort. NeoForge provides deprecated compatibility shims
> initially, but they must eventually be replaced. Follow the [Transfer Rework Guide](https://neoforged.net/news/21.9-transfer-rework/).

---

### 🔴 HIGH — Rendering Overhaul

- `GuiGraphics` → `GuiGraphicsExtractor`
- `Screen#render` → `Screen#extractRenderState`
- `Screen#renderBackground` → `Screen#extractBackground`
- `AbstractContainerScreen#renderBg` → `Screen#extractBackground`
- `AbstractContainerScreen#renderLabels` → `AbstractContainerScreen#extractLabels`
- Block model texture map format changes (non-string entries rejected)
- New `MutableQuad` API added by NeoForge (potential simplification for our custom quad code)
- Last MC version with OpenGL-only support; Vulkan backend coming in 26.2

**BuildCraft impact: ~30+ GUI/rendering files** across all modules:
- `GuiBC8.java` (base class for all GUIs)
- All ledger classes, `GuiGuide`, `GuiStack`, `GuiIcon`
- Module-specific GUIs in factory, transport, silicon, robotics, builders, energy
- Debug overlay
- BER renderers may need adaptation

---

### 🟡 MEDIUM — `ItemStack` → `ItemStackTemplate`

- `ItemStack` can no longer be created until a world is loaded
- `ItemStackTemplate`: immutable record (item holder, count, data component patch)
- `template.create()` to get an `ItemStack`, `ItemStackTemplate.fromNonEmptyStack(stack)` for reverse
- `FluidStack` / `FluidResource` also require registries; `FluidStackTemplate` introduced
- Recipes and datagen must use templates for outputs

**BuildCraft impact:** Recipe datagen, any static `ItemStack` fields initialized at class-load time, recipe handling code.

---

### 🟡 MEDIUM — `ChunkPos` Changes

- `new ChunkPos(blockPos)` → `ChunkPos.containing(blockPos)`
- `ChunkPos.asLong(...)` → `ChunkPos.pack(...)`
- `new ChunkPos(packedLong)` → `ChunkPos.unpack(packedLong)`
- `ChunkPos` is now a record

**BuildCraft impact: ~10 files** — `TileQuarry`, `WireSystem`, `ChunkLoaderManager`, `ZonePlan`, `PipeItemMessageQueue`

---

### 🟡 MEDIUM — SavedData Splitting

Primary level data split into multiple SavedData files. `SavedData` API changes may affect:
- `MarkerSubCache` saved data
- `ZonePlannerMapData`
- `ChunkLoaderManager`

---

### 🟢 LOW — Loot Type Unrolling, Validation Overhaul

- Loot table format changes (type unrolling)
- Validation system overhaul with stricter data validation
- BuildCraft loot tables and data packs will need format updates

---

### 🟢 LOW — Minor Renames & Removals

- `Level#random` field now protected (use getter)
- `interactAt` removed
- Data component initializer changes
- Recipe serializer records
- Various method renames (see [full primer](https://github.com/neoforged/.github/blob/main/primers/26.1/index.md))

---

## Recommended Migration Strategy

1. **Phase 0 (Done):** Branch, update build config
2. **Phase 1:** Get compilation working with deprecated shims
   - Update `GuiGraphics` → `GuiGraphicsExtractor` renames
   - Fix `ChunkPos` constructor changes
   - Fix `ItemStack` static initialization issues
   - Address any `Screen#render` signature changes
3. **Phase 2:** Migrate Transfer APIs
   - Follow 3-step process from NeoForge guide
   - Start with commenting out capability providers
   - Migrate queries, then providers, then remove deprecated usage
4. **Phase 3:** Rendering pipeline updates
   - Adapt BER renderers to new rendering pipeline
   - Update block models if needed
5. **Phase 4:** Data format updates
   - Update loot tables, recipes, data packs to new formats
   - Migrate SavedData to new API
6. **Phase 5:** Test and stabilize

## References

- [NeoForge 26.1 Release Blog](https://neoforged.net/news/26.1release/)
- [Transfer Rework Guide](https://neoforged.net/news/21.9-transfer-rework/)
- [Porting Primer (ChampionAsh5357)](https://github.com/neoforged/.github/blob/main/primers/26.1/index.md)
- [Fabric Blog — MC Changes Detail](https://fabricmc.net/2026/03/14/261.html)
