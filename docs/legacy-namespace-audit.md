# Legacy Namespace Audit

`src/main/resources/assets/` and `src/main/resources/data/` carry namespaces left over from the 1.12.2-era multi-mod suite, alongside the canonical modern namespace `buildcraftunofficial` (the only registered modid per `META-INF/neoforge.mods.toml`).

## TL;DR

- 8 legacy `assets/` namespaces (293 files total) plus 2 legacy `data/` namespaces (2 files) clutter the resource tree.
- **One** silently-broken loader was already known: `RulesLoader.loadAll()` (compat rules). **One more** is broken in the same shape: `CraftingUtil` (`@EventBusSubscriber(modid = "buildcraftlib")` — never registers).
- Most legacy content is dead in two ways at once: wrong namespace **and** wrong subdirectory (e.g. `assets/<ns>/recipes/` vs the modern `data/<ns>/recipe/` singular). Modern Minecraft wouldn't pick the file up regardless of namespace.
- A small handful of legacy assets are still actively reachable via direct `ResourceLocation` lookup or via the script system that walks all `data/` subfolders. These need real migration, not just deletion.
- About **270 files** are pure orphans / dead-format and can be deleted outright. About **20 files** carry content that should be moved to the canonical namespace and have their references rewritten.

## Phase 1 — Inventory

### `assets/buildcraft/` (2 files)

| Path | Status | Notes |
|---|---|---|
| `lang/en_US.lang` | **Orphan / dead format** | 960 lines of robotics translation strings (`buildcraft.boardRobot*`). `.lang` format hasn't been read since pre-1.13 — modern uses `lang/en_us.json`. None of the strings are present in the canonical [`assets/buildcraftunofficial/lang/en_us.json`](../src/main/resources/assets/buildcraftunofficial/lang/en_us.json). Useful as a reference when the robotics module is finally ported, but contributes nothing at runtime. |
| `logo.png` | **Orphan duplicate** | The active `logoFile="logo.png"` in `neoforge.mods.toml` resolves at the resource root ([`src/main/resources/logo.png`](../src/main/resources/logo.png)), not under any namespace. This copy is dead weight. |

### `assets/buildcraftbuilders/` (83 files)

