# Guide book: robots chapter and table-of-contents restructure

Design note, 2026-10-15. Status: **proposal, undecided** — see the decisions at the end. Facts below were
verified against the tree on that date; re-check `guide.txt` and `GuideManager#addCategoryEntries` before acting.

## Where things stand

Content lives in `src/main/resources/assets/buildcraftunofficial/compat/buildcraft/guide/en_us/` (163 pages:
27 block, 34 item, 46 pipe, 35 trigger, 13 action, 5 concept, 2 config) and is registered by
`compat/buildcraft/guide.txt` through the aliases in `guide/util.txt`. Every entry carries `tag_type` and
`tag_subtype`; the contents page nests them per the active sort order (`GuiGuide.SORTING_TYPES`):

| Sort button | Nesting | State |
|---|---|---|
| Sort By Type (default) | type → subtype | the curated tree below |
| Sort By Mod | mod → type | everything collapses under one "BuildCraft" node; `ETypeTag.SUB_MOD` exists but no order uses it — **dead button** |
| Alphabetical | flat | fine |

Both tag values are free strings: a new top-level chapter is only a new `tag_type` plus a
`buildcraft.guide.chapter.type.<x>` lang key, and a subchapter only a `buildcraft.guide.chapter.subtype.<x>` key.
Per-entry sort weights (`_s` aliases) order siblings; a chapter inherits its lowest member's weight.

Current tree (Sort By Type): **Blocks** — Automation 10, Engines 6, Fluid 3, Laser Power 1, Mining 2, Refining 2,
Specialized 3 · **Items** — Basic 1, Chipsets 5, Fluid 2, Gears 5, Paperwork 3, Pluggables 8, Tools 8, Wires 1 ·
**Pipes** — Item 16, Fluid 11, Power 9, Structure 1, FE 9 · **Triggers** / **Actions** — Automation, Basic, Engine,
Fluid, Fluid Transport, Item, Item Transport, Pluggables, Power Delivery, plus the Java-built category entries
(Pipe Signals, Set Pipe Direction, Paint Passing Items, Set Power Limit, Filler Patterns, Extraction Presets).

### Robotics gaps (defects regardless of any restructure)

- **No robotics page exists.** The only mention is one sentence on `block/builder.md`. 1.7.10 had no guide book
  and 1.12.2 had no robots, so there is no upstream text to port — this is new writing. Ground truth for behaviour:
  `docs/robotics-gameplay-audit.md`, `docs/robotics-resurrection.md`, and the BC 7.1 operation notes in project
  memory (no command UI; Work-in-Area needs a Quartz/Emerald gate; tools come only from wooden item-pipe stations
  with Provide Items inside the work zone).
- **Six robotics items/blocks are search-only.** Robot, Redstone Board, Docking Station, Programming Table,
  Zone Planner, Requester have no `guide.txt` entry, so `PageEntryItemStack#iterateAllDefault` files them under
  the invisible "Other Items" chapter (visible only while a search query matches). The Integration Table lost
  its entry in `88b55844a` when it was dev-only; it is a real survival block now.
- **Twenty robot statements sprawl as untyped leaves.** 4 triggers (Sleep, Robot In Station, Station Linked,
  Station Reserved) and 16 actions (Goto Station, Work in Area, Load/Unload in Area, Wake Up, Filter, Filter Tool,
  Provide/Accept/Request Items, Drop Items In Pipe, Allow Craft, Request Needed Items, Accept/Provide Fluids,
  Forbid/Force Robot) have no `trigger`/`action` line, so `PageEntryStatement#iterateAllDefault` emits them flat
  under Triggers/Actions with no subchapter — the exact failure mode the timer/pulsar comments in `guide.txt`
  describe.
- **No recipe renderer for the robot crafting chain.** `lib/client/guide/parts/recipe/` has Crafting, Assembly
  and Smelting only. `<recipes_usages>` on a board or robot page cannot show Programming Table (board ← blank
  board) or Integration Table (robot ← blank robot + board) steps. JEI already has an Integration Table category
  (`8eb5038a6`), so the recipe model to mirror exists; the guide needs `GuideIntegration*` / `GuideProgramming*`
  triples modelled on `GuideAssembly*`.
- Stale in passing: `item/guide.md` still points players at mod-buildcraft.com forums and the upstream
  BuildCraft/BuildCraft issue tracker.

## Decision 1 — how robots go in

