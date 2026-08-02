# Robotics Ph3 — EntityRobot + ItemRobot + renderer: design record

Working design for [robotics-resurrection.md](robotics-resurrection.md) Ph3, synthesized 2026-08-01 from
three research passes (7.1.x source inventory; main-side seams + per-node entity APIs; renderer
landscape) and then **adversarially verified by a 6-agent workflow** — every claim below survived or was
corrected by that pass. Implementation agents: treat this as the contract. Cited signatures were
verified against `.neoforge-ref/` — re-verify only if a compile disagrees.

**Sources of truth:** `upstream/7.1.x:common/buildcraft/robotics/EntityRobot.java` (1556 L, line numbers
below refer to it unless marked), `ItemRobot.java`, `EntityRobotEnergyParticle.java`;
`8.0.x-1.12.2:src_old_license/buildcraft/robotics/EntityRobot.java` = **[1.12]**, a dead mechanical
1.12-era migration — take its BlockPos/Direction/Vec3 phrasing and its bug fixes, NOT its half-commented
stubs (`initSkullItem`, `attackTargetEntityWithCurrentItem` are broken there; use 7.1.x for those).

---

## Decision 1 — EntityRobotBase rebases onto bare `Entity` (dropping `LivingEntity`)

7.1.x line-level evidence: the ONLY things EntityRobot ever used from its living parents are
(1) `ForgeHooks.onLivingAttack` (one hook; robots are machines, we accept not firing living-damage
events), (2) the `EnchantmentHelper` melee path (Ph9 Knight board only), and (3) armour-model rendering
for wearables (Ph9 polish only). Everything else was *suppression*: `setHealth` no-op'd, all four
equipment accessors stubbed, `moveEntityWithHeading`/`isOnLadder`/`canDespawn`/`updateEntityActionState`
overridden to neuter the parent, and the [1.12] port had to renumber its DataWatcher ids because the
living base owned id 15.

Modern `LivingEntity` is far heavier than the 1.7.10 one: mandatory `EntityAttributeCreationEvent`
registration (missing it NPEs at first construction with **no boot-time warning** — `MobCategory.MISC`
is excluded from `DefaultAttributes.validate()`), air-supply/drowning in `baseTick`, potion/area-cloud
susceptibility, player sweep attacks hitting docked robots, `isPushable()=true`, equipment-update
detection, and the full living tick. Each is a suppression override and a cross-node bug surface.

Bare `Entity` is fully templated in-repo by `EntityQuarryRig` (registration, save/load, hurt,
bounding-box, collision directives all already written there).

Consequences implementation must cover (bare-Entity obligations):
- `isPickable()` → true, `canBeCollidedWith` → false (directive: `(Entity)` arg ≥1.21.10 vs no-arg
  1.21.1 — copy `EntityQuarryRig:126-133`), `isPushable()` → false.
- `hurtServer(ServerLevel,DamageSource,float)` ≥1.21.10 vs `hurt(DamageSource,float)` 1.21.1
  (copy `EntityQuarryRig:147-156`); shared body in a version-neutral private method.
- No attribute registration, no `getMainArm`, no equipment stubs, no despawn concern (nothing
  despawns unless `Mob`), no living events. Hurt flash = own `hurtTime` int synced via accessor,
  decremented both sides.
- Ctor becomes `EntityRobotBase(EntityType<? extends EntityRobotBase>, Level)` — the hard-coded
  `EntityType(s).PIG` placeholder AND its `//? if >=26.2` directive are deleted (on 26.2 only the
  vanilla *constants* moved to `EntityTypes`; the class and `Builder.of` are unchanged, so
  `BCRoboticsEntities` itself needs no 26.2 directive — only the `.build()` fork per
  `BCBuildersEntities:29-33`).
