/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.core.IZone;
import buildcraft.api.events.RobotEvent;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.inventory.AbstractInvItemTransactor;
import buildcraft.lib.inventory.filter.StackFilter;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.StackUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.ai.AIRobotMain;
import buildcraft.robotics.ai.AIRobotShutdown;
import buildcraft.robotics.ai.AIRobotSleep;
import buildcraft.robotics.item.ItemRobot;

/**
 * The robot. Ported from 7.1.x {@code buildcraft.robotics.EntityRobot}, rebased onto bare {@code Entity} and
 * re-plumbed onto {@link SynchedEntityData} + complex spawn data in place of the 7.1.x per-field command
 * channel.
 *
 * <p>Three things about this class are load-bearing and easy to get wrong later:
 * <ul>
 * <li><b>Removal must route through the registry.</b> A {@code discard()} that bypasses
 *     {@code killRobot}/{@code unloadRobot} leaks every station reservation and resource lock the robot held —
 *     the station then reads as permanently claimed by a robot that no longer exists, and nothing short of
 *     editing the save fixes it. {@link #remove(RemovalReason)} and {@link #onRemovedFromLevel()} are the two
 *     seams for that, and they are one-shot (guarded by {@link #registeredWithRegistry}) because both can fire
 *     for the same removal.</li>
 * <li><b>{@code ItemStack} does not override {@code equals()}</b>, and {@code SynchedEntityData.set} gates
 *     dirtiness on value inequality — so re-pushing the same mutated instance is a silent no-op and the client
 *     never sees an in-place inventory change, while unconditionally pushing a fresh {@code copy()} every tick
 *     re-sends five stacks at 20 Hz forever. {@link #pushStack} is the only correct shape: compare the live
 *     stack against the last value PUSHED (which is a copy, so it cannot be mutated behind our back) with
 *     {@code ItemStack.matches}, and only then set a fresh copy.</li>
 * <li><b>The transfer slots hold live references.</b> {@link #setInventoryStack} stores the caller's instance,
 *     exactly as 7.1.x did, so an AI may mutate a stack in place; {@link #pushStack} runs every tick and picks
 *     that up. Do not "fix" this by copying on the way in — half the AI framework mutates stacks it was
 *     handed.</li>
 * </ul>
 *
 * <p>Ph3 scope: there is no AI. {@link #mainAI} is null for the whole phase and every consumer null-guards it,
 * so a robot placed today registers, docks, charges, takes damage, drops correctly and idles. Ph4 fills the AI
 * tree in behind the seams already present here.
 */
public class EntityRobot extends EntityRobotBase implements IEntityWithComplexSpawn, IDebuggable {

    /** Wearable slots. Wearables never enter play before Ph9, but the list, its NBT and its spawn-sync land
     *  now so the save format never has to change. */
    public static final int MAX_WEARABLES = 8;

    /** The four transfer slots. */
    public static final int INVENTORY_SIZE = 4;

    /** Single-tank capacity, in mB. 7.1.x: four buckets. */
    public static final int TANK_CAPACITY = 4000;

    /** How long the hurt flash lasts, in ticks. 7.1.x set {@code hurtTime = maxHurtTime = 10}. */
    public static final int HURT_TIME_ON_DAMAGE = 10;

    /** Ceiling for the charging latch, and the level below which a fresh delivery tops it back up. 7.1.x used
     *  30/25 in ticks, and a station delivers every tick while a kinesis pipe is feeding it, so the latch sits
     *  pinned near the top for as long as charging continues and drains within ~1.5 s of it stopping. */
    private static final int MAX_TICKS_CHARGING = 30;
    private static final int TICKS_CHARGING_TOPUP_BELOW = 25;
    private static final int TICKS_CHARGING_PER_DELIVERY = 5;

    /** How far the body yaw may swing per client tick, in degrees. */
    private static final float CLIENT_YAW_STEP = 60F;

    /** Energy-particle rate divisor: one puff per this much accumulated spend-per-cycle. 7.1.x scaled this by
     *  the client's particle setting by hand; vanilla's own {@code ClientLevel.addParticle} limiter already
     *  applies that setting, so doing it twice would just make the effect vanish on "decreased". */
    private static final float ENERGY_FX_PER_PARTICLE = 100F;

    /** Fallen this far below the world floor and the robot is gone. 7.1.x hard-coded -128, which is above the
     *  floor of a modern world. */
    private static final int VOID_KILL_DEPTH = 64;

    // ── Synched data (Decision 4) ───────────────────────────────────────────
    // Replaces the six 7.1.x PacketCommand messages and the client's requestInitialization round-trip.
    // ENERGY_MJ is deliberately whole-MJ VAR_INT rather than a LONG of micro-joules: LONG exists on every
    // node, but a micro-joule accessor goes dirty every single tick where a whole-MJ one goes dirty at most
    // once per MJ.

