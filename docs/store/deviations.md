# BuildCraft Redux vs original BuildCraft: what's different

Compiled 2026-10-31 from five history sweeps: every port commit (2026-03 → 2026-10, about 2,400 non-merge
commits), every past version of `changelog.md`, the release notes in `BuildCraftVersions.json`, the design
docs and the config classes. Claims were checked against the original 1.12.2 source
(`upstream/8.0.x-1.12.2`, `common/`) where the scouts could; robots there exist only in the uncompiled
`src_old_license/`, so 1.12.2 shipped none.

Baseline is **BuildCraft 8.0.x for Minecraft 1.12.2**. Tags:
- **new**: 1.12.2 had nothing like it.
- **7.1**: brought back from BuildCraft 7.1.x (Minecraft 1.7.10).
- **reworked**: 1.12.2 had it; Redux changes how it works.
- **fix**: a bug the original 1.12.2 also had.
- **(?)**: the commit or changelog says so, but the 1.12.2 behaviour was not confirmed in source. Check
  before putting it on the store page.

Fixes for bugs the port itself introduced, refactors, build work and per-Minecraft-version porting are left
out. Commit shas are kept so each line can be traced.

---

## Highlights

- **Robots are back** (7.1): robots, 17 boards, Docking Stations, 22 gate statements, Requester and
  Delivery robot, Programming and Integration Tables, a Robots creative tab and Guide Book chapter.
- **Oil reworked**: rebuilt surface generation with biome tiers, Nether oil (searing, with Crimson Forest
  springs), oils with weight (heavy oil sinks through water), per-fuel burn speeds, exploding gas.
- **Combustion Engine fuel temperature**: hot fuel gives 25% more power, searing fuel 25% less and runs hot.
- **The Help system is finished**: help ledgers with per-slot text on nearly every machine screen, with
  slots and tanks highlighted as you hover.
- **Guide Book overhaul**: Sort by System contents, ~25 written or rewritten pages, a Robots chapter with
  tutorials, recipe display, GuideME export.
- **Quarry and Mining Well upgrades**: anvil enchantments (Silk Touch, Fortune, Efficiency), a Pump on top
  drains lava/oil in the way, redstone pauses them.
- **Builder and Filler**: Filler preview replaces the Filler Planner, Builder stall diagnostics, marker
  volume resizing with the new Volume Wrench.
- **Waterlogging everywhere**: pipes, markers, engines, tanks, lasers, machines and mining tubes.
- **Respects land claims**: every world-changing machine and every robot asks protection mods first, acting
  as its owner or deployer.
- **JEI and REI** on every version, Forge Energy across most machines, other mods' wrenches, AE2.

---

## Power & Engines

- Combustion Engine fuel temperature: hot fuel ×1.25 power, searing fuel ×0.75 and 1.5× heat, needs extra coolant — 59ed74a25 — new
- Blue Ice works as Combustion Engine coolant (2.5×, alongside ice 1.5× and packed ice 2×) — 27aa6d84c — new
- Engine overheat explosions are optional (`canEnginesExplode`, default off) — e0c22c142 — new
- One wrench action clears an overheated engine; wrench handling unified across engines and the Distiller — e0c22c142, 2902eecef — reworked (?)
- Stirling Engine recovers from overheat on its own and accepts any stone-tier block in its recipe — e14a6ba06, 6c36156a4 — reworked (?)
- Right-clicking an engine with a pipe only places pipes that connect to it — 394b5cd33 — reworked (?)
- RF Engine and MJ Dynamo are always craftable (1.12.2 hid both behind config, default off) — 6610f95ea — reworked
- Forge Energy accepted by the Pump, Mining Well, Quarry, Distiller, Chute, Laser, Filler, Builder and Auto Workbench, following the power-mode setting (1.12.2: engines, dynamo and RF pipes only) — 375ff4d4a, b06ae0a24, ef30e3b46 — new
- Searing fluids vent steam from their surface (`searingFluidSteam`) — 7b237246f — new

## Oil & Fuels