- `isAlive()` semantics intentionally shift from health-gated (`LivingEntity`: `!removed && health>0`)
  to removal-gated (`Entity`: `!isRemoved()`). Sole consumer is `RobotRegistry.robotIdTaking` (:202)
  lazy-release; this is the *intended* fix (7.1.x no-op'd health anyway) — note it in that test.
- `EntityRobotBase.defineSynchedData` gets a concrete empty body so test fixtures/subclasses without
  synched data compile; `EntityRobot` overrides (no super-call trap on bare Entity — the vanilla base
  seeds its own entries before the subclass hook).

**Known compile ripple (land in ONE commit with the rebase or inactive nodes break):**
`RobotStationPluggableTester.TestRobot` has FIVE LivingEntity-coupled sites — `getMainArm()`
(:513-516, delete), the `//? if <1.21.10` equipment trio (:636-650, delete), the
`super.defineSynchedData` call + comment (:615-620, now wrong), the `>=1.21.10`-only save-data guard +
comment (:622-634, wrong — bare Entity needs both branches), and the `super(level)` ctor (:385-387).
`RedstoneBoardRegistryTest.TestEmptyBoardNBT.create(EntityRobotBase)` (:123) breaks when Decision 2
retypes the abstract — that file joins the Modified list.

## Decision 2 — Seam (a): `IRobotAccess`, and what types against what

New interface `buildcraft.api.robots.IRobotAccess` — the surface AIs/boards program against.
`AIRobot.robot` (currently `EntityRobotBase`, AIRobot.java:16), the reflective ctor lookups
(**AIRobot.java:191 in `loadFromNBT`, AIRobot.java:216 in `loadAI`, RobotManager.java:53** — all
`getConstructor(EntityRobotBase.class)` → `getConstructor(IRobotAccess.class)`),
`RedstoneBoardRobot`'s generic, and `RedstoneBoardRobotNBT.create` all retype to `IRobotAccess`.
Concrete AI ctors (Ph4+) will take `IRobotAccess`.

`EntityRobotBase extends Entity implements IRobotAccess, IFluidHandlerAdv` stays the API anchor;
`DockingStation.robotTaking()`, `RobotEvent`, and the registry keep `EntityRobotBase`.
`setUniqueRobotId(long)` deliberately stays on `EntityRobotBase`, NOT `IRobotAccess` (it's a
registry-lifecycle member, live at RobotRegistry.java:142 — not an AI concern).
`onChunkUnload()` is DELETED from the contract — it's 7.1.x residue with zero callers; the unload
path is `onRemovedFromLevel()` → `unloadRobot` (Decision 7).

`IRobotAccess` contents = the measured 7.1.x AI-usage surface. Entity-derived members are declared
with signatures `Entity` already satisfies for free (`Level level()`, `Vec3 position()`,
`BlockPos blockPosition()`, `int getId()`, `Vec3 getDeltaMovement()`, `void setDeltaMovement(Vec3)`,
`AABB getBoundingBox()` — all verified present with these exact names on 1.21.1 AND 26.2), plus the
robot surface: `getBattery`, `getPower`, `getHeldItem` (= itemInUse), `setItemInUse`,
`setItemActive`, `aimItemAt` (both), `getAimYaw/Pitch`, `dock`, `undock`, `getDockingStation`,
`getLinkedStation`, `setMainStation`, `getRegistry`, `getRobotId`, `getZoneToWork`,
`getZoneToLoadUnload`, `containsItems`, `hasFreeSlot`, `unreachableEntityDetected`,
`isKnownUnreachable`, `getBoard`, `isMoving`, `receiveItem`, `releaseResources`, plus an inventory
accessor pair (`int getInventorySize()` = 4, `ItemStack getInventoryStack(int)` /
`void setInventoryStack(int, ItemStack)`) since main's contract no longer extends a container type.
Fluid access rides the existing `IFluidHandlerAdv` supertype fork.

This is what makes AI framework tests pure JUnit: a mock `IRobotAccess` + real `MjBattery` drives
`AIRobot.cycle()/startDelegateAI/terminate/abort` with no Level.

## Decision 3 — Energy: re-pin MAX_POWER to the canonical 10 RF = 1 MJ conversion

The API as ported is internally inconsistent: `MAX_POWER = 5000 * MjAPI.MJ` implies 1 RF = 0.05 MJ,
while `AIRobot.getPowerCost()` default (`MjAPI.MJ/10`) implies 1 RF = 0.1 MJ — a modern robot would
run **half** as long as a 7.1.x one. BuildCraft's canonical bridge was 1 MJ = 10 RF. **Re-pin
`MAX_POWER = 10_000 * MjAPI.MJ`** (7.1.x MAX_ENERGY 100 000 RF ÷ 10). Verified safe: the existing
station charge tests use a hand-picked TestRobot capacity, not MAX_POWER — nothing pins 5000. These
are `public static final long` compile-time constants that would inline into any addon jar — re-pin
NOW, before any API jar ever ships (per the api-redistribution plan), is exactly the right moment.
Ratios convert cleanly (all constants get a `// chosen, not derived — 7.1.x <RF value> at 10 RF/MJ`
comment):

| Quantity | 7.1.x RF | Modern µMJ |
|---|---|---|
| MAX_POWER | 100 000 | `10_000 * MjAPI.MJ` (1e10 — still > `Integer.MAX_VALUE`; tripwire test keeps its point) |
| SAFETY_POWER | 20 000 | `MAX_POWER / 5` |
| SHUTDOWN_POWER | 0 | 0 |
| Damage debit per point (l.903) | 2 600 | `260 * MjAPI.MJ` = 2.6 % of max — same ratio as 7.1.x |
| Charging-detect threshold (l.1530) | > 5 | `> MjAPI.MJ / 2` |
| Recharge-complete headroom (Ph4) | 500 | express as `MAX_POWER / 200`, survives re-pins |
| Per-tick extract cap (l.144 maxExtract 100) | 100 RF/t | **not reinstated** — if a Ph4 AI needs a ceiling, the existing idiom is `MjBattery.rampedExtractLimit(maxPerTick)` (MjBattery.java:124, used by TileQuarry:649); do NOT hand-roll a second mechanism |

Wearable damage-reduction: 7.1.x `mul = mul * 2 / (2 + damageReduceAmount)` for armour, `mul *= 0.7`
(int-truncating) per other wearable — port the *shape* in long math; exact armour hookup may defer with
wearable acceptance (Decision 7). Survival test stays `stored - debit > 0` (strictly greater — landing
on exactly 0 destroys; preserve, pin in a test, comment it).

## Decision 4 — Networking: SynchedEntityData + complex spawn data replace the 7.1.x command channel

The six 7.1.x `PacketCommand` messages and the client `requestInitialization` round-trip are deleted.
- **Accessors**: BOARD_ID (String); ITEM_IN_USE (ItemStack — replaces the init handshake);
  ITEM_ACTIVE (boolean); AIM_YAW, AIM_PITCH (float); ENERGY_MJ (int whole-MJ,
  `(int)(stored / MjAPI.MJ)`, 0..10000 — `EntityDataSerializers.LONG` DOES exist on all nodes, so
  this is a deliberate bandwidth/dirty-rate choice, not an API constraint: VAR_INT whole-MJ goes
  dirty once per MJ, not once per µMJ); SLEEPING (boolean — Decision 6); ENERGY_SPEND (int, drives
  particle rate); HURT_TIME (int); STEAM_DIR (`EntityDataSerializers.VECTOR3` — **one directive on
  the field declaration only**: the constant's generic is `Vector3f` on ≤1.21.10 but `Vector3fc` on
  ≥1.21.11, so fork the `EntityDataAccessor<...>` declaration `//? if >=1.21.11`; reads stay neutral
  typed `Vector3fc v = entityData.get(STEAM_DIR)` (Vector3f implements Vector3fc, already imported at
  MutableQuad.java:12), writes stay neutral as `new Vector3f(x,y,z)`; take the [1.12] Vec3-based
  `setSteamDirection`, which fixes 7.1.x's int-truncation bug); INV_0..INV_3 (4 × ItemStack —
  replaces per-slot command sync; renderer reads all four).
- **⚠ ItemStack identity trap (applies to ITEM_IN_USE + all four INV accessors):** `ItemStack` does
  not override `equals()`, and `SynchedEntityData.set` gates dirtiness on value inequality — so
  re-setting the SAME mutated instance is a silent no-op (client never sees the change), while
  unconditionally setting a fresh `copy()` every tick re-sends 5 stacks at 20 Hz. Correct pattern:
  keep a server-side shadow copy per accessor, push `stack.copy()` ONLY when
  `!ItemStack.matches(shadow, current)`. JOML `Vector3f` DOES value-compare — do not generalize the
  trap to STEAM_DIR. A game test must mutate a slot in place, push, and assert the synched value
  changed.
- **Spawn data**: `IEntityWithComplexSpawn` (uniform all five nodes) carries the wearables list.
- Laser DW 12–15 + NBT + the three never-called setters: **port the API, skip the render branch**
  (verified: nothing in 7.1.x ever calls them; keep for addon surface + NBT-shape stability).

## Decision 5 — Persistence: version-neutral writeData/readData on the CONCRETE entity, modern format, no legacy migration

The `readAdditionalSaveData`/`addAdditionalSaveData` fork (`CompoundTag` 1.21.1 vs
`ValueInput/ValueOutput` ≥1.21.10) is isolated ONCE — **on concrete
`buildcraft.robotics.entity.EntityRobot`, NOT on `EntityRobotBase`**: `BCValueInput`/`BCValueOutput`
live in `buildcraft.lib.misc`, and `buildcraft/api/**` has ZERO lib imports today (`NbtApiUtil`
exists precisely to keep that edge out; the api-redistribution plan depends on it). EntityRobotBase
stays abstract-and-pure. Template is `EntityQuarryRig:64-76` — **no `super.saveAdditional`-style
calls** (those methods are `protected abstract` on Entity; the AbstractBCBlockEntity super-call shape
does NOT transfer). Both branches are needed on bare Entity (1.21.1's methods are abstract too — the
LivingEntity-only-≥1.21.10 trick from the old TestRobot comment dies with the rebase). Construction
needs nothing extra: the 1.21.1 wrappers take a bare CompoundTag (registry ops resolve globally via
`NBTUtilBC.registryAwareOps()`); escape hatch if ever needed is `this.registryAccess()` (uniform).

Format — keep 7.1.x key *names*, modern *bodies*:
- `battery`: bridge MjBattery↔wrapper via `output.store("battery", CompoundTag.CODEC,
  battery.serializeNbt())` / `input.read("battery", CompoundTag.CODEC).ifPresent(t ->
  battery.deserializeNbt(t))` — copy TileLaser.java:296/:314 verbatim. Single `stored` long;
  `setStored` clamps, so capacity re-pins load cleanly.
- `linkedStation`/`currentStation`: 7.1.x KEY NAMES with DockingStation's modern `pos` int[3] +
  side-byte BODY — but as a small static pos/side helper, NOT a call into
  `DockingStation.readFromNbt` (abstract class; the robot only needs pos+side to re-resolve via the
  registry). **Bounds-check the side byte 0..5 on read** (legacy UNKNOWN=6 or corrupt data must mean
  "no station", not an ArrayIndexOutOfBoundsException chunk-load crash — DockingStation.java:155's
  unguarded indexing is not the pattern to copy). `setMainStation(null)` stores nothing/null (the
  [1.12] fix), never a sentinel.
- `inv`: `new ItemHandlerSimple(4, ...)` — fixed-size, zero-fills, serializes as a proper list;
  exactly fits (NOT the 7.1.x `inv[0]` literal-bracket top-level keys).
- `wearables`: `store("wearables", ItemStack.CODEC.listOf(), ...)` or a hand-rolled ListTag loop per
  ItemHandlerSimple:120-166. **`ItemStack.CODEC` rejects empty stacks** (see TravellingItem.java:86
  guard) — filter/guard emptiness on every stack write; single-stack reads use
  `input.read(key, ItemStack.CODEC).orElse(ItemStack.EMPTY)` (TileBuilder.java:700 idiom).
- `mainAI` read must null-guard `AIRobot.loadAI` returning null (unregistered AI name — 7.1.x NPE'd
  on next tick). `board` keeps the 7.1.x write-only-if-not-inside-mainAI dedup; on read, absent board
  recovers from the AI tree, else the registry's empty board.
- Drop dead 7.1.x fields entirely: `stackRequests` (never written), `isDocked` (never read),
  `getItemIcon`. No legacy migration — no modern save has ever contained a robot.

## Decision 6 — `isActive()` is renamed `isSleeping()` with honest semantics

7.1.x `isActive()` returns true when the robot is asleep/shut down — the inverse of its name; the
renderer and the Ph6 sleep trigger consume it. Port as `isSleeping()`; DW pushes
`isSleeping() && ticksCharging == 0` into SLEEPING (charging robots look awake so the charge is
watchable). `mainAI == null` (the whole of Ph3) → not sleeping. Changelog-note the rename.

The `ticksCharging` latch survives modern charging via a **charge-receiver wrapper** — with three
verified constraints:
1. It must implement `IMjReceiver` **AND `IMjReadable`** — `TriggerPower` (:74/:84) instanceof-checks
   the CAP_RECEIVER result for IMjReadable, so the "Energy Stored" gate trigger currently reads a
   docked robot's charge through the station; a receiver-only wrapper silently kills that with no
   test coverage. Cleanest: `class RobotChargeReceiver extends MjBatteryReceiver` overriding
   `receivePower` to bump `ticksCharging` (max 30, decay 1/tick, bump +5 while ≤ 25, threshold per
   Decision 3).
2. **`simulate == true` must NOT bump the latch** (simulating callers exist in-repo:
   PipeBehaviourObsidian:238, PluggablePulsar:212) — guard it, pin it in the receiver test.
3. `EntityRobotBase` gets a CONCRETE default `getChargeReceiver()` returning a plain
   `MjBatteryReceiver(getBattery())` so fixtures/subclasses compile; `EntityRobot` overrides.
   `RobotStationPluggable.dockedRobot()`'s null-guard chain extends to the receiver (an NPE there
   surfaces as a pipe-tick crash, not a robot bug).
