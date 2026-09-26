# Upstream 7.1.x tail sweep (2026-09-26)

The 51 non-merge commits in `upstream/8.0.x-1.12.2..upstream/7.1.x` (7.1.20 → 7.1.27, 2017-04 → 2025-01) never reached any 8.0.x branch. Every one was classified against the port on 2026-09-26; none needed a direct port. COVERED = the port is already correct, N/A = not applicable to a modern NeoForge port, PORTED = changed in that pass. The adjacent comparator gap (next to `4fb401ac7`) was ported: Tank, Chute, Filtered Buffer and Requester give comparator output through `IComparatorOutputTile`. The `upstream/6.4.x` tail (22 commits) was not swept; treat it as input to client-driven gameplay audits.

| sha | subject | verdict | reason |
|---|---|---|---|
| b84395c85 | add missing configuration lang keys | N/A | 1.7.10 config-GUI keys; optional emerald tooltip rewording is a followup. |
| bef282dee | gate copier: configuration not information | COVERED | en_us chat.gateCopier.* already says configuration. |
| 6649f8a90 | update GitHub CI | N/A | Build. |
| a04407404 | static classes, dedupe empty schematic arrays | N/A | 1.7.10 hygiene. |
| ab37631c2 | BuildCraft 7.1.27 | N/A | Release bump. |
| 4fb401ac7 | RemoteIO crash (comparator override guard) | N/A + PORTED adjacent | There is no generic inventory comparator override to guard. Adjacent gap: 1.7.10 comparator output on the Tank (also 8.0.x), Chute, Filtered Buffer and Requester was missing in the port. All four are now PORTED through IComparatorOutputTile, with a zero-counted-slot guard, plus the ItemHandlerManager callback fix. |
| 2dd907325 | #4712 gates missing from creative tab | COVERED | BCSilicon tab lists every gate variant. |
| 20fe67231 | pipe render/pluggable state API (Logistics Pipes) | N/A | 1.7.10 TileGenericPipe API. |
| 3287b9a2d | 7.1.26 Java 6 support | N/A | Toolchain. |
| ccc5cf956 | BuildCraft 7.1.26 (+refinery side.isServer) | N/A | Release; no refinery command packets. |
| ef0e758db | update build tools | N/A | Build. |
| f541cb0cd | packet security improvements | COVERED (structural) | The NeoForge registrar enforces payload direction; the removed exploit handlers have no port equivalent; server-bound payloads validate their input (container id/stillValid, 8-block distance, content hash). |
| 0848496eb | packet sender thread improvements | N/A | No custom sender thread. |
| d81a3dd19 | update versions.txt | N/A | Release metadata. |
| 365286314 | BuildCraft 7.1.25 | N/A | Release. |
| 8a6b12670 | #4688 package tooltip crash with NEI | N/A | No Package item. |
| cc1893448 | alpha-pass render resilience | N/A | 1.7.10 renderer. |
| 2ba3baf43 | #4640 creative tab crash with disabled items | N/A | No per-item disabling. |
| f7ea40193 | alpha-pass bug in PipeRendererWorld | N/A | 1.7.10 renderer. |
| 3b8340afd | Gradle 6, GitHub Actions | N/A | Build. |
| bc01c8b4d | fix module building | N/A | Build. |
| 39257fbc1 | change ForgeGradle forks | N/A | Build. |
| c31480986 | BuildCraft 7.1.24 | N/A | Release. |
| d891e3b76 | IMC to forbid blocks in default crop handler | N/A | IMC only; sugar-cane exclusion hard-coded; tag equivalent optional (followup). |
| f4a4efce0 | quarry ghost chunkloading at edges | COVERED | 2026-09-08 spot-check. |
| 57418971a | checkstyle exclusion | N/A | Build. |
| a6f381f14 | #4618 oredict stacks >1 in recipe API | COVERED | IngredientStack(Ingredient, count). |
| 262ff7ee6 | fix Gradle compilation | N/A | Build. |
| c09be6f46 | update CoFH energy API | N/A | NeoForge FE. |
| 9bfc13031 | delivery robot multi-slot drop; ItemStackExact slots | COVERED | 7.1.x-final robotics port; minor NBT-default nit is a followup. |
| 70cc38697 | ItemStackExact stacking | COVERED | 7.1.x-final port. |
| e853ceb5d | direction statement parameter crash | COVERED | 2026-09-08 spot-check. |
| b429080af | 7.1.23: #3839 world corruption, #3837 oil mid-air on superflat | COVERED / N/A | OilGenerator vetoes FlatLevelSource; MjBattery capacity is fixed in code (not read from NBT); IngredientStack. |
| a0faa7a20 | update versions.txt | N/A | Release metadata. |
| bc43bf161 | changelog 7.1.22 | N/A | Docs. |
| b9e8998da | BuildCraft 7.1.22 | N/A | Release. |
| 50147c04a | MapWorld/saving concurrency (#3611) | N/A | No server MapWorld; the Zone Planner map is client-side. Carry the lesson into the far-map todo. |
| 04936780c | crash when a machine clicks a gate (#3542) | COVERED (structural) | PluggableGate never casts containerMenu; NeoForge FakePlayer.openMenu does nothing. |
| d5033da8a | BuildCraft 7.1.21 | N/A | Release. |
| 54eb54d59 | #3498 refinery deletes insufficient input | COVERED | Distiller checks the input amount and output room before extracting. |
| bfa69a2ef | #3497 Nether flammable-fluid explosion | N/A | Dropped by 8.0.x; design input for Nether oil (followup). |
| 9417e8a59 | #3307 autoworkbench not reacting to insertion | COVERED + PINNED | WorkbenchCrafting dirty flags; game test autoworkbench_resumes_after_input. |
| e2c2361df | #3503 dupe, #3496 icon crash, #3500 div-by-zero | COVERED / N/A | Symmetric component matching plus craftExact re-verification; no metadata icons; miningMultiplier range 1.0 to 200.0. |
| 25318e36c | sanitize fluid block draining (#3495) | COVERED | BlockUtil drains through FluidState.isSource and BucketPickup. |
| 1449ec058 | BuildCraft 7.1.20 | N/A | Release. |
| 50f983bda | #3341 builder and flowing liquid | COVERED | SchematicBlockFluid isFlowing. |
| cc9df443f | #3429 robots sink when station broken | COVERED | DockingStationPipe refuses when the pipe is gone. |
| 293ab8098 | #3488 zone planner fullscreen crash | N/A | GUI rewritten. |
| 4c106fdf5 | #3492 wasted space in packets | N/A | StreamCodec payloads. |
| dc7b11203 | BuildCraft 7.1.19 | N/A | Release. |
| 790d05d4c | #3316 builder dupe | COVERED | 2026-09-08 spot-check. |