- Surface oil generation rebuilt: rich land (desert, badlands incl. Eroded Badlands) gets pools with springs and tall spouts, deep oceans always have a spring, shallow oceans ~15× rarer, standard/reduced/mountainous biome tiers, trees cleared around pools, rivers excluded — 73ec8793d, 7d874b2c0, f28e396ad — reworked
- Oil generates in the Nether, always searing: lava-sea slicks, buried pockets, cavern spouts, infinite springs in Crimson Forests (Nether removed from the default `excludedDimensions`) — 59ed74a25 — new
- Oil generation runs as a worldgen feature instead of on chunk load — 4ea8c38b2 — reworked
- Oils have weight: heavy oil sinks through and displaces water, lighter fluids float and spread over oceans, surfaces bob, heavy oil sinks below waterlogged blocks without deleting them — c19e80f76, 606fec9f1, a07ffef64, 4bf070304, 006cd400e, ab3a66b3b, 6f94f0f6a — new (?)
- Each oil and fuel burns at its own speed: residue never, crude and heavy slowly and long, light fuels fast; heating speeds burning (1.12.2 had uniform flammability) — 8c1f6b4bd, cca51c0d8, 992382a88 — reworked
- Gaseous fuels explode when lit (fire, lava, a lit campfire) and on their own in the Nether; blasts chain to nearby gas (`gasExplodes`) — 8c1f6b4bd, 8b0e8422d — 7.1
- Residue is as thick as crude and can't be swum in — 31cc84501 — fix (?)
- Falling into oil breaks your fall; animals don't swim in oil (needs a NeoForge build that exposes the hooks: 26.x yes, 1.21.x no) — 4f7df3fb2 — new
- Pump facing a single fluid source places its tube (1.12.2 never did) — ea7003ab7 — fix
- Pump searches straight down through air, tubes and fluid, drills past water to oil, idles on a dry spring until it refills — bf6780055 — fix (?)

## Pipes & Transport

- Pipes are waterloggable — 28e33cf3d — new
- Each pipe material has its own sound (wood creaks, iron/gold/stripes ring, emzuli chimes, stone, sandstone and clay their own); Redstone/Stirling engines, markers and quarry frames too — eb87aa5e8 — new
- Fluid pipes fill cauldrons and wooden fluid pipes drain them; item pipes feed composters — 9fc3c2156 — new
- The vanilla Brush cleans painted pipes and blocks — 12e251a68 — new
- Paintbrush also recolours glazed terracotta, candles and beds (26.2+) — ad5e2561c, 479231e68 — new
- Pipe painting spends one brush use per pipe — cd0b1cbea — reworked (?)
- Placement preview: a held gate, plug, lens or wire outlines exactly the face it will occupy — c8aab2c87, 67af20560 — new
- Pipe wires are easier to click — 6bca47871 — reworked (?)
- Mobs no longer path through pipes, frames, tanks, tubes or laser tables — bf9ddfd8d — new
- Facades cast soft corner shadows like the blocks they copy; a pipe with a solid facade shades its neighbours — 5d588b867, 3ffe560e6 — new
- Facades toggle between solid and hollow by crafting one alone — 5274f79c0 — new (?)
- Identical-looking facades de-duplicated (cuts 1,000+ duplicates) — 030f99479, c5f941e96 — new
- Filter lenses actually block differently coloured items — f28e396ad — fix
- Chute, Filtered Buffer and Requester drive comparators (8.0 dropped it for Chute and Buffer) — 5093ff96e — 7.1
- RF pipe upgrade recipes for every tier plus a reverse recipe back to MJ — 6610f95ea — new (?)
- `disableRfPipe` removes the RF pipe recipes too, so disabled pipes can't be crafted — e12ebb5da — reworked
- Stone, cobblestone and quartz pipes take any block of their type via tags; Stone Pipes need true stone, not granite — 57eec3c3d, ef2def321 — reworked
- Wooden Diamond fluid pipe actually filters — ca38b60c3 — fix (?)
- Item pipes connect to the Auto Workbench, Assembly, Integration and Advanced Crafting tables — 29af443fe — new (?)

## Gates & Logic

