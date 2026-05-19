# BuildCraft 1.12.2 → 26.1.x Port Status

Last audited: 2026-05-19. The changelog block in `changelog.md` titled "Changes since 26.1.x Beta release 6" is authoritative for what has shipped since the previous audit; consult it before re-auditing this file.

## Subsystem Status Overview

The 1.12.2 → modern port is effectively done. Java packages still mirror the old submod boundaries (`transport`, `energy`, `factory`, …) but they're just packages now — see `CLAUDE.md` for why. The table below tracks remaining gaps per package.

| Subsystem (package) | Active `.java` | Status |
|:---|:---:|:---|
| **API** | 229 | ✅ Complete |
| **Lib** | 606 | ✅ Complete |
| **Core** | 88 | ✅ Complete |
| **Transport** | 122 | ✅ Complete |
| **Silicon** | 75 | ✅ Complete |
| **Factory** | 51 | ✅ Complete |
| **Energy** | 31 | ✅ Complete |
| **Builders** | 127 | ✅ Complete |
| **Robotics** | 12 | ⚠️ Only Zone Planner ported (low priority — robots were not actively maintained in 1.12.2) |

Zero `.java.disabled` files remain anywhere in the project; 1.12.2 logic remains accessible via the `8.0.x-1.12.2` branch if reference is ever needed.

---

## 🔧 Outstanding work

### Advancements — 15 orphaned triggers
`minecraft:impossible` is the placeholder used for advancements granted from Java via `AdvancementUtil.unlockAdvancement(...)`. The following 15 advancements still have only an impossible-trigger JSON with no matching Java grant — pick a thematic in-code event for each and wire it up:

- [ ] `all_plugged_up`
- [ ] `building_for_the_future`
- [ ] `colorful_electricial`
- [ ] `destroying_the_world`
- [ ] `flooding_the_world`
- [ ] `goggles`
- [ ] `heating_and_distilling`
- [ ] `lava_power`
- [ ] `list`
- [ ] `logic_transportation`
- [ ] `paper`
- [ ] `paving_the_way`
- [ ] `refine_and_redefine`
- [ ] `start_of_something_big`
- [ ] `too_many_pipe_filters`

### Block Sounds
- [ ] Most blocks use the default `SoundType` — only 43 explicit declarations exist across the codebase (mostly `METAL`, three `STONE`, three `SLIME_BLOCK`). A deliberate sound pass is still needed (engines, pipes, facades, tanks, machines).

### Guidebook
- [ ] Fill the last 2 stubs: `engine_basics.md` (1-line title only) and `registry_overview.md` (empty).

### Help Ledger — content coverage
- [ ] The `LedgerHelp` framework is active and interactive slot/tank highlighting works (confirmed in `ScreenEngineIron`, etc.). Per-block content is the gap: each block needs to register `ElementHelpInfo`s via `addHelpElements()`. Coverage is uneven; sweep needed.

### Robotics
- [ ] No robots, robot AI, robot stations, or robot items ported. **Low priority** — robots were not actively maintained in 1.12.2.

---

## 🆕 New Features (version 1.1)