`RobotStationPluggable.getCapability`/`getInternalCapability` switch to `robot.getChargeReceiver()`;
receiver identity is safe (all call sites refetch per call — verified). The three existing station
game tests must stay green.

## Decision 7 — Ph3 scope: what lands, what stubs, what waits

**Lands fully:** entity + registration (`BCRoboticsEntities` — copy `BCBuildersEntities:20-33` shape
verbatim incl. the `.build()` directive, wired in `BCRobotics.init` after BlockEntities); flags
(`noPhysics = true`, `setNoGravity(true)`, `fireImmune` via builder, `shouldBeSaved()` true,
pick/collide/push overrides); dimensions 0.25×0.25 `.sized(0.25f, 0.25f)` + synthetic ±0.25 pick box
(`makeBoundingBox` directive per `EntityQuarryRig:92-116`); tick loop (docking snap server-side —
client rides position sync; charge decay; DW pushes with the ItemStack shadow-copy gate; item ticking
with the update-blacklist keyed on the Item OBJECT per-entity, not 7.1.x's static numeric-id set);
dock/undock/setMainStation + linked-station resolution/validation/shutdown-on-loss (shutdown = log +
`convertToItems` deferral note; `AIRobotShutdown` is Ph4); void kill at `minBuildHeight - 64` (not
7.1.x's hard −128); registry lifecycle (`firstUpdate` register, `onRemovedFromLevel` → unloadRobot,
`remove(RemovalReason)` override routing server kills through `killRobot` — the 7.1.x `setDead`
override, CRITICAL: a `discard()` that bypasses the registry leaks reservations; kill vs unload
mapped from `RemovalReason`); damage/death (guards: no mob damage, no falling-block damage, docked =
invulnerable; battery debit; `convertToItems` + drops: robot item w/ board+charge, itemInUse, inv,
wearables — tank NOT dropped, 7.1.x behaviour, comment it); 4-slot inventory + `containsItems`/
`hasFreeSlot`; single-stack 4000 mB tank behind the `IFluidHandlerAdv` fork; `interact` (directive:
3-arg ≥26.1 / 2-arg else) with the wrench-dismantle peel path (`onRobotHit(false)`: last wearable →
itemInUse (via `setItemInUse(null)`, the [1.12] fix) → `convertToItems`) + `RobotEvent.Interact`/
`Dismantle`; `aimItemAt` both overloads (7.1.x atan2(dz,dx) formula — the [1.12] arg-swap is a bug;
verify aim in-client) + client yaw smoothing (60°/tick step); unreachable-entity cache extracted as
pure `UnreachableEntityCache` (LongSupplier clock, WeakHashMap, +1200 ticks, remove-on-expiry);
`ItemRobot`; renderer + energy particle (tinted CLOUD-family, no bespoke ParticleType — spawn point
offset 0.25 along steam dir, rate from ENERGY_SPEND gated by particle setting); empty-board wiring so
the robot has a skin (board id DW → `RedstoneBoardRegistry` texture resolve).

**Stubbed with API kept (→ Ph4/Ph6):** `mainAI` field + null-guarded cycle site + `getOverridingAI`/
`overrideAI` forwards; `shutdown(String)`; `receiveItem` (station-adjacency check lands, delegates to
AI when present, else returns stack); zones return null (Ph6); `getDebugInfo` position/energy lines
now, AI walk guarded.

**Waits entirely:** `attackTargetEntityWithCurrentItem` (Ph9 — do not port; every enchantment
decision would be forced now); skull-profile resolution + armour/skull *rendering* (Ph9); wearable
*acceptance* in `interact` (Ph9 — the list, NBT, spawn-sync, and drop path DO land so the save format
never changes; nothing can insert wearables until Ph9, so no mutation payload needed);
`stackRequests` (Ph8, dropped).

## Decision 8 — ItemRobot + recipes

`ItemRobot`: maxStackSize 1 (skip 7.1.x's stack-16-when-empty nicety); board + energy in
`DataComponents.CUSTOM_DATA` (`{board:{id:String}, energy:long}`) — lazily treat a bare stack as
empty-board. **Verified incantations (uniform all five nodes):** read via
`stack.get(DataComponents.CUSTOM_DATA)` → `copyTag()`, write via
`stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, d -> ...CustomData.of(t))` (precedents:
ItemList_BC8:44-55, ItemMapLocation:77-81). **CompoundTag accessors cliff at 1.21.10**
(`getString`/`getLong` return Optionals on newer nodes) — read the blob ONLY through
`NbtApiUtil.getString/getLong/getCompound` (already Stonecutter-forked, api-layer; use it
consistently on every robot NBT-blob read path so the helpers don't drift vs NBTUtilBC).

`useOn` (uniform `UseOnContext` API): must hit a pipe holding an untaken `RobotStationPluggable` on
the clicked face (resolve via `RobotUtils.getStations` + hit face); fire cancellable
`RobotEvent.Place`; assign id from registry; position at station face-center (same math as tick
snap); `takeAsMain` → `dock(getLinkedStation())` → `level.addFreshEntity` (uniform — deliberately
NOT `EntityType.spawn`, which cliffs 3 ways); consume unless creative. **⚠ Drop 7.1.x's empty-board
placement rejection** (ItemRobot.java:205-208 upstream): Ph3 has ONLY the empty board, so copying
that guard would make nothing placeable and the placement game test could never pass. An empty-board
robot places, docks, idles — that is the Ph3 MVP. (Ph4 revisits whether an empty-board robot should
still place once real boards exist.)

Tooltip: charge % with the 7.1.x colour ladder. The 7.1.x `tip.gate.*` lang keys DO NOT exist on
main — add three NEW keys under an honest namespace: `tip.charge.charged`, `tip.charge.fullcharge`,
`tip.charge.nocharge` (en_us.json; zh_cn falls back). Do not resurrect the `tip.gate.*` prefix —
it was a 7.1.x gates/robots string-sharing accident.

Creative tab: empty-board entry at 0 energy + per-registered-board 0/full entries (auto-grows in
Ph4/5); follow the post-61caeb0a3 tab wiring in BCRoboticsItems.

Item model: ship BOTH `items/robot.json` (minecraft:model selector) AND a hand-authored
elements-based `models/item/robot.json` UNCONDITIONALLY — there is no per-node resource mechanism;
the 1.21.1 `generateOldItemModels1211` task skips items/ and never clobbers a hand-authored classic
model (build.gradle.kts:272-317, robot_station precedent). The cube model's texture must live under a
LISTED atlas source dir — the planned copy at `textures/item/robot.png` qualifies via vanilla's
`item` source (the alternative — adding an `entity` directory source to atlases/blocks.json — would
stitch 25 unused sprites; the single duplicate PNG is the cheaper call).

**Recipes:** `robot_station` gets its 7.1.x recipe NOW (shaped: `"   "/" I "/"ICI"`, I = iron ingot,
C = `buildcraftunofficial:chipset_gold`) — creative-only since Ph2 was an oversight. **`ItemRobot`
deliberately ships recipe-less in Ph3**: boards don't exist until Ph4, so a craftable robot would be
an expensive do-nothing item; pricing (and whether the Redstone Crystal returns) is decided in Ph4.
This supersedes the Ph3 recipe note in robotics-resurrection.md — update that file when landing.

## Decision 9 — Renderer: one class, 8.0.x geometry, in-repo textures

**TWO API generations, not three** (corrects robotics-resurrection.md): 1.21.1
`EntityRenderer<T>` (`render(T,float,float,PoseStack,MultiBufferSource,int)` +
`getTextureLocation(T)`) vs ≥1.21.10 `EntityRenderer<T,S extends EntityRenderState>`
(`createRenderState()` / `extractRenderState(T,S,float)` /
`submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`). **26.1.2 and 26.2 are identical for
entity rendering.** Directive sites in the one renderer class — FOUR, not three:
(1) imports — 3-way only because `CameraRenderState` moves package (`renderer.state` ≥1.21.10 →
`renderer.state.level` ≥26.1); (2) class declaration (2-way at 1.21.10); (3) the method-body block
(2-way at 1.21.10); (4) **the laser call** — `LaserRenderer_BC8.renderLaserStatic` forks at **26.1**,
NOT 1.21.10, so on 1.21.10/1.21.11 the "modern" body must call the immediate-mode arity: wrap it in
its own `>=26.1` helper using the `Object collector` trick (`BCBuildersEventDist.java:394-421`; same
pattern recurs at :277-280, :438, :826-829). Registration is one undirected
`event.registerEntityRenderer(...)` line in `BCRoboticsClient`
(`EntityRenderersEvent.RegisterRenderers`, uniform all five).

**Geometry reference is `8.0.x-1.12.2:src_old_license/buildcraft/robotics/render/RenderRobot.java`,
NOT 7.1.x** — the robot entity textures already in this repo are byte-identical to 8.0.x's 32×32
re-cut of 7.1.x's 64×32 originals; 7.1.x UV math does not fit them. One 8³-unit cube centred on
origin at 1/16 scale, `texOffs(0,0)`/`textureSize(32,32)` net (pixels: up (16,0)-(24,8),
down (8,0)-(16,8), north (8,8)-(16,16), south (24,8)-(32,16), west (16,8)-(24,16),
east (0,8)-(8,16)). Emit via a shared `emitCube(VertexConsumer, ...)` helper — copy ONLY the
`ModelUtil.createFace(...).lighti(...).render(pose, buffer)` STYLE from
`BCBuildersEventDist.emitRobotCube`; that cube is atlas-sprite-mapped, so its
`getInterpU/V` sprite math must be dropped and `UvFaceData` fed raw 0..1 floats (pixel/32).
Deliberately NOT `Model`/`ModelPart` (generic shape forks at 1.21.10 + needs LayerDefinition).
Modern branch: `collector.submitCustomGeometry(poseStack, type, (pose, vb) -> ...)`
(call-compatible 1.21.10→26.2); 1.21.1: `bufferSource.getBuffer(type)`.

**Render type — cull-inversion trap:** vanilla `entityCutout` is culled on ≤1.21.11 but NO-CULL on
26.x. Never call vanilla directly — use `BCLibRenderTypes.entityCutoutCull(id)`
(`lib/client/render/BCLibRenderTypes.java:266`, already cross-node-correct). Entity textures are
direct file paths, never atlas sprites
(`Identifier.parse("buildcraftunofficial:textures/entity/robot_base.png")` — full prefix + .png;
`SpriteHolderRegistry` does NOT apply and fails silently to missing-texture on misuse). Write
canonical `Identifier` — the build rewrites for <1.21.11.

**Draw passes** (all over the same cube): base skin (texture resolved from BOARD_ID via the board
registry); `overlay_side.png` at vertex-alpha = charge fraction (ENERGY_MJ/10000),
entity-translucent, fullbright (`LightTexture.FULL_BRIGHT`); `overlay_bottom.png` opaque fullbright.
Both overlays suppressed while SLEEPING. Hurt: vertex-color tint (1.0, 0.6, 0.6) + tiny Z wobble
while HURT_TIME > 0. Body yaw: shortest-arc interpolation of prev/current robot yaw. **No shadow** —
neither 7.1.x nor 8.0.x ever set one; leave shadowRadius alone on every node. Held item:
`ItemStackRenderState` field on the render state — the in-repo pattern is
`RenderPipeHolder.extractRenderState:99-144` (resolver FIELD + 6-arg `updateForTopItem`, cleared
each frame; a PATTERN, not a signature template — the entity path uses
`context.getItemModelResolver()`); aim-pitch rotation + the 45°-cycle active spin driven from
tickCount+partialTick, NOT the 7.1.x wall-clock-mutated-from-renderer hack. Four inventory slots as
small floating items at the cube corners (±0.125 x/z, +0.28 y, 0.5 scale). Goggles see-through
charge mode (8.0.x feature): deferred with the goggles-art todo.

**RobotRenderState extends EntityRenderState**: only ADD fields. Full inherited-field shadow-hazard
set (both gens): entityType, x, y, z, ageInTicks, boundingBoxWidth/Height, eyeHeight,
distanceToCameraSq, isInvisible, isDiscrete, displayFireAnimation, lightCoords, outlineColor,
passengerOffset, nameTag, nameTagAttachment, leashStates, shadowRadius, shadowPieces, partialTick;
1.21.10-only: hitboxesRenderState, serverHitboxesRenderState; 26.2-only: scoreText — **never name a
field any of these** (a 26.2-only name compiles on four nodes and shadows on the fifth), and don't
name methods getRenderData/setRenderData (NeoForge base). State is created once and re-filled every
frame — every added field must be unconditionally overwritten in `extractRenderState`. On 1.21.1 add
the `lerpTo` snap override on the ENTITY (`EntityQuarryRig:170-182`) or movement visibly lags.

**Assets — explicit move list, NOT a glob:** `git mv` from `misc/unused_textures/` into new
`src/main/resources/assets/buildcraftunofficial/textures/entity/`: the **23** `robot_*.png` entity
skins **EXCLUDING the four `robot_station*.png`** (those are 16×16 pipe-pluggable copies, not entity
skins — leave them). Three of the four still duplicate a live texture; `robot_station_base.png` no
longer does, because the live copy was deleted once the pedestal moved to the `available` sprite
1.7.10 actually drew, so the `misc/` copy is now the only one on disk. Plus
`overlay_side.png` + `overlay_bottom.png` (verify exact overlay
filenames on disk) = 25 files; rename `robot_fluidCarrier.png` → `robot_fluid_carrier.png` and
`robot_leaveCutter.png` → `robot_leave_cutter.png` (uppercase rejected). Must NOT sit under
`textures/block/` (atlas-stitching). MVP uses `robot_base.png` + two overlays; the other skins ship
dark until later phases.

## Test plan (tests are written FIRST, red, against skeletons)

**Pure JUnit:** energy tripwire (`MAX_POWER > Integer.MAX_VALUE`, `== 10_000 * MJ`,
`SAFETY == MAX/5`, damage debit `== 260 * MJ`, chosen-not-derived comments);
`UnreachableEntityCache` (expiry, weak-key tolerance, remove-on-expiry read);
AIRobot framework delegation against a mock `IRobotAccess` (start/delegate chain/terminate/abort/
power drain through a real `MjBattery`; `loadAI` null-guard on unregistered name); ItemRobot
CUSTOM_DATA round-trip under Bootstrap — **pin ONLY the board-id string + energy long** (dynamic-
registry components silently drop under Bootstrap ops fallback, NBTUtilBC.java:38-50; component-
fidelity assertions belong in game tests).

**GameTests** (each: Java method + `BuildCraftGameTests` registration + manifest JSON — count must
increment; new shared harness utils `forceLoadEntityArena` (extract the FluidPhysicsTest:42-49 3×3
chunk-force recipe into a lib) + `tickUntil`): spawn → registry-registered + unique id assigned;
full NBT round-trip (battery long, inv list, tank, itemInUse, stations) through a real save cycle;
docking snap position after tick; damage debits battery + sets HURT_TIME; battery-exhausted hit →
converts to items, drops contain charged robot item, station reservation freed, registry emptied
(death-frees-all end-to-end); docked robot invulnerable + mob/falling-block damage filtered;
persistence (shouldBeSaved, no despawn over time); **in-place inventory mutation propagates to the
synched accessor** (the ItemStack identity trap); ItemRobot placement end-to-end (station taken,
robot docked at face-center, item consumed; blocked when station already taken; Place event
cancellable; empty-board robot PLACES — no 7.1.x rejection); charge-receiver: three existing station
tests stay green + simulate does not bump ticksCharging + IMjReadable still exposed (TriggerPower
path). Document the `makeMockPlayer`-not-ServerPlayer skips where they bite.

**In-client (McDevBridge, after implementation):** renderer on 26.2 + 1.21.1 (the two extreme
generations), aim orientation (the atan2 question), energy particles, dock/undock feel, hurt flash.

## File plan

New: `api/robots/IRobotAccess.java`; `robotics/BCRoboticsEntities.java`;
`robotics/entity/EntityRobot.java`; `robotics/entity/UnreachableEntityCache.java`;
`robotics/item/ItemRobot.java` (+ registration in `BCRoboticsItems`);
`robotics/client/render/RenderRobot.java` (+ `RobotRenderState` ≥1.21.10);
assets: `textures/entity/` (25 git-mv'd PNGs per the explicit list, 2 renamed), `items/robot.json` +
`models/item/robot.json` + `textures/item/robot.png` copy (all unconditional), lang keys (3 new
`tip.charge.*` + item names); `data/buildcraftunofficial/recipe/robot_station.json`;
tests: `robotics/entity/EntityRobotTester.java` (game), `EnergyConstantsTest`,
`UnreachableEntityCacheTest`, `AIRobotFrameworkTest`, `ItemRobotComponentTest` (JUnit),
lib test util (`forceLoadEntityArena`/`tickUntil`).
Modified: `api/robots/EntityRobotBase.java` (rebase + ctor + constants + drop onChunkUnload),
`AIRobot.java` (:16, :191, :216), `RobotManager.java` (:53), `api/boards/*` retypes,
`RobotStationPluggable.java` (charge receiver + null-guard), `BCRobotics.java`,
`BCRoboticsItems.java`, `BCRoboticsClient.java`, `en_us.json`,
`RobotStationPluggableTester.java` (TestRobot: five coupled sites; fixture retires for the real
entity where possible), `RedstoneBoardRegistryTest.java` (TestEmptyBoardNBT retype),
`docs/robotics-resurrection.md` (Ph3 supersessions). **The rebase + api/boards retype + both
test-fixture edits land in ONE commit** (a half-applied rebase leaves inactive nodes uncompilable).

## Post-baseline amendments (2026-08-01, after the skeleton + red-test landing)

The skeleton/test phase falsified three details; these amendments SUPERSEDE the sections above.

1. **The plain `test` task cannot class-load `Entity` at all** (NeoForge `AttachmentHolder.<clinit>`
   → `FMLEnvironment.isProduction()` → "no current FML Loader"; `Bootstrap.bootStrap()` is equally
   dead, which is why `VanillaSetupBaseTester` — currently extended by nothing — silently rotted).
   Consequences, chosen deliberately:
   - **The four energy constants move to `IRobotAccess`** (interface fields; loads without Entity).
     `EntityRobotBase` keeps compiling references via inheritance; JUnit reads
     `IRobotAccess.MAX_POWER` directly. Addons stop needing an Entity class-load to read a constant.
   - **`UnreachableEntityCache` becomes generic over its key** (weak-identity semantics don't need
     Entity); `EntityRobot` instantiates it with Entity keys; the JUnit tests key on plain objects —
     the `ReflectionFactory` fake-entity hack in UnreachableEntityCacheTest is DELETED, not kept.
   - **`ItemRobotComponentTest`'s assertions convert to a game test** (ItemStacks cannot exist in
     the plain test JVM — registry-dead). The JUnit file goes away; the same six pins land in an
     `ItemRobotComponentTester` game test (+ manifest + registration).
   - The full fix — moddev's `neoforge { unitTest }` FML-JUnit environment — is spun off as its own
     scoped task, NOT part of Ph3. **LANDED 2026-08-01** (see amendment 9): the constraint this
     amendment worked around no longer exists, but every design choice it produced stays on its own
     merits (interface constants, generic cache keys, the game-test component pins).
2. **`getChargeReceiver()` is `public abstract IMjReceiver getChargeReceiver()` on
   `EntityRobotBase`** — the skeleton's concrete default (`new MjBatteryReceiver(...)`) created the
   first-ever `buildcraft.api` → `buildcraft.lib` import, which Decision 5 exists to forbid.
   Implementations: `EntityRobot` → `RobotChargeReceiver` (extends MjBatteryReceiver, so still
   IMjReadable for the TriggerPower path); `TestRobot` fixture → plain `MjBatteryReceiver`.
   `RobotStationPluggable.chargeReceiver()` types against `IMjReceiver`.
3. **Known regression to fix in implementation:** `robot_station_render_state_network_round_trip`
   (pre-existing Ph2 game test) now crashes — NPE, station null during `takeAsMain`. Introduced
   somewhere in the skeleton phase (fixture ctor/rebase suspected); diagnose properly, do not
   paper over. The implementation gate is the FULL game-test suite green, not just the 14 new ones.
4. **Force-loading an arena does NOT make it tick this tick** — the flake that survived the
   implementation gate. `robot_station_render_state_transitions` failed on 26.1.2 with "a freshly
   registered, untaken station renders as available"; `robot_item_rejected_when_station_taken` failed
   with "the squatting robot ... holds a real id on tick 5". Same single cause, measured:
   - `EntityArenaUtil.forceLoadEntityArena` calls `ServerLevel.setChunkForced`, which adds a `FORCED`
     ticket and blocks until the chunk is FULL — but promotion to `BLOCK_TICKING`/`ENTITY_TICKING`
     (levels 32/31, `ChunkLevel`) is applied later by the chunk source's own update pass. Instrumenting
     the squatter's first tick over 10 consecutive 26.1.2 runs gave **1, 1, 3, 1, 2, 1, 1, 2, 1, 1** —
     it varies per run, with no upper bound to rely on.
   - It varies because `GameTestServer.startTests` (`:339`) drops the whole test grid at
     `random.nextIntBetweenInclusive(±14 999 992)`, so a given test's blocks land in the arena's own
     chunk on some runs and in a neighbouring one on others. `StructureGridSpawner` then lays arenas out
     **8 per row, 6 apart in X and 8 in Z** (measured; matches `+5`/`+6` over a 1×1×1 structure), and
     `TestInstanceBlockEntity:349` force-loads only the chunks the structure box itself touches.
   - The production behaviour is correct, just not instantaneous: `RobotStationPluggable.onTick()` is
     the ONLY place a station is registered, and a block entity does not tick until its chunk is
     block-ticking. Nothing was changed in production for this.
   - Fix: every robot game-test phase now gates on the state it needs instead of a hard-coded tick, via
     `EntityArenaUtil.tickUntil` (single phase) and the new `tickUntilThen` (a second phase a fixed gap
     *after the first one actually fired* — chaining a bare `runAfterDelay(9)` behind a polled phase
     re-introduces the same race inverted). `RobotStationPluggableTester.whenStationRegistered` and
     `ItemRobotPlacementTester.whenStationRegistered` are the shared gates.
   - Fixed alongside: `ItemRobotPlacementTester` built at relative **x = 7**, one block outside its own
     6-wide arena cell and therefore inside the NEXT test's arena — both a latent same-block collision
     with `RobotStationPluggableTester`'s x = 1 column (they use identical z values) and the reason its
     pipe and robot straddled a chunk border so often. Moved to x = 3.
   - Not a defect but worth knowing: the game-test world (`run-gameTestServer/gametestserver/`) is
     **not** wiped between runs, so `buildcraft_robot_registry.dat` accumulates the docking stations
     every run leaks (19 after one run; nothing calls `onRemove` when the framework clears an arena).
     Harmless today only because the arena origin is re-randomised each run, so a stale station never
     shares a `(pos, side)` with a fresh one. Delete that directory if the registry ever needs a reset.
5. Noted for Ph4 (not Ph3 work): `AIRobot.writeToNbt` NPEs on an unregistered AI class name;
   the `ticksCharging` latch needs a read-only accessor when it lands so the charge-receiver game
   test can pin "simulate does not bump the latch" directly (the test file asks for it in a
   comment).

## Post-implementation amendments (2026-08-01, after in-client visual verification)

In-client verification on 26.2 and 1.21.1 found three defects the 397-test suite could not see. All
three are fixed; the root causes are recorded here because each is a trap that will recur.

6. **The Robot Station pluggable could not be placed by a player, on any node** (latent since the Ph2
   commit `22d061628`). `PluggableDefinition`'s three-argument constructor — the one that takes an
   explicit reader and loader, which the station needs because it *does* write a creation payload —
   nulls `creator`. `ItemPluggableSimple.onPlace` returns null when `creator` is null, and
   `BlockPipeHolder.useItemOn` then falls through with no pluggable, no sound and no message: the
   item is completely inert in the hand. The fix is a **four-argument
   `PluggableDefinition(identifier, reader, loader, creator)`** so a pluggable can have real synced
   state *and* be placeable from a plain item; the two existing constructors now document which half
   they give up. `robot_station` was the only definition in the tree built with the three-argument
   form *and* used through `ItemPluggableSimple` (silicon's facade/gate/pulsar/lens use it too, but
   each of those has a bespoke item that constructs the pluggable itself, so they are correct).
   The reason 397 green tests missed it: **every** test installed the pluggable programmatically via
   `replacePluggable`. `RobotStationPluggableTester#testPlayerPlacesRobotStationByHand` now drives
   `BlockState.useItemOn` — the exact method `ServerPlayerGameMode` calls — which is public with the
   same parameter order on all five nodes (only the return type moved, so calling it as a statement
   needs no directive). **Rule of thumb: a game test that constructs the thing under test bypasses
   the registration path that makes it reachable.**

7. **The charge overlay was invisible as a charge indicator.** Not an alpha bug — the alpha reached
   the vertex fine. `robot_base.png` carries the *same* red LED texels as `overlay_side.png`, so the
   overlay's only job is to make that LED brighter than the world-lit base. But every entity render
   type on every node applies `core/entity`'s directional term
   (`lightAccum = min(1, (max(0,n·L0)+max(0,n·L1)) * 0.6 + 0.4)`, `L0 = norm(0.2,1,-0.7)`,
   `L1 = norm(-0.2,1,0.7)`), which for an axis-aligned face is **1.00 up / 0.74 north-south /
   0.50 east-west / 0.40 down** — a ceiling `LightTexture.FULL_BRIGHT` cannot lift, because the
   lightmap is a separate multiply. A "fullbright" overlay on a north face therefore maxed out at
   `0.74 × 255 = 189`, which is *exactly* what the base skin's identical LED already renders at in
   full daylight: the blend became a no-op and the LED measured the same at 0 % as at 100 %.
   (Measured before the fix: 189 on 26.2 and 186 on 1.21.1 at every charge level. The verifier read
   that as "alpha never reaches the vertex"; the arithmetic says otherwise, and the night screenshot
   — LED still 188 with the body nearly black — already proved the overlay was drawing.)
   8.0.x avoided all of this with `GL11.glDisable(GL_LIGHTING)` around its overlay passes. The modern
   equivalent, with **no custom pipeline and no shader of our own**, is to emit the decal passes with
   a straight-**up normal**: `n·L0 + n·L1 = 1.617` clamps `lightAccum` to 1.0 on all six faces. The
   normal has no other job on these passes — culling is by winding, and nothing downstream of the
   diffuse term reads it. Verified in-client: the LED now ramps 189→255 (north face) / 127→255 (east
   face) in daylight and ~25→250 in a sealed dark room, linearly in charge, identically on 26.2 and
   1.21.1.

8. **`overlay_bottom` never drew on 26.x** (fine on 1.21.1). The body and the bottom decal are two
   *exactly coplanar*, depth-writing, opaque `entityCutoutCull` submissions. 1.21.1's
   `MultiBufferSource.BufferSource` ends the running batch whenever a different render type is asked
   for, so they come out in call order and the decal wins the equal-depth tie. On 1.21.10+ they do
   not: `SubmitNodeCollection.submitCustomGeometry` files non-blending geometry into the `solid`
   phase, `CustomFeatureRenderer.Submit.batchKey()` **is the `RenderType`**, and
   `SimpleFeatureRenderPhase.FeatureSubmits.batches` is a plain **`HashMap`** whose `values()`
   iteration order is the identity-hash order of those `RenderType` objects — arbitrary, and
   arbitrary *per JVM run*. Whichever batch happens to run second wins, and the body kept winning.
   Fix: submit both decals through **`collector.order(1)`**. Order buckets live in an
   `Int2ObjectAVLTreeMap` (`SubmitNodeStorage.submitsPerOrder`), so they are genuinely sorted, and
   this is vanilla's own decal idiom — `EyesLayer`, `HorseMarkingLayer`, `WolfCollarLayer`,
   `LivingEntityEmissiveLayer` and `SlimeOuterLayer` all use `order(1)` for exactly this. `order(int)`
   exists with the same signature on all four modern nodes; the 1.21.1 branch takes the parameter and
   ignores it. **The side overlay escaped this only by luck of being translucent** —
   `renderType.hasBlending()` routes it to `translucentCustomGeometry`, which is drained after
   `solid`. It is now at `order(1)` too, so it no longer depends on that.
   General rule for this renderer and any future one: **on 26.x, coplanar passes are only ordered if
   you order them.**

9. **The FML-JUnit unit-test environment landed 2026-08-01** (the task amendment 1 spun off).
   `neoForge { unitTest { enable(); testedMod = mods["buildcraftunofficial"] } }` in the shared
   build.gradle.kts covers all five nodes; the whole suite is green under it (26.1.2/26.2: 545,
   1.21.11/1.21.10: 531, 1.21.1: 528 tests, 0 failures). The `test` task now boots a real FML loader
   plus FULL mod loading (registries frozen, config loaded, BC subsystems initialized) before the
   JUnit engine — `Entity` class-loads, so amendment 1's constraint is gone (its design outcomes
   stay on their merits).
   - **26.x-only trap: `new ItemStack(...)` still dies "Components not bound yet" out of the box.**
     At 26.1 default item components moved off `Item` onto `Holder.Reference`, bound only during
     server resource load / client registry sync (`BuiltInRegistries.DATA_COMPONENT_INITIALIZERS` →
     `bindComponents`) — neither happens in a unit JVM. Fix: `VanillaSetupBaseTester` (revived with
     this real job; the old Bootstrap-calling body is gone) binds them once per JVM in `@BeforeAll`
     via `VanillaRegistries.createLookup()` — the same provider vanilla's `RegistryComponentsReport`
     datagen hands to the same `build()` call. **ItemStack-touching unit tests must extend it.**
     Sub-trap inside: NeoForge's `CommonHooks.validateComponent` (dev-only, gated on
     `SharedConstants.IS_RUNNING_IN_IDE`) rejects the datagen lookup's anonymous tag HolderSets
     (e.g. PROVIDES_BANNER_PATTERNS), so the bind runs with that flag temporarily false — the
     production path, where the check does not exist.
   - `FmlJunitEnvironmentTest` pins all of it loudly (Entity + EntityRobotBase class-load, ItemStack
     construction on every node, mod present) — the tripwire that was missing while
     VanillaSetupBaseTester rotted.
   - `ItemRobotComponentTester` STAYS a game test — it works, and component fidelity under real
     registry ops is still most honestly pinned there. No churn.

Copyright: EntityRobot and ItemRobot are PORTED files — carry the 7.1.x upstream notice verbatim
(recover from upstream/7.1.x, not neighbours). The renderer is a NEW file: it is written from
scratch against the modern submit model and takes only UV coordinates/pass ordering (facts, not
expression) from the 8.0.x reference — contributors header only, and do NOT copy src_old_license's
MMPL header onto anything. IRobotAccess, BCRoboticsEntities, UnreachableEntityCache, and all tests
are NEW — BuildCraftUnofficial contributors header only.