| Subtree | Count | Status |
|---|---|---|
| `compat/buildcraft/builders/` | 32 | **Silently dead** — read by [`RulesLoader.loadAll()`](../src/main/java/buildcraft/builders/snapshot/RulesLoader.java#L71). The loader iterates `ModList.get().getMods()`, only `buildcraftunofficial` is registered, so it looks at `assets/buildcraftunofficial/compat/buildcraft/builders/` and finds nothing. |
| `advancements/` | 7 | **Orphan** — modern advancements load from `data/<ns>/advancement/` (singular). These wouldn't load even at the canonical namespace. |
| `blockstates/` | 3 | Orphan — `buildToolBlock`, `frameSurvivalBlock`, `marker_construction` are not registered as modern blocks anywhere. |
| `models/block/` | ~24 | Orphan — same as above, includes `architect_off/on`, `filler/pattern_*`, `frame_*`, `replacer`, etc. The active models are in `assets/buildcraftunofficial/models/block/`. |
| `models/item/` | ~10 | Orphan — old `blueprint/clean`, `template/clean` etc. The modern items use `assets/buildcraftunofficial/models/item/`. |
| `gui/filler*.json` | 3 | Orphan — 1.12.2 GUI JSON framework. Replaced by the modern `GuiBC8` system per the changelog. |
| `recipes/` | 4 | Orphan — wrong path (`assets/<ns>/recipes/` vs modern `data/<ns>/recipe/`). |

### `assets/buildcraftcore/` (78 files)

| Subtree | Count | Status |
|---|---|---|
| `advancements/` | 10 | Orphan, wrong path. |
| `blockstates/` | 8 | Orphan — `decorated`, `engine`, `markerBlock`, `power_tester` etc. Active blockstates live under `buildcraftunofficial:blockstates/`. |
| `models/block/` | 16 | Orphan — `engine/{wood,stone,iron,creative,rf}` etc. Note: live engine block textures under `buildcraftunofficial:textures/block/engine/` were already migrated per the changelog's `blocks/` → `block/` cleanup. |
| `models/item/` | ~30 | Orphan — `paintbrush/*`, `gears/*`, `decorated/*`, `diamond_shard`, etc. |
| `recipes/` | 13 | Orphan, wrong path; `recipes/list.json.old.txt` is explicitly `.old`. |
| **Textures** | 0 | None present. Anything referencing `buildcraftcore:textures/...` is broken (see Phase 2). |

### `assets/buildcraftenergy/` (19 files)

| Subtree | Count | Status |
|---|---|---|
| `advancements/` | 8 | Orphan, wrong path. |
| `blockstates/` | 2 | Orphan — `blockFuel`, `blockRedPlasma` — fluid blockstates not used by modern fluids. |
| `models/item/` | 4 | Orphan — old bucket models. |
| `recipes/` | 5 | Orphan + 1.12.2 format. `residue_to_pipe_sealant.json` uses `forge:bucketfilled` + `minecraft:item_nbt` (1.12.2 forge tags) and a `forge:mod_loaded` condition checking for `buildcrafttransport` (a non-existent modid → would never satisfy even if the recipe type existed). |

### `assets/buildcraftfactory/` (33 files)

| Subtree | Count | Status |
|---|---|---|
| `advancements/` | 8 | Orphan, wrong path. |
| `blockstates/` | 3 | Orphan. |
| `models/block,item,tiles/` | 11 | Orphan. |
| `recipes/` | 11 | Orphan, includes 1.12.2-format `_constants.json` and `water_gel_spawn.json` (same `forge:ore_shaped` / `forge:bucketfilled` issues as energy). |

### `assets/buildcraftlib/` (29 files)

| Path | Status | Notes |
|---|---|---|
| `textures/icons/lock.png` | **Active** | Loaded directly via `Identifier.parse("buildcraftlib:textures/icons/lock.png")` in [`GuiFiller.java:195`](../src/main/java/buildcraft/builders/gui/GuiFiller.java#L195). NeoForge auto-detects namespaces from the resource tree, so the lookup currently succeeds. |
| `textures/items/guide_book.png` | **Active** | Referenced from a guide markdown page (`assets/buildcraftunofficial/compat/buildcraft/guide/en_us/item/marker_path.md`). |
| `textures/blocks/engine/trunk_creative.png`, `trunk_overheat.png` | **Possibly active** | These two are missing from the modern `buildcraftunofficial:textures/block/engine/` set per the recent migration. If the creative/overheat engine variants render anywhere, they'd be reading from the legacy path. |
| Other `textures/blocks/engine/*.png` (7) | Orphan duplicates | The migration already copied chamber_base, trunk_black, trunk_blue, trunk_green, trunk_red, trunk_yellow into `buildcraftunofficial:textures/block/engine/`. |
| Other `textures/icons/*` (`engine_active.png.mcmeta`, `engine_warm.png.mcmeta`, `help_split.png`, `loading.png{,.mcmeta}`, `warning_major.png`, `warning_minor.png`) | **Possibly active** | Need to grep usage; these are the kinds of things gauges/ledgers would reference. |
| `textures/items/{debugger,guide_note}.png` | Likely active for `guide_note` (registered item), `debugger` is dev-only. |
| `textures/gui/buttons.png`, `gui/misc_slots.png.mcmeta` | Likely active | UI atlases. |
| `atlases/blocks.json` | **Orphan** | References `buildcraftlib:block/white` which doesn't exist. The active atlas is `buildcraftunofficial:atlases/blocks.json`. Safe to delete. |
| `gui/ledger/{controllable,engine_power}.json` | Orphan | 1.12.2 GUI JSON framework. |
| `lang/en_US.lang` | Orphan / dead format | 35 lines of guide chapter category names — unported but possibly reusable. |
| `models/item/engine_base.json` | Orphan | The active engine item models are under canonical. |
| `recipes/guide_book.json`, `advancements/recipes/guide_book.json` | Orphan, wrong path. |

### `assets/buildcraftrobotics/` (11 files)

| Path | Status | Notes |
|---|---|---|
| `blockstates/{requester,zonePlan}.json` | Orphan | Robotics blocks not registered yet — `TileZonePlanner` exists as a stub per its comments. |
| `models/{block,item,pluggables}/*` | Orphan | All robotics rendering. |
| `models/robot.json`, `pluggables/robot_station_base.{obj,mtl}` | Possibly useful for future robotics port. |

### `assets/buildcrafttransport/` (40 files)

| Subtree | Count | Status |
|---|---|---|
| `advancements/` | 12 | Orphan, wrong path. |
| `models/item/{pipewire,wire}/*` | 18 | Orphan — pipewire/wire item models for legacy items. |
| `models/pipes/stripes.json` | 1 | Orphan. |
| `recipes/` | 5 | Orphan + 1.12.2 format. |
| `recipes/_constants.json`, `_factories.json` | 2 | 1.12.2 forge recipe metadata. The `_factories.json` references `buildcraft.transport.recipe.EngineRegisteredFactory`, which **does not exist** in the codebase — confirms this is dead. |

### `assets/minecraft/` (3 files) — KEEP

These atlas merge into the vanilla `minecraft` namespace at runtime and are actively used (per the changelog's `gui.json` atlas fix). Not part of the legacy cleanup.

### `data/buildcraftcore/` and `data/buildcraftlib/` (1 file each)

| Path | Status | Notes |
|---|---|---|
| `data/buildcraftcore/compat/buildcraft/book.txt` | **Active** | Loaded by [`ScriptableRegistry`](../src/main/java/buildcraft/lib/script/ScriptableRegistry.java#L213): the script loader walks `data/` and treats every subfolder as a domain, regardless of registration. Registers a `"main"` guide book. |
| `data/buildcraftlib/compat/buildcraft/book.txt` | **Active** | Same — registers a `"meta"` guide book. |

The modern equivalent already exists at [`data/buildcraftunofficial/compat/buildcraft/book.txt`](../src/main/resources/data/buildcraftunofficial/compat/buildcraft/book.txt). Need to merge the two legacy entries into the canonical book.

## Phase 2 — Silent-bug patterns

The original `RulesLoader` bug was: iterate `ModList.get().getMods()` → build `assets/<modid>/...` path → silently skip legacy namespaces. I scanned for the same shape elsewhere.

### Confirmed silent-bug cases

| Location | Pattern | Impact | Severity |
|---|---|---|---|
| [`RulesLoader.java:71-122`](../src/main/java/buildcraft/builders/snapshot/RulesLoader.java#L71) | Iterates registered mods, looks under `assets/<modid>/compat/buildcraft/builders/` | `RULES` list silently empty — every block scans into a `SchematicBlockDefault` with no `requiredBlockOffsets` / `ignoredProperties` / `placeBlock` | **Already known** (band-aided with a `buildcraftbuilders` fallback in another commit). 32 rule files involved. |
| [`CraftingUtil.java:33`](../src/main/java/buildcraft/lib/misc/CraftingUtil.java#L33) | `@EventBusSubscriber(modid = "buildcraftlib")` | The `@SubscribeEvent`-annotated `onServerStarted` handler is **never registered** because no mod has id `buildcraftlib`. The FML event bus dispatcher binds subscribers to a specific mod context. | **Low impact** — the handler is just a debug `BCLog.info` dump of the vanilla `white_bundle` recipe class. Still wrong; the modid should be `buildcraftunofficial`. |

### Inspected and clean

| Location | Why it's not affected |
|---|---|
| [`ScriptableRegistry.java:115`](../src/main/java/buildcraft/lib/script/ScriptableRegistry.java#L115) | Iterates registered mods to **find the mod jar**, but then walks ALL subfolders of `data/` (line 213, `subFolder.getFileName()`), not just `data/<modid>/`. So `data/buildcraftcore/` and `data/buildcraftlib/` get loaded too — that's why the legacy `book.txt` entries above are still active. |
| [`GuideManager.java:181`](../src/main/java/buildcraft/lib/client/guide/GuideManager.java#L181) | Builds paths from `entryKey.getNamespace()` (the entry's own ResourceLocation), not the registered modid list. Whatever namespace the guide entry was registered with is the namespace the lookup uses. |
| [`ResourceLoaderContext.java:33`](../src/main/java/buildcraft/lib/client/model/ResourceLoaderContext.java#L33) | Uses `Minecraft.getInstance().getResourceManager().getResourceOrThrow(location)` — direct ResourceLocation lookup, not a modid iteration. |

### Broken `forge:mod_loaded` recipe conditions

`assets/buildcraftenergy/recipes/residue_to_pipe_sealant.json` and `assets/buildcraftfactory/recipes/water_gel_spawn.json` both contain conditions like `{"type": "forge:mod_loaded", "modid": "buildcrafttransport"}`. These conditions check the registered mod registry, so they'd evaluate to false even if these recipe files were in the right path/namespace. Combined with the 1.12.2 recipe format, they're triple-dead.

### Broken guide-markdown texture references

The guide markdown sources under `assets/buildcraftunofficial/compat/buildcraft/guide/en_us/` contain `<image src="…"/>` tags pointing at legacy namespaces. These ARE active references (loaded by `XmlPageLoader` at runtime), but several of the targets don't exist:

| Reference | Resolves? |
|---|---|
| `buildcraftcore:textures/items/marker_path.png` | **No** — `buildcraftcore` has zero textures. |
| `buildcraftcore:items/wrench` | **No** — same reason. |
| `buildcraftlib:textures/items/guide_book.png` | Yes — file exists in `buildcraftlib/textures/items/`. |
| `buildcraftenergy:textures/gui/combustion_engine_gui.png` | **No** — `buildcraftenergy` has zero textures. |

These render as broken-image / missing-texture in the guide book GUI.

## Phase 3 — Cleanup plan

### Migration order (low risk → higher risk)

**Step 1 — Pure deletes (no references, dead format). DONE in this audit commit.** 6 files, near-zero risk.
- ✅ `assets/buildcraft/logo.png` — duplicate of the active root `logo.png`.
- ✅ `assets/buildcraftlib/atlases/blocks.json` — orphan atlas referencing only `buildcraftlib:block/white`, which is itself dead.
- ✅ `assets/buildcraftlib/textures/block/white.png` — only consumer was the orphan atlas above; bundled with the same delete.
- ✅ `assets/buildcraftcore/recipes/list.json.old.txt` — explicit `.old`.
- ✅ `assets/buildcrafttransport/recipes/_factories.json` — references nonexistent class `EngineRegisteredFactory`.
- ✅ `assets/buildcrafttransport/recipes/_constants.json` — 1.12.2 forge recipe metadata, only consumed by `_factories.json` (now deleted).
- Verified `./gradlew compileJava` passes after deletes.

**Step 2 — Move-and-rewrite the live RulesLoader files. DONE.** 32 files in `assets/buildcraftbuilders/compat/buildcraft/builders/`.
- ✅ `git mv`d the whole subtree to `assets/buildcraftunofficial/compat/buildcraft/builders/`.
- ✅ Stripped the `buildcraftbuilders` fallback set out of `RulesLoader.loadAll()`; back to the original simple `for (modInfo : ModList.get().getMods())` loop.
- 55/55 game tests pass on the rebased base (was 34/34 before the rebase brought in the new fluid-mode + support-required testers).

**Step 3 — Drop the dead-book legacy `book.txt` scripts. DONE.** 2 files.
- Discovery: the legacy declarations register *separate* books (`buildcraftcore:main`, `buildcraftlib:meta`) under their respective namespaces, not duplicates of `buildcraftunofficial:main`. Nothing in Java references either, and `guide.txt` only fills `buildcraftunofficial:main`, so the two legacy books were registered-but-empty and unreachable from any item.
- A literal "merge" into the canonical script would have hit `ScriptableRegistry.executeScripts()`'s "Multiple scripts attempting to add `<name>`" error path, since the canonical `book.txt` already adds `"main"`. Pure delete is the right outcome.
- ✅ `git rm`d both `data/buildcraftcore/compat/buildcraft/book.txt` and `data/buildcraftlib/compat/buildcraft/book.txt`.
- Note left for follow-up: `BCCoreCreativeTabs.java:43` calls `ItemGuide.createForBook(..., "buildcraftunofficial:config")`, but no script defines `buildcraftunofficial:config` — the lookup currently returns `null`. Out of scope for this cleanup; warrants its own commit.

**Step 4 — Fix `CraftingUtil` modid. DONE.** 1-line edit.
- ✅ `@EventBusSubscriber(modid = "buildcraftlib")` → `@EventBusSubscriber(modid = "buildcraftunofficial")` in `CraftingUtil.java`. The debug `onServerStarted` handler now actually registers; behavioral change is just a one-line `BCLog.info` dump of the vanilla `white_bundle` recipe class on each server start.

**Step 5 — Fix or remove guide markdown texture references. DONE.**
- Found two `<image>` tags pointing at non-existent textures, both in `marker_path.md` (a placeholder/filler stub page):
  - ✅ `buildcraftcore:textures/items/marker_path.png` → `buildcraftunofficial:textures/items/marker_path.png` (canonical PNG existed all along).
  - ✅ `buildcraftenergy:textures/gui/combustion_engine_gui.png` → `buildcraftunofficial:textures/gui/combustion_engine_gui.png` (canonical PNG existed; namespace was the only thing wrong).
- ✅ `guide_page_format.md` two refs to `buildcraftcore:items/wrench` (one in escaped example text, one as a real image render) → `buildcraftunofficial:items/wrench`.
- The `buildcraftlib:textures/items/guide_book.png` ref in `marker_path.md` was deferred to Step 6 (where the texture itself moves).
- All other legacy-namespace strings under `compat/buildcraft/guide/en_us/` are inside `<json_insn>` example code blocks documenting the JSON-INSN script syntax, not real loads — left as-is.

**Step 6 — Move actively-referenced lib textures. DONE.** 6 files moved, 2 references rewritten. The grep for `buildcraftlib:` after this step should return only changelog/audit-doc mentions.
- (Correction added later — see the corrective changelog entry.) On closer inspection the "fix latent rendering bug" framing in this section was wrong: `LedgerEngine.drawIcon()` and `LedgerEngineFE.drawIcon()` already animate the engine icons via manual Java UV cycling (`vOffset = (System.currentTimeMillis()/50 % 6) * 16f`) and bypass Minecraft's animation system entirely, so the legacy `.mcmeta` sidecars were inert before *and* after the move; `misc_slots.png.mcmeta` contains a custom `buildcraft_data` block that no code in the repo reads; and `StandardButtonTextureSets.BUTTON_TEXTURES` is dead code (`new ButtonTextureSet(...)` is not invoked anywhere). The moves are still good cleanup — namespaces lined up, future code paths can rely on the canonical lookup — they just didn't fix any user-visible bug. A separate follow-up commit converts the engine ledgers to atlas-sprite-driven animation so the `.mcmeta` becomes load-bearing and resource packs can override the timing.
- ✅ `git mv` of the 6 currently-referenced files (history preserved):
  - `buildcraftlib/textures/gui/buttons.png` → `buildcraftunofficial/textures/gui/buttons.png`
  - `buildcraftlib/textures/gui/misc_slots.png.mcmeta` → `buildcraftunofficial/textures/gui/misc_slots.png.mcmeta`
  - `buildcraftlib/textures/icons/engine_active.png.mcmeta` → `buildcraftunofficial/textures/icons/engine_active.png.mcmeta`
  - `buildcraftlib/textures/icons/engine_warm.png.mcmeta` → `buildcraftunofficial/textures/icons/engine_warm.png.mcmeta`
  - `buildcraftlib/textures/icons/lock.png` → `buildcraftunofficial/textures/icons/lock.png`
  - `buildcraftlib/textures/items/guide_book.png` → `buildcraftunofficial/textures/items/guide_book.png`
- ✅ Updated the only two remaining `buildcraftlib:` callers: `GuiFiller.java:195` (lock icon) and `marker_path.md:17` (guide_book image).
- Side effect (intended): engine icon animations now actually animate; button/slot atlases load with their full intended sources.

**Step 7 — Bulk-delete the dead remainder. DONE.** 248 files.
- ✅ Whole-namespace `git rm -r` for `assets/buildcraftbuilders/`, `assets/buildcraftcore/`, `assets/buildcraftenergy/`, `assets/buildcraftfactory/`, `assets/buildcraftrobotics/`, `assets/buildcrafttransport/` — every remaining advancement/blockstate/model/recipe in those trees was wrong-namespace AND wrong-subdirectory (e.g. `assets/<ns>/recipes/` vs the modern `data/<ns>/recipe/` singular), so none were loaded by NeoForge regardless.
- ✅ Subtree `git rm -r` of `assets/buildcraftlib/{advancements,gui,models,recipes,textures}/` — all 21 remaining lib resources after Step 6's six moves.
- Verified zero references for the "possibly active" stragglers (`help_split.png`, `loading.png{,.mcmeta}`, `warning_{major,minor}.png`, `debugger.png`, `guide_note.png`, `trunk_creative.png`, `trunk_overheat.png`) before deleting; the only `help_split.png` mention is a Java comment, and `trunk_overheat` is aliased to `trunk_black` in the active models. Canonical `buildcraftunofficial:textures/item/guide_note.png` was already there for the registered guide_note item.

**Step 8 — Delete the 1.12.2 lang reference files. DONE.** 2 files.
- Reconsidered after inspecting `guidelines/`: that directory is for Eclipse IDE config (`.epf`, `.checkstyle`), not reference material. Per CLAUDE.md, the canonical home for "how 1.12.2 worked" is the `8.0.x-1.12.2` branch's `src_old_license` / `common` folders, with in-branch `.disabled` files reserved for Java mirrors. `.lang` files don't load in modern Minecraft anyway (the format was dropped after 1.13), so a `.disabled` rename would just be sitting in `assets/<legacy-ns>/lang/` indefinitely.
- ✅ `git rm`d `assets/buildcraft/lang/en_US.lang` (960 lines, robotics translations) and `assets/buildcraftlib/lang/en_US.lang` (35 lines, guide chapter category names). Whoever ports robotics later can pull the strings from git history or the 1.12.2 branch and rewrite them in the modern `lang/en_us.json` format.

## Result

After all 8 steps:
- 0 of 8 legacy namespaces remain. `assets/` now contains only `buildcraftunofficial/` and the vanilla-merge `minecraft/`.
- 0 silent-bug callers remain (RulesLoader's band-aid stripped, CraftingUtil's modid corrected, all `buildcraftlib:` references rewritten).
- Two pre-existing latent rendering bugs fixed as a side effect of Step 6 (engine icon animations, button atlas).
- Snapshot/blueprint NBT format untouched — no save-data migration needed.
- Verified all 55 game tests pass at every commit boundary.

### Save-data / blueprint compatibility

`SchematicBlockDefault` and friends serialize block IDs by their full `Identifier` (e.g. `minecraft:stone`, `buildcraftunofficial:builder`). None of the legacy namespaces appear in any save/blueprint NBT path I can see — they're purely resource-side. The compat rules in `buildcraftbuilders/` are read at boot, not stored in saves. So none of the steps above touch save format.

The user's run folder has `housetest2.bpt` and similar blueprint files. As long as the Step 2 move is purely a resource relocation (no rule-content edits), existing blueprints continue to load identically.

### Build / test guarantees

Each step in the plan can stand alone behind `./gradlew compileJava && ./gradlew runGameTestServer` (currently 55 tests). Specifically:
- Step 1 deletes only files with zero references — compile-clean.
- Steps 2-3 are content-equivalent moves; the active code paths read both the "before" and "after" locations interchangeably.
- Step 4 is a string change in an annotation; compile-clean and behaviorally equivalent (or fixed).
- Steps 5-6 require careful per-reference verification before each commit.
- Step 7 is the largest delete but should be a no-op behaviorally.

## Suggested commit cadence

Steps 1, 4 → one cleanup commit each.
Step 2 → one commit (move + Java edit + changelog entry).
Step 3 → one commit (book.txt merge + delete legacy + changelog entry).
Steps 5, 6 → likely several commits, one per cluster of related references.
Step 7 → one big delete commit, after all moves verified.
Step 8 → one commit (move lang refs out of `assets/`).

Each commit should add a `changelog.md` bullet under "Changes since 26.1 Beta release 5:".