- [ ] **Pump-on-top fluid extraction for Mining Well & Quarry.** Both miners stop or skip when they hit blocking fluids today (Mining Well halts on lava/oil, Quarry skips fluid columns). Adding a Pump on top of either should consume fluid from the same blocking column, draining it into a buffer / adjacent tank or pipe so the miner can resume. Power for the on-top Pump comes out of the host miner's internal MJ battery rather than requiring a separate engine hookup. Mining Well top texture should swap to the open Flood Gate sprite when a Pump is mounted. Open: shared-battery priority/throttling, where the drained fluid goes, whether the Quarry's larger column needs a different sweep pattern than the Well's single-block-below path.
- [ ] **Marker connector connection length HUD.** Display the measured distance (in blocks or appropriate unit) of a marker connection the player is currently looking at.
- [ ] **Facade smooth shading.** AO infrastructure exists in `MutableQuad`; needs facade-specific wiring.
- [ ] **Texture filtering bug and crash.** Needs description / repro to be actionable.
- [ ] **Updated Filler mode icons.**
- [ ] **Abandoned quarry frames** with pre-excavated hole down to deepslate (worldgen).
- [ ] **Flesh out wrench as a tool.** Add small damage value, quick attack speed, durability, make it the ideal tool for harvesting BuildCraft blocks, enchantable; consider loot table / chance of spawning with a zombie.
- [ ] **Unify plug in-world geometry under vanilla `models/block/plug_*.json`.** Today the plug rendering pipeline has three flavours: (a) blocker / power_adapter load BC-dialect JSON from `models/plugs/` via `ModelHolderStatic`, (b) timer / light_sensor / pulsar-base bake from hardcoded UVs in [PlugBakerSimpleItems.java:70-94](src/main/java/buildcraft/silicon/client/model/plug/PlugBakerSimpleItems.java#L70-L94) (translated from the 1.12.2 JSONs at port time), and (c) lens / facade / gate are genuinely parametric and stay in Java. Migrate (a) and (b) to standard vanilla block-model JSONs loaded through the normal resource manager, with one generic rotating baker that pulls BakedQuads off a face and rotates them per `KeyPlug*.side`. Wins: resourcepacks can reshape *and* retexture any static plug with stock Minecraft JSON, the `models/plugs/` directory and `ModelHolderStatic` go away (verify nothing else uses the latter first), and surviving Java bakers become clearly-exceptional parametric cases. Tradeoff: non-trivial refactor across transport + silicon; per-element `shade` / extended UV tricks in the BC dialect need a NeoForge model-extension equivalent or to be dropped.

---

## 🚫 Blocked Features/Optimizations

### Awaiting upstream (Minecraft / NeoForge) infrastructure

- [ ] **Split pipe textures onto a dedicated `buildcraftunofficial:pipes` atlas.** Would shrink the vanilla blocks atlas from 8192×4096 back to ~1024×1024 by moving the 400 `dye_replace`-generated dyed fluid-pipe variants (plus the 25 base fluid pipe sprites and the rest of `textures/pipes/`) off the vanilla atlas onto a dedicated one. Atlas size at 8192×4096 is fine on any GPU made in the last decade but is a real concern for older Intel integrated graphics that cap at 8192×8192 or below. **Blocked by three independent vanilla-level constraints** (all empirically verified 2026-05-15 via a one-pipe-family spike):
  - **`BakedQuad.MaterialInfo.of()` hardcodes a binary atlas check.** Item render type is derived via `if (sprite.atlasLocation().equals(LOCATION_BLOCKS)) Sheets.cutoutBlockItemSheet() else Sheets.cutoutItemSheet()`. A sprite on any third atlas falls into the `else` branch and binds the items atlas — there's no third-atlas case in the factory.
  - **`ChunkSectionLayer` is a closed enum.** Three values (SOLID, CUTOUT, TRANSLUCENT), each hardwired to a vanilla `RenderPipeline` that binds the blocks atlas to Sampler0. No NeoForge `RegisterChunkSectionLayer` event; `AddSectionGeometryEvent` lets a mod append geometry but still constrains it to one of the three existing slots.
  - **AtlasManager rejects cross-atlas duplicate sprites.** Declaring a sprite on both atlases produces a vanilla warning per duplicate ("Duplicate sprite … will be rejected in a future version") — the "register on both" workaround is itself on a Mojang-enforced deprecation timer.

  Spike result: pipe rendered with panorama backdrop and GUI icons bleeding through quad faces — the chunk renderer ignored `sprite.atlasLocation()` and sampled whatever was bound to Sampler0.

  **Reopen trigger:** Mojang exposes an extensibility hook for chunk render layers, per-quad atlas binding, or otherwise opens the third-atlas case.

  **Cheap escape hatch if a low-spec-GPU compat report comes in:** ~1 hour revert of commit `5a6cdb5ac` — remove the 25 `dye_replace` entries from `assets/minecraft/atlases/blocks.json`, flip `PipeBaseModelGenStandard.ensureDyedSprites` to return null, restore the three fallback branches. Painted fluid pipes drop from 1-layer dyed-sprite rendering to 2-layer base+mask-overlay; atlas shrinks back to ~1024×1024.

- [ ] **Fluid viscosity** currently isn't really a thing. Flow speed can be modified, but negative density (floating gases) is not native, and traversal speed / swimming modifications aren't possible.

---

## 🧹 Finalization

- [ ] Deprecation and warning fixes (both compile-time and runtime).
- [ ] Final code review across all subsystems.