- "Filler Action: Pattern" gate action sets the Filler's pattern and locks its GUI — 62e5d96f9 — fix
- Logic Gates guide page lists every gate recipe with cycling panels — 2a96bcb91 — new
- Robot and station statements: see Robots

## Building (Builder, Filler, Architect Table, Library, Replacer, markers)

- Filler Preview toggle (eye button): see-through cubes over every spot left to fill, visible to nearby players — 247855eb1, d6c13664e — new
- Filler Planner removed; Fillers that a planner drove take over its box and pattern on first load — 247855eb1, e82e8a6d9, 3d235d0ac — reworked
- Filler changes (pattern, parameters, invert, box) reach watching players immediately — changelog 2026.3.0 — reworked (?)
- Builder stall diagnostics: holding a Marker or Volume Wrench outlines every skipped cell (red can't place / protected, yellow missing items, blue fluid in the way); "Left to Place" explains the stall — ef43a0fc6, 492c301a2, 88c22244f — new
- Builder Container Contents toggle (place containers filled or empty) — 904465fc4 — new
- Builder fluid mode button (keep fluid / clear / replace); Clear mode absorbs cleared sources into its tanks — da61dadac, 22f998be7 — new (?)
- Builder and Filler reach full speed at half charge (1.12.2 capped them at half speed) — f42f402b8 — fix
- Builders cope with water: place beside it, break waterlogged blocks, finish builds with fluid in them, no longer stall beside fluid they can't remove — 0234a720c, 475c9769f, 13c05b786 and others — fix (?)
- Filler stops after one cycle unless in Loop mode — da6dfdaa7 — fix (?)
- Filler's Fill pattern clears grass, snow layers and vines in its box — 2d120e82b — fix (?)
- Builder and Filler animate each placement as a small flying item — 474197b1c — new (?)
- Path-marker Builders show the route they'll follow — a973a8906 — new (?)
- Marker volumes resize: drag a corner with the Volume Wrench, confirm, and a floating box replaces the markers — 38ecce436, 993423698 — new
- A Filler or Architect Table placed against markers keeps the area as a box the Volume Wrench can resize — ace7763eb, 83274f7c9 — new
- Marker laser length in blocks shown under the crosshair — 8bf174c65 — new
- Volume Markers float in air and survive losing their anchor block — 41aad67e5 — reworked (?)
- Markers are waterloggable; no BuildCraft block washes away — b935498b6 — new
- Replacer finished (1.12.2's shipped unfinished) and an empty replacement slot turns Replace into Remove — 8cbe1fbb7, 2874503d5 — reworked
- Blueprints and Templates craft 2 and 4 (1.12.2: 1 each) — d3ffc1861 — reworked
- Blueprint and template 3D previews in tooltips and the Architect Table, including pipes and their pluggables; templates draw as a ghost shell — 52c039d4f, d75556b35, e733466e1 — reworked (?)
- Architect Table refuses areas too large for a blueprint to load — changelog 2026.2.1 — new (?)
- Electronic Library: scrolling list (1.7.10 scrollbar), sorted alphanumerically — 7ff237206, 8a5229d0b, 6b006eaf6 — 7.1
- Electronic Library upload cap (`libraryMaxUploadMb`, default 32 MB, max 128) — 29b9beed5 — new
- Players can't fetch other players' blueprints from a server library — changelog 2026.2.1 — new
- Single schematic clicks keep the clicked block; schematics aren't consumed — 6b4b52832 — fix (?)
- Filler, Quarry and Architect Table status lights wired (1.12.2 shipped the Filler art but never lit it) — d0710a5d9, 37fde81c0, 31548e181 — new (?)

## Mining & Fluids (Quarry, Pump, Mining Well, Flood Gate, Tank)

- Quarries and Mining Wells take Silk Touch, Fortune and Efficiency from an anvil (`machineEnchantments`); enchantments and anvil names survive breaking — 71ec2e968, a97ad570f — new
- A Pump on top of a Mining Well drains the lava or oil in its way, borrowing the well's power — be2a677ce, 995363035 — new
- A Pump on top of a Quarry drains liquid the sweep skipped; the Quarry goes back for those blocks; pump light turns blue — 6f00dbfdf, 43d94796f — new
- Redstone pauses the Pump (7.1), Mining Well and Quarry (new) without losing progress or spending power — 7422809c5 — new / 7.1
- Quarries, Mining Wells and Pumps handle waterlogged blocks (mine them, un-waterlog them) instead of skipping or deleting them; tubes are waterloggable — 0234a720c, 12d286d00, 376b44017, bc7d3da2c — new
- Mining Well skips flowing water instead of looping on it (1.12.2 had the loop) — cd3bd9cfc — fix
- Quarry stops drawing power when finished; idle Quarries and Pumps over huge pools stop costing server time — 0dbe42beb, a1561910d, c61088af2 — reworked
- Quarry repairs a frame gap once whatever blocked it is gone; cells it can never mine are skipped — changelog 2026.2.1, 3723686dc — new (?)
- Quarries don't freeze forever on bedrock or protected blocks in their frame — changelog 2026.2.0 — fix (?)
- Quarry mining floor anchored to the quarry block; config changes apply to placed quarries — 31c37644c — reworked (?)
- Flood Gate fills from a bounded search instead of scanning a whole lake — changelog 2026.2.1 — reworked (?)
- Tanks, Distillers, Heat Exchangers, Chutes, lasers and quarry frames waterlog — b4e218ff8 — new
- Tank tooltips: "Empty 16,000 mB Tank", units follow display settings — 1ab86e494, d255e8092 — reworked
- Tool tiers enforced per machine (Quarry diamond, Mining Well iron…) — 22f998be7, b86e2b255 — reworked (?)
- Distiller lights the arrow of whichever tank is blocking production — c34ab67f5 — reworked

## Silicon & Lasers

- Lasers work underwater and fire through water with bubbles — b4e218ff8 — new
- Laser targeting option: 1.12.2 line-of-sight cone (default) or the 7.1 five-block box (`laserTargetingBehavior`) — 9e8aa9ad5 — 7.1
- Laser hitbox matches its model — cf6dfc2ba — reworked
- Advanced Crafting Table: cycle-output button offers the recipe in other woods/materials and rewrites the blueprint; a substitute-materials toggle (`craftingVariantTags`) — 915a72ee9, 38ba71cc8 — new
- Auto Workbench and Advanced Crafting Table can switch between conflicting recipes — changelog 2026.1.0-rc4 — new
- Assembly Table can pause and resume a recipe, keeping stored power — 838916487 — reworked (?)
- Programming and Integration Tables shipped with survival recipes — ba2ba3f92, 6a1fd54d5 — 7.1

## Robots (1.12.2 shipped none)

- Robot entity, item and renderer: MJ battery, charge shown on the body and item, smooth movement, 7.1 skins and red exhaust — fbeba3408, 90a797f37, e8662c83e — 7.1
- Docking Station pluggable on any pipe: charges robots through the pipe (FE too, on RF pipes), available/reserved/linked states; open to any player again — 22d061628, be06cc45c, 9438f4d14 — 7.1
- 17 boards: Picker, Carrier, Tank, Lumberjack, Harvester, Miner, Planter, Farmer, Pump, Butcher, Knight, Leaf Cutter, Shovelman, Delivery, Bomber, Stripes, Builder — 5ebd86138, be95477ef, a04b70811, c0945fea8 — 7.1
- 22 robot and station gate statements (Goto Station, Work in Area, Load/Unload, Wake Up, Sleep, Filter, Request/Provide/Accept Items and Fluids, Forbid/Force Robot…) with 7.1 icons — 629bf417f, 54b381073 — 7.1
- Requester block and Delivery robot — a04b70811 — 7.1
- Robot recipe costs a Redstone Crystal; blank robots stack to 16 and refuse to place unprogrammed; name tags name robots — 989751aa0, 79c4478e9, f7b566042 — 7.1
- Behaviour audited against a running 7.1.27 client (16 differences fixed) — docs/robotics-gameplay-audit.md — 7.1
- Robots remember who deployed them: they obey protection as that player, and kills credit that player — d07d97a15, 24ba45333 — new
- Butcher robots spare pets, tamed horses and named animals (7.1 killed any animal) — cd175a050 — new
- Robot searches are bounded and never load chunks — 54624217d, 1c701ad83 — 7.1 / new
- Zone Planner: live 3D map you can paint on, terrain shown past render distance from server memory, survival recipe (1.12.2's recipe was commented out), map data stored on the world — d8a76157c, df4b7427b, fd15f932e — reworked
- BuildCraft Robots creative tab — 1d884bc6c — 7.1

## Guide Book & Help

- Help system finished: help ledgers on nearly every machine screen with per-slot/per-tank text, highlights on hover, 26+ new help topics; a warning icon where a screen has none — 3a605220c, 98a9b37ea, 043303672, d66a656da — reworked
- Ledgers word-wrap to the screen and remember whether they were open — 9db508c6f, 32214faa7 — new (?)
- Sort by System is the default contents order (Getting Started, Power, Transport, Gates and Logic, Building, Mining and Fluids, Silicon, Robots), with Sort by Type and alphabetical — c8cba104e, 440b2a09a — reworked
- ~25 pages written or rewritten (pipes, engines, Filler, Builder, Library, Replacer, chipsets, gates…) — 902d139f7, b3e61aada and others — new
- Robots chapter: overview, every robot, block and board, statement pages, Programming/Integration Table steps — e58a0835d, a4f71f135 — new
- Filler patterns and Emzuli presets on one page each; 16 paint actions collapsed into one entry — 1093dbf96, 425280cb5 — reworked
- Gate pages show crafting and Assembly recipes; recipes display on multiplayer servers — 2a96bcb91 — reworked
- Creative-only entries hidden from survival players — f78611941 — new
- Web links open through vanilla's confirm screen; links wrapping onto the next page clickable on both — 40cffc61d, a73b282bf — new
- Exported as a GuideME guide when GuideME is installed — ecdaf83be — new
- Guide Note item removed (never worked, even in 1.12.2) — 0e00546fb — reworked

## GUI & UX

- Number display options: thousands/decimal separators, abbreviated large numbers (1.2k, 1.5M; on by default), flow per second/tick/both, full unit names, fluids in buckets above 1,000 mB — b3d45a28e, b723cec04, 98dfcb456, 0a9a59b33, fe4aff444 — new
- Colour-blind mode follows Minecraft's High Contrast setting by default — 8cbcd6980 — reworked
- Machine items face you in hand like a furnace — 753854d8a — reworked
- All buttons are vanilla buttons, so resource packs restyle them — ff197a3d2 — reworked
- Spectators can view BuildCraft screens — c87a23efe — new
- List filter: Match Info ledger, ghost previews, Precise / By-Type / By-Material modes — 7db8d4e2a — new (?)
- New tooltips for engines, Quarry, Miner, markers and builder tools — 2ae5772b3, 27575f5cd — new (?)

## Config (options 1.12.2 didn't have)

- `machineEnchantments`, `canEnginesExplode`, `allowForeignWrenches`, `gasExplodes`, `netherSpringBiomes`, the per-tier oil biome lists, `searingFluidSteam`, `useRfNaming`, the number-format options, `laserTargetingBehavior`, `disableFacadeGeneration`, `craftingVariantTags`, `libraryMaxUploadMb`
- Display options moved to a separate client config; dead options removed — 5129e2ae0, f4c6e238b — reworked

## Mod compatibility

- JEI (1.12.2 snapshot has no JEI plugin): every machine's recipes, fuels and coolants, Assembly/Heat Exchanger/Programming/Integration categories, "+" transfer, clickable gauges, ghost drag-and-drop, subtype splits, ledgers move JEI aside, facades hidden from search — e61de42d7, b2a7a6408, 44a9b486a, 2940069c3, c275495a0 and others — new
- REI on every version at JEI parity — 0519dce04, 491e762d9 — new
- Other mods' wrenches (`c:tools/wrench`, e.g. Mekanism Configurator) work on BuildCraft blocks; BuildCraft's wrench works on theirs — 6b60fd6cb — new
- FE pipes power AE2's Energy Acceptor — 16f358ead — new
- Iron Tanks Glass Tank support — changelog 2026.1.0-rc4 — new
- Gears publish `c:gears/*` tags so other mods' gears work in BuildCraft recipes — 22f998be7 — new (?)
- Recipe-book unlocks for every module, gated on what you've picked up — d700b1e73, 056acda73 — new

## Performance

- Long pipe networks no longer drag the server tick; pipes cache collision shapes — 8a46f716c — reworked
- Machines push state by packet instead of rebuilding the chunk; logic gates and fluid pipes stop forcing chunk rebuilds — c919ee0c7, 9e65948d5, 156003df4 — reworked
- Faster robot pathfinding and pipe wires; Zone Planner draws only what's in view — changelog 2026.2.1, 19788e872 — reworked
- JEI startup 2.3 s → 0.8 s, Guide Book and first-GUI hitches removed — c53eb48ac, 5ea7dd580 — reworked

## Server & protection

- Pumps, Quarries (frames and tubes), Mining Wells, Builders, Fillers, the Flood Gate, Stripes pipes and robots ask protection mods before acting, as their owner; refused spots are skipped and retried later (1.12.2 only checked some breaks) — 553d8ed4b, 3e32f70c7, 39155ed48, da6767d5b and others — new (?)
- A Flood Gate can't pour lava or gas into a neighbour's claim — 3e32f70c7 — new
- Pumps and robots never load or generate chunks — changelog 2026.2.1 — new
- `/setblock`, `/fill` and `/clone` wipe machines instead of spilling contents — changelog 2026.2.1 — reworked

## Other

- Wrench tiers: Basic Wrench, Marker Wrench (the old Marker Connector, now also a wrench) and the diamond Volume Wrench — 4483d41ca — new
- Wrenches are real tools with durability, mended with iron or diamond, mining BuildCraft blocks at tool speed — 37c4e932a — new
- Some zombies and husks carry a Basic Wrench and may drop a worn one — 37c4e932a — new
- Advancements that could never be earned in 1.12.2 now work (Pipe Fanatic, Ice Cool, Powering Up, Fine Riches, Sticky Dipping, Lava Power, Destroying the World, Building for the Future, Paving the Way, Refine and Redefine, Heating and Distilling, Colorful Electrician, All Plugged Up and more), several with progress counters — fb9b4000d, ce32bdf6d, 29d039e5a and others — fix
- Power Tester (1.12.2 dev-only) available in creative — 327aa2b86 — reworked
- Worlds from older BuildCraft Unofficial versions keep their blocks and items through ID renames — a340d83da — new
- Simplified Chinese translation — 7dd529549, b5bb133f0 — new

---

## Don't claim these (the history says otherwise)

- **Replacer is not new.** 1.12.2 has a working `BlockReplacer`/`TileReplacer`; two commit bodies call it new. It's reworked.
- **Oil burning is not new.** 1.12.2 had flammable oils (`can_burn`, default on); what's new is per-fuel volatility.
- **Stripes pipe item use, minecart breaking and pipe extension already existed in 1.12.2**, though the 2026.2.1 changelog calls them 1.7.10 features. They were port fixes.
- **RF pipes existed in 1.12.2** (behind `disable_rf_pipe`); `docs/gameplay-differences-1.7.10-vs-1.12.2.md` §4 says otherwise and is wrong.
- **Abbreviated numbers did not exist in 1.12.2**, despite the 2026.1.0-rc4 line saying they match it.
- **Redstone engines not feeding FE machines** was already fixed in 1.12.2; the port regressed and re-fixed it.
- **Auto Workbench recipe book** existed in 1.12.2 (`GuiAutoCraftItems`).
- **Zone Planner and Programming Table** existed in 1.12.2; the Zone Planner is reworked, the tables' robot role is 7.1.
- Robots respecting claims is new, but **1.12.2 already fired break events** for some machine breaks; say "every machine and robot asks", not "the first BuildCraft to respect claims".
