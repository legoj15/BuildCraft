# Guide book: contents by system, and the Robots chapter

Reference for the guide book's contents tree and the robotics pages. Decided and shipped 2026-10-15
(the proposal this file started as is in git history before that date). Read this before adding a guide
entry, a system or a subtype.

## The two axes

Every entry in `compat/buildcraft/guide.txt` carries three tags: `tag_system`, `tag_type`, `tag_subtype`.
The contents page offers three orders (`GuiGuide.SORTING_TYPES`, first is the default):

| Button | Nesting | Purpose |
|---|---|---|
| Sort By System (default) | system → subtype | what the player is trying to do |
| Sort By Type | type → subtype | the registry-shaped reference tree 1.12.2 shipped |
| Sort Alphabetically | flat | search-style |

Sort By Mod is gone (one mod, so it collapsed to one node); its button art was renamed to `sort_system*`
and still shows the old glyph — an art pass is pending.

**Systems** (`buildcraft.guide.chapter.system.<key>`, fixed order in `GuideManager`; unknown keys sort after,
A–Z): `getting_started`, `power`, `transport`, `logic` (Gates and Logic), `building`, `mining_fluids`,
`silicon`, `robots`. The subtype is shared by both axes, so pick it as "what kind of thing within its
system": Engines, Power Transport, Pluggables, Builders, Markers, Crafting Tables, Boards, Docking
Stations… Statements share subtypes with the items they belong to (engine-stage triggers sit in Engines
beside the engines; station actions in Docking Stations beside the station), which is deliberate — a
system chapter lists everything about one subject in one place, icons telling statements from items.

Mechanics worth knowing:
- `JsonTypeTags` holds all four tags; an entry with no system files at the root of the system view
  (never under "Unknown"). Auto-iterated statements with no `guide.txt` line get system `logic`.
- Extra entries (fall-through statements, "Other Items", category pages) file through
  `GuideManager#placeExtraEntryInOrder`; in an order with no TYPE level an entry without a subtype uses
  its category title as the subtype level, which is why "Other Items" and stray statements still group.
- Category pages (`registerCategory`) take a system per chapter placement.
- A system chapter's position comes from an explicit `ContentsNode` sort index, not from its members'
  weights; entry weights (`_s` aliases, `sort`) still order siblings inside a chapter and float a chapter
  within its subtype level (the Robots overview carries −10 so it leads its chapter).
- `GuideTxtSystemTagTest` runs the real JSON-INSN interpreter over `guide.txt`/`util.txt` and pins: every
  main-book entry has one of the eight systems, every system/subtype key exists in `en_us` and `zh_cn`,
  the robot content and statements are registered, no retired subtype is used, aliases are defined once.

## Authoring rules learned the hard way

- **Links to statements go by page, never by id.** `<link to="buildcraft:robot.sleep" type="statement"/>`
  is not a supported link form (`PageEntryStatement` has no `createLink`) and prints as raw red text.
  Register the statement with a `trigger`/`action` line and link `buildcraftunofficial:trigger/<name>`.
  `type="item_stack"` links by item id do work (`buildcraftunofficial:map_location`).
- **A board is not an item.** Every board is the one `redstone_board` item with a board id in its data,
  and the JSON `stack` field strips data, so board pages are `external` entries (`board "<name>"` alias
  → `board/<name>.md`, title `buildcraft.guide.page.board_<name>`). External entries draw no icon — the
  Boards subchapter is text-only until `PageEntryExternal` learns an optional icon stack.
- **Concept pages** use the `concept "<sys>" "<subtype>" "<name>" "<sort>"` alias (external, titled through
  `buildcraft.guide.page.<name>`).
- `<recipes_usages>` shows Programming Table and Integration Table steps (robotics registers
  `GuideProgrammingRecipes` / `GuideIntegrationRecipes` through `RecipeLookupHelper.register`); a page can
  pin one with `<recipe id="buildcraftunofficial:boardRobotMiner" type="programming"/>` or
  `type="integration"`.
- Smoke: `scripts/smoke/scenarios/guide-robots.json` walks the three sort views, the chapter overlay, the
  Robots chapter and the Robot/Board/Station pages. Sort buttons and contents entries are not bridge
  widgets — click them by GUI coordinates; the search tab is `x 58, y 8`, first result `x 130, y 56`.

## Robots chapter

Pages under `guide/en_us/`: `concept/robots.md` (Getting Started with Robots), `item/robot.md`,
`item/redstone_board.md`, `item/robot_station.md`, `block/programming_table.md`,
`block/integration_table.md` (filed under Silicon), `block/zone_planner.md`, `block/requester.md`,
`board/<18>.md`, and the 18 statement pages `trigger/robot_*|station_*.md`, `action/robot_*|station_*.md`.
Facts were taken from the port's code and `docs/robotics-gameplay-audit.md`; keep them in step when
robot behaviour changes (battery 10,000 / safety 2,000 MJ; board costs 800 / 3,200 / 12,800 / 51,200;
Quartz 1 and Diamond 3 action parameters; Provide Items only through a Wooden pipe's extraction face;
home links need two clear cells; the Request Needed Items action is inert, as in 7.1.x).

## Delegation log (2026-10-15)

- impl-opus — Java axis (tags, orders, filing, button rename, 17 tests): clean, one round.
- impl (Sonnet) — guide.txt retag + robot registrations + guard test: clean; found 18 statements not 20.
- impl-opus — Programming/Integration recipe renderers + in-client check: clean, one round.
- reviewer (Haiku) — found the `type="statement"` link bug (already caught in-client), two prose
  overclaims, two missing guard tests; two of its "likely wrong" prose items were wrong calls
  (Planter light rule and the two-cell home approach are real).
- impl (Sonnet) — link-target and id guard tests + three nits: clean; the link test exposed two pre-existing
  dead links and the SimpleScript import bug (fixed here, pinned by `SimpleScriptImportTest`).
- opencode triad (Qwen 27B + GLM flash; Flash-Next deferred) — confirmed: Miner pick-tier prose wrong,
  gate-free recharge misstated, Forbid/Force and Robot In Station parameter claims wrong, Set Power Limit
  category orphaned a "Power › Item Transport" chapter, stale crafting-recipes comment, no title-key lang test.
  Refuted: the lookup "returns all steps" claim (no such fallback), the "no system/subtype lang test" claim.
  Noted, not done: zh_cn lacks the robot item and board display names (pre-existing translation gap).
- PENDING: the Flash-Next (dgx) leg of triad `review-the-committed-change-20261015-211942-2f0a` was still running at session end (deferred); the opencode notify hook delivers it at the next prompt, or collect it with `opencode_wait_group`. Adjudicate against fb195ecb2.