    private static final EntityDataAccessor<String> BOARD_ID =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<ItemStack> ITEM_IN_USE =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> ITEM_ACTIVE =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AIM_YAW =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ENERGY_MJ =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SLEEPING =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ENERGY_SPEND =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HURT_TIME =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.INT);

    //? if >=1.21.11 {
    /** {@code EntityDataSerializers.VECTOR3}'s generic widened from {@code Vector3f} to {@code Vector3fc} at
     *  1.21.11. Only this declaration forks — reads are typed {@code Vector3fc} (which {@code Vector3f}
     *  implements) and writes construct a {@code Vector3f}, so both are version-neutral. */
    private static final EntityDataAccessor<Vector3fc> STEAM_DIR =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.VECTOR3);
    //?} else {
    /*// EntityDataSerializers.VECTOR3 is still generic over the concrete Vector3f on this line; reads and
    // writes elsewhere in the class stay neutral, so this declaration is the only forked site.
    private static final EntityDataAccessor<Vector3f> STEAM_DIR =
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.VECTOR3);*/
    //?}

    /** The four transfer slots, synched individually so the renderer can draw their contents. */
    private static final List<EntityDataAccessor<ItemStack>> INV = List.of(
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.ITEM_STACK),
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.ITEM_STACK),
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.ITEM_STACK),
            SynchedEntityData.defineId(EntityRobot.class, EntityDataSerializers.ITEM_STACK));

    /** The steam/exhaust direction a robot points at when it is not docked: straight down. */
    private static final Vector3f STEAM_DIR_DEFAULT = new Vector3f(0, -1, 0);

    // ── State ───────────────────────────────────────────────────────────────

    /** The robot's top-level AI. Null for the whole of Ph3 — every consumer must null-guard. */
    public AIRobot mainAI;

    private final MjBattery battery = new MjBattery(MAX_POWER);
    private final UnreachableEntityCache<Entity> unreachable =
            new UnreachableEntityCache<>(() -> level().getGameTime());

    /** One receiver instance for the robot's lifetime. {@code RobotStationPluggable} re-fetches it on every
     *  capability query (so identity is never cached outside), but the {@link #ticksCharging} latch it drives
     *  has to outlive any one query — hence one instance rather than one per call. */
    private final RobotChargeReceiver chargeReceiver = new RobotChargeReceiver(this);

    /** Live transfer slots. Deliberately the caller's own {@code ItemStack} references — see the class
     *  javadoc. {@link #INV} carries the copies that were last pushed to clients. */
    private final ItemStack[] inv = new ItemStack[INVENTORY_SIZE];

    /** The stack held out in front of the robot (7.1.x {@code itemInUse}), server-side and live. */
    private ItemStack itemInUse = ItemStack.EMPTY;

    private final List<ItemStack> wearables = new ArrayList<>();

    /** Items whose {@code inventoryTick} threw once. Per-robot and keyed on the {@code Item} OBJECT: 7.1.x
     *  kept a STATIC set of numeric ids, so one misbehaving item on one robot silently stopped that item
     *  ticking for every robot in the world until the next restart. */
    private final Set<Item> tickBlacklist = new HashSet<>();

    private FluidStack tank = FluidStack.EMPTY;

    private RedstoneBoardRobot board;

    private long robotId = NULL_ROBOT_ID;
    private DockingStation dockingStation;
    private DockingStation mainStation;

    /** Saved station coordinates, held between {@code readData} and the first tick that can resolve them: the
     *  {@code RobotRegistry} is a {@code SavedData} on the level, which a loading entity has no business
     *  touching from inside its own deserialisation. Null once resolved (or if there was nothing to resolve). */
    private BlockPos linkedStationPos;
    private Direction linkedStationSide;
    private BlockPos currentStationPos;
    private Direction currentStationSide;

    private boolean firstUpdateDone = false;
    private boolean registeredWithRegistry = false;
    private boolean shutdownReported = false;

    private int ticksCharging = 0;
    private int hurtTime = 0;
    private int energySpendPerCycle = 0;

    /** Client-side particle accumulator. */
    private float energyFX = 0;

    // ── Construction ────────────────────────────────────────────────────────

    /** The registry constructor — what {@code EntityType} calls on load and on the client. */
    public EntityRobot(EntityType<? extends EntityRobotBase> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        java.util.Arrays.fill(inv, ItemStack.EMPTY);
    }

    /** Convenience constructor for placement from {@code ItemRobot}. */
    public EntityRobot(Level level, RedstoneBoardRobotNBT boardNBT) {
        this(BCRoboticsEntities.ROBOT.get(), level);
        if (boardNBT != null) {
            setBoard(boardNBT.create(this));
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BOARD_ID, "");
        builder.define(ITEM_IN_USE, ItemStack.EMPTY);
        builder.define(ITEM_ACTIVE, false);
        builder.define(AIM_YAW, 0F);
        builder.define(AIM_PITCH, 0F);
        builder.define(ENERGY_MJ, 0);
        builder.define(SLEEPING, false);
        builder.define(ENERGY_SPEND, 0);
        builder.define(HURT_TIME, 0);
        builder.define(STEAM_DIR, new Vector3f(STEAM_DIR_DEFAULT));
        for (EntityDataAccessor<ItemStack> slot : INV) {
            builder.define(slot, ItemStack.EMPTY);
        }
    }

    // ── Persistence (Decision 5) ────────────────────────────────────────────
    // The CompoundTag/ValueInput fork is isolated here, on the concrete entity, and NOT on EntityRobotBase:
    // BCValueInput/BCValueOutput live in buildcraft.lib, and buildcraft.api must not gain a lib edge.
    // Note there is no super call — Entity declares these protected ABSTRACT, so the AbstractBCBlockEntity
    // "call super.writeData" shape does not transfer.

    @Override
    //? if >=1.21.10 {
    protected void readAdditionalSaveData(ValueInput input) {
        readData(new BCValueInput(input));
    }
    //?} else {
    /*protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag input) {
        readData(new BCValueInput(input));
    }*/
    //?}

    @Override
    //? if >=1.21.10 {
    protected void addAdditionalSaveData(ValueOutput output) {
        writeData(new BCValueOutput(output));
    }
    //?} else {
    /*protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag output) {
        writeData(new BCValueOutput(output));
    }*/
    //?}

    /** Version-neutral write hook. Keeps the 7.1.x key names ({@code battery}, {@code linkedStation},
     *  {@code currentStation}, {@code inv}, {@code wearables}, {@code mainAI}, {@code board}) with modern
     *  bodies. */
    protected void writeData(BCValueOutput out) {
        out.store("battery", CompoundTag.CODEC, battery.serializeNbt());
        out.putLong("robotId", robotId);

        writeStation(out, "linkedStation", mainStation, linkedStationPos, linkedStationSide);
        writeStation(out, "currentStation", dockingStation, currentStationPos, currentStationSide);

        // A fixed-size handler rather than 7.1.x's literal-bracket "inv[0]".."inv[3]" top-level keys: it
        // zero-fills, so a trailing populated slot cannot be dropped by a short list on read.
        ItemHandlerSimple handler = new ItemHandlerSimple(INVENTORY_SIZE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            handler.setStackInSlot(slot, inv[slot]);
        }
        out.store("inv", CompoundTag.CODEC, handler.serializeNBT());

        if (!itemInUse.isEmpty()) {
            // ItemStack.CODEC rejects an empty stack outright, so every single-stack write is guarded.
            out.store("itemInUse", ItemStack.CODEC, itemInUse);
            out.putBoolean("itemActive", isItemActive());
        }

        if (!tank.isEmpty()) {
            out.store("tank", FluidStack.CODEC, tank);
        }

        List<ItemStack> worn = new ArrayList<>();
        for (ItemStack wearable : wearables) {
            if (!wearable.isEmpty()) {
                worn.add(wearable);
            }
        }
        if (!worn.isEmpty()) {
            out.store("wearables", ItemStack.CODEC.listOf(), worn);
        }

        // The board id is written as a plain string as well as (potentially) an AI tree. That is an addition
        // to the 7.1.x format and it is what makes the board survive on its own: AIRobot.writeToNbt stamps
        // RobotManager.getAIRobotName(getClass()) into the tag, which is null for any board not registered as
        // an AI — and in Ph3 none are, because the AI registry lands with the AI tree in Ph4. Writing the tree
        // unguarded would NPE on the first save of the first robot.
        out.putString("boardId", getBoardId());

        if (mainAI != null && RobotManager.getAIRobotName(mainAI.getClass()) != null) {
            CompoundTag ai = new CompoundTag();
            mainAI.writeToNbt(ai);
            out.store("mainAI", CompoundTag.CODEC, ai);
        }
        // 7.1.x's dedup: the board is only written separately when it is not already inside the AI tree.
        if (board != null && (mainAI == null || mainAI.getDelegateAI() != board)
                && RobotManager.getAIRobotName(board.getClass()) != null) {
            CompoundTag boardTag = new CompoundTag();
            board.writeToNbt(boardTag);
            out.store("board", CompoundTag.CODEC, boardTag);
        }
    }

    /** Version-neutral read hook. See {@link #writeData}. */
    protected void readData(BCValueInput in) {
        in.read("battery", CompoundTag.CODEC).ifPresent(battery::deserializeNbt);
        robotId = in.getLongOr("robotId", NULL_ROBOT_ID);

        readStation(in, "linkedStation", (pos, side) -> {
            linkedStationPos = pos;
            linkedStationSide = side;
        });
        readStation(in, "currentStation", (pos, side) -> {
            currentStationPos = pos;
            currentStationSide = side;
        });

        ItemHandlerSimple handler = new ItemHandlerSimple(INVENTORY_SIZE);
        in.read("inv", CompoundTag.CODEC).ifPresent(handler::deserializeNBT);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            setInventoryStack(slot, handler.getStackInSlot(slot));
        }

        setItemInUse(in.read("itemInUse", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        setItemActive(in.getBooleanOr("itemActive", false));

        tank = in.read("tank", FluidStack.CODEC).orElse(FluidStack.EMPTY);

        wearables.clear();
        in.read("wearables", ItemStack.CODEC.listOf()).ifPresent(wearables::addAll);

        // AIRobot.loadAI returns null for an AI name nothing has registered (a removed addon, a renamed
        // class). 7.1.x stored that null straight into mainAI and NPE'd on the very next tick; here every
        // consumer null-guards it and the robot simply idles.
        in.read("mainAI", CompoundTag.CODEC).ifPresent(tag -> mainAI = AIRobot.loadAI(tag, this));

        RedstoneBoardRobot recovered = null;
        java.util.Optional<CompoundTag> boardTag = in.read("board", CompoundTag.CODEC);
        if (boardTag.isPresent()) {
            AIRobot loaded = AIRobot.loadAI(boardTag.get(), this);
            if (loaded instanceof RedstoneBoardRobot robotBoard) {
                recovered = robotBoard;
            }
        } else if (mainAI != null && mainAI.getDelegateAI() instanceof RedstoneBoardRobot robotBoard) {
            recovered = robotBoard;
        }
        if (recovered != null) {
            setBoard(recovered);
        } else {
            setBoardFromId(in.getStringOr("boardId", ""));
        }
    }

    /** Writes one station record: 7.1.x's key, a modern {@code pos} int[3] + {@code side} byte body. Prefers
     *  the live station and falls back to the coordinates read from disk, so a robot saved before its first
     *  tick (i.e. before it could resolve anything) does not lose its stations. */
    private static void writeStation(BCValueOutput out, String key, DockingStation live, BlockPos savedPos,
                                     Direction savedSide) {
        BlockPos pos = live != null ? live.getPos() : savedPos;
        Direction side = live != null ? live.side() : savedSide;
        if (pos == null || side == null) {
            // setMainStation(null) stores nothing at all rather than a sentinel — a sentinel is just a value
            // some later reader has to remember to special-case.
            return;
        }
        BCValueOutput sub = out.child(key);
        sub.putIntArray("pos", new int[] { pos.getX(), pos.getY(), pos.getZ() });
        sub.putByte("side", (byte) side.ordinal());
    }

    /** Reads one station record, or leaves the target untouched if it is absent or malformed.
     *
     * <p>The side byte is bounds-checked against {@code Direction.values()} deliberately: legacy saves wrote
     * {@code ForgeDirection.UNKNOWN} (6) for "no station", and any corrupt byte lands here too. Indexing
     * blind — which is what {@code DockingStation.readFromNbt} still does — turns that into an
     * ArrayIndexOutOfBoundsException during chunk load, i.e. a world that cannot be opened at all. */
    private static void readStation(BCValueInput in, String key, java.util.function.BiConsumer<BlockPos,
            Direction> target) {
        in.child(key).ifPresent(sub -> {
            int[] pos = sub.getIntArray("pos").orElse(new int[0]);
            if (pos.length != 3) {
                return;
            }
            byte side = sub.getByteOr("side", (byte) -1);
            Direction[] sides = Direction.values();
            if (side < 0 || side >= sides.length) {
                BCLog.logger.warn("[robots] Ignoring a robot's '" + key + "' record with an out-of-range side "
                        + "byte " + side + " — treating it as 'no station'");
                return;
            }
            target.accept(new BlockPos(pos[0], pos[1], pos[2]), sides[side]);
        });
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    // ── Spawn data ──────────────────────────────────────────────────────────

    /** Carries the wearables list. Uniform across all five nodes. Nothing can put a wearable on a robot until
     *  Ph9, so this is an empty list today — it ships now purely so the spawn payload never changes shape. */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(wearables.size());
        for (ItemStack wearable : wearables) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, wearable);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        wearables.clear();
        int count = additionalData.readVarInt();
        for (int i = 0; i < count && i < MAX_WEARABLES; i++) {
            wearables.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(additionalData));
        }
    }

    // ── Bare-Entity obligations ─────────────────────────────────────────────

    @Override
    public boolean isPickable() {
        return true;
    }

    //? if >=1.21.10 {
    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }
    //?} else {
    /*@Override
    public boolean canBeCollidedWith() {
        return false;
    }*/
    //?}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    //? if >=1.21.10 {
    /** Synthetic pick box: the entity type is 0.25 cubed, which puts the default box on the ground under the
     *  robot's centre. Robots are drawn as a cube centred on their position, so the clickable box is +/-0.25
     *  around that centre instead — otherwise a player aiming at a docked robot hits nothing. Reads no
     *  instance state, which matters: the Entity constructor calls this before any subclass field exists. */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        return new AABB(position.x - 0.25, position.y - 0.25, position.z - 0.25,
                position.x + 0.25, position.y + 0.25, position.z + 0.25);
    }
    //?} else {
    /*// Synthetic pick box: the entity type is 0.25 cubed, which puts the default box on the ground under the
    // robot's centre. Robots are drawn as a cube centred on their position, so the clickable box is +/-0.25
    // around that centre instead — otherwise a player aiming at a docked robot hits nothing. Reads no
    // instance state, which matters: the Entity constructor calls this before any subclass field exists.
    @Override
    protected AABB makeBoundingBox() {
        Vec3 position = this.position();
        return new AABB(position.x - 0.25, position.y - 0.25, position.z - 0.25,
                position.x + 0.25, position.y + 0.25, position.z + 0.25);
    }*/
    //?}

    //? if >=1.21.10 {
    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
        return hurtRobot(source, amount);
    }
    //?} else {
    /*@Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return hurtRobot(source, amount);
    }*/
    //?}

    /** Version-neutral damage body.
     *
     * <p>A robot is machinery, not prey, and 7.1.x's filter says so in three ways: a hit with no direct entity
     * behind it (fire, drowning, cactus, the whole environmental set) is ignored, a hit from a mob or a falling
     * block is ignored, and a DOCKED robot is flatly invulnerable — a robot parked on its station is part of
     * the machine.
     *
     * <p>Damage that does land is paid for out of the battery at {@link #DAMAGE_ENERGY_PER_POINT} per point,
     * and the survival rule is strictly greater than zero: a robot holding EXACTLY the debit is destroyed
     * rather than left sitting at a flat battery. That is 7.1.x behaviour and the drop path depends on it. */
    private boolean hurtRobot(net.minecraft.world.damagesource.DamageSource source, float amount) {
        Entity src = source.getDirectEntity();
        if (src == null || src instanceof FallingBlockEntity || src instanceof Enemy) {
            return false;
        }
        if (dockingStation != null) {
            return false;
        }
        if (level().isClientSide()) {
            return true;
        }

        // 7.1.x's shape, in long math: each worn item discounts the debit by a flat 30%. Upstream additionally
        // scaled armour by its own protection value; that hookup waits for wearable ACCEPTANCE (Ph9), since
        // until then nothing can put a wearable on a robot and this loop never runs.
        long perPoint = DAMAGE_ENERGY_PER_POINT;
        for (int worn = wearables.size(); worn > 0; worn--) {
            perPoint = perPoint * 7 / 10;
        }

        long debit = Math.round((double) amount * perPoint);
        if (battery.getStored() - debit > 0) {
            battery.setStored(battery.getStored() - debit);
            setHurtTime(HURT_TIME_ON_DAMAGE);
            return true;
        }
        // Deliberately NOT debited first: the robot dies holding the charge it had, and that charge rides the
        // dropped robot item.
        onRobotHit(true);
        return true;
    }

    //? if >=26.1 {
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return interactRobot(player, hand);
    }
    //?} else {
    /*@Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return interactRobot(player, hand);
    }*/
    //?}

    /** Version-neutral interaction body: fires {@code RobotEvent.Interact} and, for a sneaking player holding a
     *  wrench, peels the robot back down one layer at a time (last wearable, then the held item, then the whole
     *  robot). Wearable ACCEPTANCE — the other half of 7.1.x's interact — is Ph9. */
    private InteractionResult interactRobot(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        RobotEvent.Interact interact = new RobotEvent.Interact(this, player, stack);
        NeoForge.EVENT_BUS.post(interact);
        if (interact.isCanceled()) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown() && EntityUtil.isWrench(stack)) {
            RobotEvent.Dismantle dismantle = new RobotEvent.Dismantle(this, player);
            NeoForge.EVENT_BUS.post(dismantle);
            if (dismantle.isCanceled()) {
                return InteractionResult.PASS;
            }
            if (level().isClientSide()) {
                // Just the swing: EntityUtil.wrenchUsed wants a HitResult that an entity interaction has no
                // honest one of, and a BuildCraft wrench's own callback is about blocks.
                player.swing(hand);
            } else {
                onRobotHit(false);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    //? if <1.21.10 {
    /*// 1.21.1 only: snap to each synced position rather than running the classic fixed-step client lerp,
    // which visibly trails a robot that changes direction. 1.21.10+ use the adaptive InterpolationHandler.
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }*/
    //?}

    // ── Tick ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (!firstUpdateDone) {
            firstUpdateDone = true;
            firstUpdate();
        }

        if (ticksCharging > 0) {
            ticksCharging--;
        }

        if (level().isClientSide()) {
            clientTick();
        } else {
            serverTick();
        }

        tickCarriedItems();
    }

    /** Registry registration, once, on the first tick the robot is actually in a world. Deliberately not the
     *  constructor: a robot is constructed by the entity type before it has a level it belongs to, and on the
     *  client as well, where there is no registry at all. */
    private void firstUpdate() {
        IRobotRegistry registry = getRegistry();
        if (registry != null) {
            registry.registerRobot(this);
            registeredWithRegistry = true;
        }
    }

    private void serverTick() {
        resolveStations();

        if (dockingStation != null) {
            // The snap has to WIN against live motion, not merely coincide with a stationary robot: zero the
            // motion first, then place. A docked robot that drifts is a robot visibly off its station.
            setDeltaMovement(Vec3.ZERO);
            setPos(faceCentreOf(dockingStation));
        } else {
            Vec3 motion = getDeltaMovement();
            if (motion.lengthSqr() > 1.0E-12) {
                setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
            }
        }

        if (getY() < level().getMinY() - VOID_KILL_DEPTH) {
            BCLog.logger.info("[robots] Destroying robot " + robotId + " — fallen into the void");
            // discard() routes through remove(RemovalReason), which is what frees the reservations.
            discard();
            return;
        }

        if (mainAI != null && (mainStation == null || mainStation.isInitialized())) {
            mainAI.cycle();
            AIRobot active = mainAI.getActiveAI();
            // Kept on 7.1.x's RF-equivalent scale (10 RF = 1 MJ) so ENERGY_FX_PER_PARTICLE still means the
            // same particle rate it did upstream: the default AI cost of MJ/10 comes out as 1, exactly as the
            // 1 RF it was.
            energySpendPerCycle = active == null ? 0 : (int) (active.getPowerCost() * 10 / MjAPI.MJ);
        }

        // Synched pushes. Every one of these is value-compared by SynchedEntityData before it goes dirty,
        // EXCEPT the ItemStacks — see pushStack.
        entityData.set(SLEEPING, isSleeping() && ticksCharging == 0);
        entityData.set(ENERGY_MJ, (int) (battery.getStored() / MjAPI.MJ));
        entityData.set(ENERGY_SPEND, energySpendPerCycle);
        if (hurtTime > 0) {
            setHurtTime(hurtTime - 1);
        }
        pushStack(ITEM_IN_USE, itemInUse);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            pushStack(INV.get(slot), inv[slot]);
        }
    }

    private void clientTick() {
        float step = Mth.wrapDegrees(getAimYaw() - getYRot());
        step = Mth.clamp(step, -CLIENT_YAW_STEP, CLIENT_YAW_STEP);
        setYRot(getYRot() + step);

        energyFX += getEnergySpend();
        if (energyFX >= ENERGY_FX_PER_PARTICLE) {
            energyFX = 0;
            Vector3fc dir = getSteamDirection();
            level().addParticle(ParticleTypes.CLOUD,
                    getX() + dir.x() * 0.25, getY() + dir.y() * 0.25, getZ() + dir.z() * 0.25,
                    dir.x() * 0.05, dir.y() * 0.05, dir.z() * 0.05);
        }
    }

    /** Re-resolves the two docking stations from the {@code RobotRegistry}, and reacts when the linked one is
     *  gone.
     *
     *  <p>A robot whose linked station has vanished (the pipe was broken, the chunk it lived in was reshaped)
     *  is orphaned and 7.1.x shut it down. A robot that never had one at all is a different case — that cannot
     *  happen in play, since the only survival route into the world is {@code ItemRobot.useOn} taking a station
     *  as main, but it is exactly what a test or a {@code /summon} produces, and shutting those down (or worse,
     *  logging about them every tick) would be noise. */
    private void resolveStations() {
        IRobotRegistry registry = getRegistry();
        if (registry == null) {
            return;
        }

        if (mainStation == null && linkedStationPos != null) {
            mainStation = registry.getStation(linkedStationPos, linkedStationSide);
            if (mainStation == null) {
                shutdown("no docking station");
            } else {
                linkedStationPos = null;
                linkedStationSide = null;
            }
        }

        if (mainStation != null && mainStation.robotTaking() != this) {
            if (mainStation.robotIdTaking() == robotId) {
                // The station still believes it belongs to this robot id but is holding a stale entity — the
                // previous instance was unloaded without being released. Drop the stale reference and let it
                // re-resolve to us.
                BCLog.logger.warn("[robots] Robot " + robotId + " was not properly unloaded");
                mainStation.invalidateRobotTakingEntity();
            }
            if (mainStation.robotTaking() != this) {
                shutdown("wrong docking station");
            }
        }

        if (dockingStation == null && currentStationPos != null) {
            dockingStation = registry.getStation(currentStationPos, currentStationSide);
            if (dockingStation != null) {
                currentStationPos = null;
                currentStationSide = null;
            }
        }
    }

    /** Ticks the four transfer slots and the held item, exactly as 7.1.x did, so an item that needs a tick to
     *  behave (a clock, a compass, a mod's charging tool) still gets one while a robot is carrying it. An item
     *  whose tick throws is blacklisted on this robot rather than being allowed to throw once per tick
     *  forever. */
    private void tickCarriedItems() {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            tickItem(inv[slot], slot, false);
        }
        tickItem(itemInUse, 0, true);
    }

    private void tickItem(ItemStack stack, int slot, boolean held) {
        if (stack.isEmpty() || tickBlacklist.contains(stack.getItem())) {
            return;
        }
        try {
            //? if >=1.21.10 {
            // inventoryTick became server-only (and slot-typed) at 1.21.10; a null EquipmentSlot is the
            // documented "not worn" value.
            if (level() instanceof net.minecraft.server.level.ServerLevel server) {
                stack.getItem().inventoryTick(stack, server, this, null);
            }
            //?} else {
            /*stack.getItem().inventoryTick(stack, level(), this, slot, held);*/
            //?}
        } catch (Throwable e) {
            tickBlacklist.add(stack.getItem());
            BCLog.logger.warn("[robots] " + stack.getItem() + " threw while ticking inside a robot; it will "
                    + "not be ticked again by this robot", e);
        }
    }

    /** The point a robot docked at {@code station} sits on: the block centre pushed half a block out along the
     *  mounting face. {@code ItemRobot.useOn} places a fresh robot with the same math. */
    private static Vec3 faceCentreOf(DockingStation station) {
        BlockPos pos = station.getPos();
        Direction side = station.side();
        return new Vec3(
                pos.getX() + 0.5 + side.getStepX() * 0.5,
                pos.getY() + 0.5 + side.getStepY() * 0.5,
                pos.getZ() + 0.5 + side.getStepZ() * 0.5);
    }

    // ── Removal / registry lifecycle ────────────────────────────────────────

    /** Every removal has to reach the registry or the robot's station reservations and resource locks leak.
     *  A destructive removal is a kill (release everything, forcibly); a chunk/player unload is an unload (keep
     *  the reservations, just forget the entity), which is what lets a robot come back with its station still
     *  its own. */
    @Override
    public void remove(RemovalReason reason) {
        boolean unload = reason == RemovalReason.UNLOADED_TO_CHUNK
                || reason == RemovalReason.UNLOADED_WITH_PLAYER
                || reason == RemovalReason.CHANGED_DIMENSION;
        deregister(unload);
        super.remove(reason);
    }

    /** The chunk-unload path — {@code unloadRobot}, never {@code killRobot}. Both this and
     *  {@link #remove(RemovalReason)} can fire for one removal; {@link #deregister} is one-shot so the second
     *  one is inert. */
    @Override
    public void onRemovedFromLevel() {
        deregister(true);
        super.onRemovedFromLevel();
    }

    private void deregister(boolean unload) {
        if (!registeredWithRegistry) {
            return;
        }
        IRobotRegistry registry = getRegistry();
        if (registry == null) {
            return;
        }
        registeredWithRegistry = false;
        if (unload) {
            registry.unloadRobot(this);
        } else {
            registry.killRobot(this);
        }
    }

    /** Drops the robot back into the world as items and removes it. */
    public void convertToItems() {
        if (level().isClientSide() || isRemoved()) {
            return;
        }
        if (mainAI != null) {
            mainAI.abort();
        }
        for (ItemStack drop : getDrops()) {
            dropStack(drop);
        }
        // discard() -> remove(DISCARDED) -> killRobot: the reservations are freed by the removal path, not
        // here, so there is exactly one place that can leak them.
        discard();
    }

    /** The stacks {@link #convertToItems()} spills: the robot item carrying board and charge, the held item,
     *  the four transfer slots and the wearables. Deliberately NOT the tank — 7.1.x voided a dying robot's
     *  fluid, and the only alternative (spilling it) would make a robot a fluid-duplication vector, since
     *  nothing here can put the fluid back into a container. */
    public List<ItemStack> getDrops() {
        List<ItemStack> drops = new ArrayList<>();
        RedstoneBoardRobot robotBoard = getBoard();
        String boardId = robotBoard == null ? null : robotBoard.getNBTHandler().getID();
        drops.add(ItemRobot.createRobotStack(boardId, battery.getStored()));
        if (!itemInUse.isEmpty()) {
            drops.add(itemInUse);
        }
        for (ItemStack stack : inv) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        drops.addAll(wearables);
        return drops;
    }

    private void dropStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        //? if >=1.21.10 {
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            spawnAtLocation(server, stack);
        }
        //?} else {
        /*spawnAtLocation(stack);*/
        //?}
    }

    /** 7.1.x's "something hit me" funnel. An attack that the battery could not pay for converts the whole
     *  robot; a wrench peels one layer at a time so a player can recover a robot's cargo without destroying
     *  it. */
    private void onRobotHit(boolean attacked) {
        if (level().isClientSide()) {
            return;
        }
        if (attacked) {
            convertToItems();
        } else if (!wearables.isEmpty()) {
            dropStack(wearables.remove(wearables.size() - 1));
        } else if (!itemInUse.isEmpty()) {
            ItemStack held = itemInUse;
            setItemInUse(ItemStack.EMPTY);
            dropStack(held);
        } else {
            convertToItems();
        }
    }

    // ── Sleep / charging (Decision 6) ───────────────────────────────────────

    /** 7.1.x called this {@code isActive()} and returned true when the robot was ASLEEP — the inverse of its
     *  name. Renamed here to say what it means. True when the deepest active AI is a sleep or shutdown AI —
     *  the Ph4 implementation of the Ph3-stub javadoc, matching 7.1.x's
     *  {@code mainAI.getActiveAI() instanceof AIRobotSleep || instanceof AIRobotShutdown}. Only ever called
     *  server-side (the SLEEPING synced flag pushes the result to the client). */
    public boolean isSleeping() {
        if (mainAI == null) {
            return false;
        }
        AIRobot active = mainAI.getActiveAI();
        return active instanceof AIRobotSleep || active instanceof AIRobotShutdown;
    }

    @Override
    public RobotChargeReceiver getChargeReceiver() {
        return chargeReceiver;
    }

    /** Called by {@link RobotChargeReceiver} for every REAL (non-simulated) delivery. A delivery big enough to
     *  notice tops the latch up, so a robot on a charging station looks awake for as long as the charge keeps
     *  arriving and for ~1.5 s afterwards. */
    void onChargeReceived(long accepted) {
        if (accepted > RobotChargeReceiver.CHARGE_DETECT_THRESHOLD
                && ticksCharging <= TICKS_CHARGING_TOPUP_BELOW) {
            ticksCharging = Math.min(MAX_TICKS_CHARGING, ticksCharging + TICKS_CHARGING_PER_DELIVERY);
        }
    }

    /** Read-only view of the charging latch, for the sleep indicator and for tests that need to prove a
     *  simulated transfer left it alone. */
    public int getTicksCharging() {
        return ticksCharging;
    }

    // ── Energy ──────────────────────────────────────────────────────────────

    @Override
    public MjBattery getBattery() {
        return battery;
    }

    // ── Synched reads (renderer + tests) ────────────────────────────────────

    public String getBoardId() {
        return entityData.get(BOARD_ID);
    }

    /** The stack held out in front of the robot (7.1.x {@code itemInUse}). */
    @Override
    public ItemStack getHeldItem() {
        return level().isClientSide() ? entityData.get(ITEM_IN_USE) : itemInUse;
    }

    public boolean isItemActive() {
        return entityData.get(ITEM_ACTIVE);
    }

    /** Whole MJ, 0..10000 — the renderer's charge fraction is this over
     *  {@code MAX_POWER / MjAPI.MJ}. */
    public int getEnergyMj() {
        return entityData.get(ENERGY_MJ);
    }

    /** Drives the energy-particle rate. */
    public int getEnergySpend() {
        return entityData.get(ENERGY_SPEND);
    }

    public int getHurtTime() {
        return entityData.get(HURT_TIME);
    }

    /** Writes the hurt flash to the SYNCHED accessor immediately rather than leaving it to the next tick push:
     *  a 10-tick flash pushed a tick late is a frame of lie, and for a one-tick flash it would be the whole
     *  effect. */
    private void setHurtTime(int ticks) {
        hurtTime = ticks;
        entityData.set(HURT_TIME, ticks);
    }

    /** Typed {@code Vector3fc} on every node — {@code Vector3f} implements it, so only the accessor
     *  declaration forks. */
    public Vector3fc getSteamDirection() {
        return entityData.get(STEAM_DIR);
    }

    /** Takes a {@code Vec3} on purpose: 7.1.x stored three ints and truncated every normalised diagonal
     *  straight to zero, so a robot aiming diagonally emitted its exhaust from its own centre. */
    public void setSteamDirection(Vec3 direction) {
        Vec3 normalised = direction.lengthSqr() < 1.0E-9 ? new Vec3(0, -1, 0) : direction.normalize();
        entityData.set(STEAM_DIR,
                new Vector3f((float) normalised.x, (float) normalised.y, (float) normalised.z));
    }

    /** The client-visible sleep flag — {@link #isSleeping()} gated on not-currently-charging. */
    public boolean isSleepingClient() {
        return entityData.get(SLEEPING);
    }

    /** Resolved from {@link #getBoardId()} through the board registry. Entity textures are direct file paths,
     *  never atlas sprites. */
    public Identifier getTexture() {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return null;
        }
        RedstoneBoardNBT<?> nbt = registry.getRedstoneBoard(getBoardId());
        if (nbt instanceof RedstoneBoardRobotNBT robotBoard
                && robotBoard.getRobotTexture() instanceof Identifier texture) {
            return texture;
        }
        return null;
    }

    /** Pushes {@code live} into {@code accessor} only when it differs from what was last pushed.
     *
     * <p>This is the whole {@code ItemStack} identity trap in one method. {@code ItemStack} has no
     * {@code equals()}, so {@code SynchedEntityData.set}'s own dirty check compares references: re-setting the
     * SAME (mutated) instance is a silent no-op the client never learns about, and setting a fresh
     * {@code copy()} unconditionally goes dirty every single tick. The value already in the accessor is always
     * a copy we made, so it cannot have been mutated behind our back — which makes it a sound baseline for
     * {@code ItemStack.matches}. */
    private void pushStack(EntityDataAccessor<ItemStack> accessor, ItemStack live) {
        if (!ItemStack.matches(entityData.get(accessor), live)) {
            entityData.set(accessor, live.copy());
        }
    }

    // ── Aiming / held item ──────────────────────────────────────────────────

    @Override
    public float getAimYaw() {
        return entityData.get(AIM_YAW);
    }

    @Override
    public float getAimPitch() {
        return entityData.get(AIM_PITCH);
    }

    @Override
    public void aimItemAt(float yaw, float pitch) {
        entityData.set(AIM_YAW, yaw);
        entityData.set(AIM_PITCH, pitch);
    }

    /** 7.1.x's formula, verbatim: {@code atan2(dz, dx)} for the yaw and {@code -atan2(dy, horizontal)} for the
     *  pitch. The 1.12-era port swapped those arguments, which is a bug rather than a fix — swapping them
     *  mirrors every aim through the diagonal. */
    @Override
    public void aimItemAt(BlockPos pos) {
        int deltaX = pos.getX() - Mth.floor(getX());
        int deltaY = pos.getY() - Mth.floor(getY());
        int deltaZ = pos.getZ() - Mth.floor(getZ());

        float yaw = getAimYaw();
        if (deltaX != 0 || deltaZ != 0) {
            yaw = (float) (Math.atan2(deltaZ, deltaX) * 180D / Math.PI) + 180F;
        }
        double horizontal = Math.sqrt((double) deltaX * deltaX + (double) deltaZ * deltaZ);
        float pitch = (float) (-(Math.atan2(deltaY, horizontal) * 180D / Math.PI));

        setSteamDirection(new Vec3(deltaX, deltaY, deltaZ));
        aimItemAt(yaw, pitch);
    }

    /** Null-tolerant: 7.1.x cleared the held item by passing null, and the [1.12] dismantle fix relies on that
     *  call still working. */
    @Override
    public void setItemInUse(ItemStack stack) {
        itemInUse = stack == null ? ItemStack.EMPTY : stack;
        pushStack(ITEM_IN_USE, itemInUse);
    }

    @Override
    public void setItemActive(boolean active) {
        if (active != isItemActive()) {
            entityData.set(ITEM_ACTIVE, active);
            if (!active) {
                setSteamDirection(new Vec3(0, -1, 0));
            }
        }
    }

    // ── Laser (API kept, render branch deliberately not ported) ─────────────

    /** Nothing in 7.1.x ever called the laser setters; they are kept for addon surface and for NBT shape
     *  stability, and the render branch is deliberately not ported. Aiming the exhaust at the target is the one
     *  part that costs nothing and is visible. */
    public void setLaserDestination(float x, float y, float z) {
        setSteamDirection(new Vec3(x - getX(), y - getY(), z - getZ()));
    }

    /** See {@link #setLaserDestination}. */
    public void showLaser() {
    }

    /** See {@link #setLaserDestination}. */
    public void hideLaser() {
    }

    // ── Docking ─────────────────────────────────────────────────────────────

    @Override
    public void dock(DockingStation station) {
        this.dockingStation = station;
        this.currentStationPos = null;
        this.currentStationSide = null;
        if (station != null) {
            Direction side = station.side();
            setSteamDirection(new Vec3(side.getStepX(), side.getStepY(), side.getStepZ()));
        }
    }

    @Override
    public void undock() {
        if (dockingStation != null) {
            dockingStation.release(this);
            dockingStation = null;
            currentStationPos = null;
            currentStationSide = null;
            setSteamDirection(new Vec3(0, -1, 0));
        }
    }

    @Override
    public DockingStation getDockingStation() {
        return dockingStation;
    }

    @Override
    public DockingStation getLinkedStation() {
        return mainStation;
    }

    /** Releasing the previous main station is deliberately {@code unsafeRelease}: {@code release} is a no-op on
     *  a station that is somebody's MAIN, which is exactly the case here, so the plain call would silently
     *  leave the old station claimed forever. */
    @Override
    public void setMainStation(DockingStation station) {
        if (mainStation != null && mainStation != station) {
            mainStation.unsafeRelease(this);
        }
        mainStation = station;
        linkedStationPos = null;
        linkedStationSide = null;
    }

    // ── Registry / identity ─────────────────────────────────────────────────

    @Override
    public long getRobotId() {
        return robotId;
    }

    @Override
    public void setUniqueRobotId(long robotId) {
        this.robotId = robotId;
    }

    /** Null on the client and before the entity has a level: the {@code RobotRegistry} is server-side
     *  {@code SavedData} and asking for it from the client throws outright. */
    @Override
    public IRobotRegistry getRegistry() {
        Level level = level();
        if (level == null || level.isClientSide() || RobotManager.registryProvider == null) {
            return null;
        }
        return RobotManager.registryProvider.getRegistry(level);
    }

    @Override
    public void releaseResources() {
        IRobotRegistry registry = getRegistry();
        if (registry != null) {
            registry.releaseResources(this);
        }
    }

    /** Starts {@link AIRobotShutdown}, which undocks the robot and lets it fall to the ground, parking it where
     *  it stops. Reached from the tick loop for an orphaned or mis-stationed robot, so it is guarded against
     *  re-starting once a shutdown AI is already running — that is what keeps the log line at one, not a
     *  one-shot flag. With no controller (Ph3 state, no board yet) it falls back to logging once and idling. */
    public void shutdown(String reason) {
        if (mainAI instanceof AIRobotMain main) {
            if (!(main.getDelegateAI() instanceof AIRobotShutdown)) {
                BCLog.logger.info("[robots] Shutting down robot " + robotId + " — " + reason);
                main.startDelegateAI(new AIRobotShutdown(this));
            }
        } else if (!shutdownReported) {
            shutdownReported = true;
            BCLog.logger.info("[robots] Shutting down robot " + robotId + " — " + reason);
        }
    }

    // ── AI (Ph4) ────────────────────────────────────────────────────────────

    /** {@code AIRobotMain} owns the override slot; the entity just fronts it. */
    public AIRobot getOverridingAI() {
        if (mainAI instanceof AIRobotMain main) {
            return main.getOverridingAI();
        }
        return null;
    }

    /** Hands an overriding AI to the controller. {@code AIRobotMain} starts it on the next cycle unless a
     *  higher-priority shutdown or recharge has taken over. */
    public void overrideAI(AIRobot ai) {
        if (mainAI instanceof AIRobotMain main) {
            main.setOverridingAI(ai);
        }
    }

    /** Never null once a board registry exists: an un-boarded robot lazily adopts the empty board, which is
     *  what gives it a skin and what a dropped robot item records. */
    @Override
    public RedstoneBoardRobot getBoard() {
        if (board == null) {
            setBoardFromId(getBoardId());
        }
        return board;
    }

    private void setBoard(RedstoneBoardRobot robotBoard) {
        board = robotBoard;
        if (robotBoard != null) {
            entityData.set(BOARD_ID, robotBoard.getNBTHandler().getID());
            // Ph4: the board is driven by a top-level AIRobotMain that delegates to it every tick. A freshly
            // placed robot has no AI tree yet, so create the controller the first time a board is set (the
            // empty board counts). AIRobotMain.update() picks the board up as its delegate on the first cycle.
            // Loaded robots keep whatever tree their save restored (mainAI is already non-null there).
            if (mainAI == null && level() != null && !level().isClientSide()) {
                mainAI = new AIRobotMain(this);
                mainAI.start();
            }
        }
    }

    /** Resolves {@code id} through the board registry — an unknown or empty id yields the empty board, which
     *  is {@code ImplRedstoneBoardRegistry}'s documented fallback. */
    private void setBoardFromId(String id) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return;
        }
        RedstoneBoardNBT<?> nbt = registry.getRedstoneBoard(id == null ? "" : id);
        if (nbt instanceof RedstoneBoardRobotNBT robotBoardNBT) {
            setBoard(robotBoardNBT.create(this));
        }
    }

    @Override
    public boolean isMoving() {
        return getDeltaMovement().lengthSqr() > 1.0E-12;
    }

    // ── Zones (Ph6) ─────────────────────────────────────────────────────────

    /** Ph6 — the zone comes off the linked station's {@code ActionRobotWorkInArea} statements, which need the
     *  statement/gate wiring that phase brings. */
    @Override
    public IZone getZoneToWork() {
        return null;
    }

    /** Ph6 — see {@link #getZoneToWork()}. */
    @Override
    public IZone getZoneToLoadUnload() {
        return null;
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    @Override
    public int getInventorySize() {
        return INVENTORY_SIZE;
    }

    @Override
    public ItemStack getInventoryStack(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }
        return level().isClientSide() ? entityData.get(INV.get(slot)) : inv[slot];
    }

    /** Stores the caller's own instance (7.1.x semantics — AIs mutate the stacks they were handed) and pushes
     *  a copy to clients through the {@link #pushStack} gate. */
    @Override
    public void setInventoryStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }
        inv[slot] = stack == null ? ItemStack.EMPTY : stack;
        pushStack(INV.get(slot), inv[slot]);
    }

    /** The transactor the load/unload/fetch AIs insert and extract through (D2): a thin adapter over the
     *  SAME {@link #inv} array the slot accessors already serve, one instance per robot. The two write
     *  paths differ deliberately — {@link #setInventoryStack} stores the caller's own instance (7.1.x
     *  live-instance semantics for AIs that keep mutating a stack they were handed), while the transactor
     *  copies: {@link AbstractInvItemTransactor} dry-runs with the very reference it then commits, so the
     *  offered stack may never be mutated, let alone stored live. */
    private final IItemTransactor transactor = new RobotTransactor();

    @Override
    public IItemTransactor getTransactor() {
        return transactor;
    }

    /** {@link AbstractInvItemTransactor} over {@link #inv}. Standard per-slot merge on insert and a plain
     *  filter+count on extract; {@code setInventoryStack} pushes the mutated slot to clients on every real
     *  change. {@code insert} never mutates the stack it is handed — accepted and leftover counts travel in
     *  copies, so a simulated call leaves both the caller's stack and the inventory untouched, and the
     *  base class's simulate-then-commit chain in {@code insertAllAtOnce} stays exact. */
    private class RobotTransactor extends AbstractInvItemTransactor {

        @Override
        protected ItemStack insert(int slot, @Nonnull ItemStack stack, boolean simulate) {
            ItemStack current = inv[slot];
            if (current.isEmpty()) {
                int accepted = Math.min(stack.getCount(), stack.getMaxStackSize());
                if (!simulate) {
                    setInventoryStack(slot, stack.copyWithCount(accepted));
                }
                return stack.copyWithCount(stack.getCount() - accepted);
            }
            if (StackUtil.canMerge(current, stack)) {
                int amount = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if (amount > 0) {
                    if (!simulate) {
                        ItemStack merged = current.copy();
                        merged.grow(amount);
                        setInventoryStack(slot, merged);
                    }
                    return stack.copyWithCount(stack.getCount() - amount);
                }
            }
            return stack;
        }

        @Override
        protected ItemStack extract(int slot, IStackFilter filter, int min, int max, boolean simulate) {
            ItemStack current = inv[slot];
            if (current.isEmpty() || !filter.matches(current)) {
                return ItemStack.EMPTY;
            }
            int amount = Math.min(current.getCount(), max);
            if (amount < min) {
                return ItemStack.EMPTY;
            }
            ItemStack result = current.copy();
            result.setCount(amount);
            if (!simulate) {
                ItemStack remaining = current.copy();
                remaining.shrink(amount);
                setInventoryStack(slot, remaining);
            }
            return result;
        }

        @Override
        protected int size() {
            return INVENTORY_SIZE;
        }

        @Override
        protected boolean isEmpty(int slot) {
            return inv[slot].isEmpty();
        }
    }

    @Override
    public boolean containsItems() {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!getInventoryStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasFreeSlot() {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (getInventoryStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Only a docked robot accepts items, and only from a block adjacent to the station it is docked at —
     *  otherwise anything with an inventory anywhere could push cargo into a passing robot. What it then does
     *  with them is the active AI's decision; with no AI (Ph3) the stack comes straight back. */
    @Override
    public ItemStack receiveItem(BlockEntity tile, ItemStack stack) {
        if (dockingStation == null || tile == null || mainAI == null) {
            return stack;
        }
        if (dockingStation.getPos().distManhattan(tile.getBlockPos()) != 1) {
            return stack;
        }
        return mainAI.getActiveAI().receiveItem(stack);
    }

    /** Ph9 — the list, its NBT, its spawn-sync and its drop path all land in Ph3 so the save format never
     *  changes, but nothing can put a wearable on a robot until Ph9. */
    public List<ItemStack> getWearables() {
        return Collections.unmodifiableList(wearables);
    }

    // ── Pathing hints ───────────────────────────────────────────────────────

    @Override
    public void unreachableEntityDetected(Entity entity) {
        unreachable.unreachableDetected(entity);
    }

    @Override
    public boolean isKnownUnreachable(Entity entity) {
        return unreachable.isKnownUnreachable(entity);
    }

    // ── Debug ───────────────────────────────────────────────────────────────

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        RedstoneBoardRobot robotBoard = getBoard();
        left.add("Robot " + robotId + " (" + (robotBoard == null ? "no board"
                : robotBoard.getNBTHandler().getID()) + ")");
        left.add("Battery = " + battery.getDebugString());
        left.add(String.format("Position = %.2f, %.2f, %.2f", getX(), getY(), getZ()));
        left.add("Linked station = " + mainStation);
        left.add("Docked at = " + dockingStation);
        right.add("AI tree:");
        AIRobot ai = mainAI;
        while (ai != null) {
            String name = RobotManager.getAIRobotName(ai.getClass());
            right.add("- " + (name == null ? ai.getClass().getSimpleName() : name)
                    + " (" + MjAPI.formatMj(ai.getPowerCost()) + " MJ/t)");
            if (ai instanceof IDebuggable debuggable) {
                debuggable.getDebugInfo(left, right, side);
            }
            ai = ai.getDelegateAI();
        }
    }

    // ── Fluids ──────────────────────────────────────────────────────────────
    // Single 4000 mB tank behind the IFluidHandlerAdv fork (Transfer API >=1.21.10, classic IFluidHandler on
    // 1.21.1). The tank is single-fluid: once it holds something, only more of the same goes in.
    //
    // The Transfer API's transaction contract is deliberately not honoured (changes commit immediately rather
    // than on commit) — that is the established shape everywhere else in this codebase, PipeFlowFluids
    // included, because BuildCraft manages its own state and has no snapshot to roll back to.

    /** @return how much of {@code resource} the tank can take, applying it when {@code execute}. */
    private int fillTank(FluidStack resource, boolean execute) {
        if (resource.isEmpty()) {
            return 0;
        }
        if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank, resource)) {
            return 0;
        }
        int accepted = Math.min(resource.getAmount(), TANK_CAPACITY - tank.getAmount());
        if (accepted <= 0) {
            return 0;
        }
        if (execute) {
            if (tank.isEmpty()) {
                tank = resource.copyWithAmount(accepted);
            } else {
                tank.setAmount(tank.getAmount() + accepted);
            }
        }
        return accepted;
    }

    /** @return how much came out, applying it when {@code execute}. */
    private int drainTank(int maxDrain, boolean execute) {
        if (tank.isEmpty() || maxDrain <= 0) {
            return 0;
        }
        int drained = Math.min(maxDrain, tank.getAmount());
        if (execute) {
            if (drained >= tank.getAmount()) {
                tank = FluidStack.EMPTY;
            } else {
                tank.setAmount(tank.getAmount() - drained);
            }
        }
        return drained;
    }

    //? if >=1.21.10 {
    @Override
    public int size() {
        return 1;
    }

    @Override
    public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
        if (index != 0 || tank.isEmpty()) {
            return net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }
        return net.neoforged.neoforge.transfer.fluid.FluidResource.of(tank);
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == 0 ? tank.getAmount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        return index == 0 ? TANK_CAPACITY : 0;
    }

    @Override
    public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        if (index != 0 || resource.isEmpty()) {
            return false;
        }
        return tank.isEmpty() || FluidStack.isSameFluidSameComponents(tank, resource.toStack(1));
    }

    @Override
    public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                      net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        return index == 0 ? fillTank(resource.toStack(amount), true) : 0;
    }

    @Override
    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                       net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        if (index != 0 || tank.isEmpty() || !FluidStack.isSameFluidSameComponents(tank, resource.toStack(1))) {
            return 0;
        }
        return drainTank(amount, true);
    }

    @Override
    public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain,
                       net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
        if (tank.isEmpty() || !filter.matches(tank)) {
            return 0;
        }
        return drainTank(maxDrain, true);
    }
    //?} else {
    /*@Override
    public int getTanks() {
        return 1;
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
        return tank == 0 ? this.tank.copy() : net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == 0 ? TANK_CAPACITY : 0;
    }

    @Override
    public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
        if (tank != 0 || stack.isEmpty()) {
            return false;
        }
        return this.tank.isEmpty() || FluidStack.isSameFluidSameComponents(this.tank, stack);
    }

    @Override
    public int fill(net.neoforged.neoforge.fluids.FluidStack resource,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        return fillTank(resource, action.execute());
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        if (tank.isEmpty() || !FluidStack.isSameFluidSameComponents(tank, resource)) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        return drain(resource.getAmount(), action);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        if (tank.isEmpty()) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        net.neoforged.neoforge.fluids.FluidStack drained = tank.copy();
        int amount = drainTank(maxDrain, action.execute());
        if (amount <= 0) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }
        drained.setAmount(amount);
        return drained;
    }

    @Override
    public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain, boolean simulate) {
        if (tank.isEmpty() || !filter.matches(tank)) {
            return 0;
        }
        return drainTank(maxDrain, !simulate);
    }*/
    //?}
}