**Recommended: one top-level "Robots" chapter for the system, statements stay under Triggers/Actions.**
Robots are a workflow (blank robot → board programmed at the Programming Table → combined at the Integration
Table → placed on a Docking Station on a pipe → driven by gate actions → bounded by a Zone Planner map). Scattering
that over Blocks/Automation, Items/Pluggables, Items/?, Triggers and Actions (the "consistent with today's
taxonomy" option) loses the only thing a new player needs: the order of operations. A second book
(the `buildcraftunofficial:config` precedent) is wrong too — a second item, split search, and that precedent is
dev-facing.

Layout:

- **Robots › Getting Started** — one concept page (`concept/robots.md`): what a robot is, the lifecycle above,
  power (stations charge from their pipe), what "linked" and "reserved" mean, and the gate-tier rule for
  Work in Area. Cross-links to every page below. Written first; everything else can stub.
- **Robots › Boards** — 18 leaves, one per board (Picker, Carrier, Tank, Lumberjack, Harvester, Miner, Planter,
  Farmer, Pump, Knight, Butcher, Delivery, Leaf Cutter, Shovelman, Bomber, Stripes, Builder, Empty). The
  `buildcraft.boardRobot*.desc` lang strings are the one-line summaries. Each page: job, which station actions it
  needs (Provide Items for tools, Work in Area for a zone), what it carries home, hazards (Bomber). Entry icon =
  a programmed board stack; a Java-built category entry like `addFillerPatternsCategory` can enumerate boards from
  the board registry so a new board auto-files.
- **Robots › Stations and Tables** — Docking Station, Programming Table, Integration Table, Zone Planner,
  Requester. Existing `block_auto`/`plug` aliases can be re-pointed with a `robot` type; the Assembly Table stays
  under Automation (it is a silicon machine, not a robot one).
- **Robots › Items** — Robot, Redstone Board (blank). Both pages lean on the recipe renderer above.
- **Triggers › Robots** and **Actions › Robots / Docking Stations** — the 20 statements, via new `trigger`/`action`
  lines with subtypes `robot` and `station`. Two subtypes on the Actions side because the station actions (what a
  station offers) and the robot actions (what a robot is told) are different mental objects. Auto-stub bodies are
  acceptable at first; the statement names are self-describing.

Cost: ~1 day Java for the two recipe renderers; the rest is `guide.txt`, two dozen lang keys, and prose.
Prose volume is the real cost (≈ 26 pages, 18 of them short).

## Decision 2 — restructure the table of contents?

**Recommended: yes, but as a new default sort order, not a rewrite of the existing one.**

What is wrong with the current tree: it is organised by Java registry type (block / item / pipe / statement),
not by what the player is trying to do. The laser-power system is split four ways (Blocks/Laser Power holds one
entry; the Assembly and Integration Tables sit in Blocks/Automation; chipsets in Items/Chipsets; the gate recipes
on the gate page). Engines sit in Blocks, kinesis pipes in Pipes, MJ triggers in Triggers/Power Delivery.
Blocks/Automation is a grab-bag of three systems (builders, silicon crafting, item logistics). Six subchapters
have one member (Laser Power, Basic, Structure, Wires, Fluid Transport trigger, Item Transport trigger), which is
the usual sign that the axis is wrong. And Triggers/Actions duplicate the same subtype list twice.

What is right about it: for a player who already knows BuildCraft it is a fast reference ("which fluid pipe"),
the pipe banding is well curated, and it is what every BC8 player has seen since 1.12.2.

So keep it and add a second axis. `JsonTypeTags` carries domain/type/subType; add a fourth tag
(`ETypeTag.SYSTEM`, JSON `tag_system`, lang `buildcraft.guide.chapter.system.<x>`) and replace the dead
"Sort By Mod" order with **Sort By System** (system → subtype), making it the default. Sort By Type keeps the
current tree untouched. Proposed systems: Getting Started · Power (engines, kinesis pipes, FE pipes, power
triggers/actions, Pipe Power Adaptor) · Transport (item/fluid pipes, pluggables, wires, lenses, filters) · Gates
and Logic (gates, chipsets, triggers and actions as subchapters, pipe signals) · Building (Architect, Builder,
Filler, Library, markers, paperwork, templates) · Mining and Fluids (Quarry, Mining Well, Pump, Tank, Flood Gate,
Distiller, Heat Exchanger, oil/fuel pages) · Silicon (Assembly, Integration and Programming Tables, lasers) ·
Robots. Java-built category entries and the `iterateAllDefault` fall-throughs file by type today
(`GuideManager#addCategoryEntries` and the per-sort-order filing comment above it); they need a system tag too,
which is the only non-trivial code in this option. `GuideChapterTitleWrapTest` and the `line8` smoke scenario
(contents page, small-screen overlay) are the regression nets.

Cheaper fallback if the second axis is rejected: keep type/subtype, add Robots as a top-level type, fold the
singleton subchapters (Laser → a "Silicon" subtype with the tables; Basic → Tools; Structure → Item Transport),
and leave Triggers/Actions as they are. Zero Java.

## Open questions for the user

1. Second axis (Sort By System as default) vs. the cheaper type-only fallback.
2. Board pages: 18 authored leaves, or one "Boards" category page listing all with a paragraph each (the Pipe
   Signals / Filler Patterns pattern)? Authored leaves are recommended; they give `<link>` targets and recipes.
3. Whether the Getting Started page should also replace the stale `item/guide.md` front matter.
