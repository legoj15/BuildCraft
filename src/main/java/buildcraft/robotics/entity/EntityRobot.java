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
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjBattery;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;

import buildcraft.robotics.BCRoboticsEntities;

/**
 * The robot. Ported from 7.1.x {@code buildcraft.robotics.EntityRobot}, rebased onto bare {@code Entity} and
 * re-plumbed onto {@link SynchedEntityData} + complex spawn data in place of the 7.1.x per-field command
 * channel.
 *
 * <p><b>Ph3 skeleton — the public surface is final, the bodies are not.</b> Every method below is either a
 * no-op or returns a default; docking snap, NBT bodies, damage handling, the synched pushes and the AI cycle
 * all land with the implementation. Tests written against this class are expected to be red.
 *
 * <p>Two things about this class are load-bearing and easy to get wrong later:
 * <ul>
 * <li><b>Removal must route through the registry.</b> A {@code discard()} that bypasses
 *     {@code killRobot}/{@code unloadRobot} leaks every station reservation and resource lock the robot held.
 *     {@link #remove(RemovalReason)} and {@link #onRemovedFromLevel()} are the two seams for that.</li>
 * <li><b>{@code ItemStack} does not override {@code equals()}</b>, and {@code SynchedEntityData.set} gates
 *     dirtiness on value inequality — so re-pushing the same mutated instance is a silent no-op and the client
 *     never sees an in-place inventory change. Every {@code ItemStack} accessor here needs a server-side shadow
 *     copy and a {@code ItemStack.matches} guard, not an unconditional per-tick {@code copy()}.</li>
 * </ul>
 */
public class EntityRobot extends EntityRobotBase implements IEntityWithComplexSpawn, IDebuggable {

    /** Wearable slots. Wearables never enter play before Ph9, but the list, its NBT and its spawn-sync land
     *  now so the save format never has to change. */
    public static final int MAX_WEARABLES = 8;

    /** The four transfer slots. */
    public static final int INVENTORY_SIZE = 4;

    /** Single-tank capacity, in mB. 7.1.x: four buckets. */
    public static final int TANK_CAPACITY = 4000;

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

    // ── State ───────────────────────────────────────────────────────────────

    /** The robot's top-level AI. Null for the whole of Ph3 — every consumer must null-guard. */
    public AIRobot mainAI;

    private final MjBattery battery = new MjBattery(MAX_POWER);
    private final UnreachableEntityCache unreachable =
            new UnreachableEntityCache(() -> level().getGameTime());

    private long robotId = NULL_ROBOT_ID;
    private DockingStation dockingStation;
    private DockingStation mainStation;

    // ── Construction ────────────────────────────────────────────────────────

    /** The registry constructor — what {@code EntityType} calls on load and on the client. */
    public EntityRobot(EntityType<? extends EntityRobotBase> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Convenience constructor for placement from {@code ItemRobot}. */
    public EntityRobot(Level level, RedstoneBoardRobotNBT boardNBT) {
        this(BCRoboticsEntities.ROBOT.get(), level);
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
        builder.define(STEAM_DIR, new Vector3f(0, -1, 0));
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

    /** Version-neutral read hook. Keeps the 7.1.x key names ({@code battery}, {@code linkedStation},
     *  {@code currentStation}, {@code inv}, {@code wearables}, {@code mainAI}, {@code board}) with modern
     *  bodies. The station side byte must be bounds-checked 0..5 on read — legacy {@code UNKNOWN} (6) or
     *  corrupt data has to mean "no station", not an array index crash during chunk load. */
    protected void readData(BCValueInput in) {
    }

    /** Version-neutral write hook. See {@link #readData}. */
    protected void writeData(BCValueOutput out) {
    }

    @Override
    public boolean shouldBeSaved() {
        return true;
    }

    // ── Spawn data ──────────────────────────────────────────────────────────

    /** Carries the wearables list. Uniform across all five nodes. */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
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
    /** Synthetic pick box: the entity type is 0.25 cubed, but the clickable/hittable box wants +/-0.25 around
     *  the centre so a player can actually target a docked robot. Ph3 stub — still the type default. */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        return super.makeBoundingBox(position);
    }
    //?} else {
    /*// Synthetic pick box: the entity type is 0.25 cubed, but the clickable/hittable box wants +/-0.25
    // around the centre so a player can actually target a docked robot. Ph3 stub — still the type default.
    @Override
    protected AABB makeBoundingBox() {
        return super.makeBoundingBox();
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

    /** Version-neutral damage body. Ph3 stub — the real one filters mob and falling-block damage, makes a
     *  docked robot invulnerable, debits the battery and sets {@code HURT_TIME}. */
    private boolean hurtRobot(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
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

    /** Version-neutral interaction body. Ph3 stub — the real one fires {@code RobotEvent.Interact} and, for a
     *  wrench, peels the robot back down (last wearable, then the held item, then {@code convertToItems}). */
    private InteractionResult interactRobot(Player player, InteractionHand hand) {
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

    /** Ph3 stub. The real loop: first-update registry registration, docking snap (server-side only — the
     *  client rides position sync), charge decay, the AI cycle, the synched pushes behind their shadow-copy
     *  gates, held-item ticking, and the void kill below {@code minBuildHeight - 64}. */
    @Override
    public void tick() {
        super.tick();
    }

    // ── Removal / registry lifecycle ────────────────────────────────────────

    /** Ph3 stub. Every removal has to reach the registry or the robot's station reservations and resource
     *  locks leak: a server-side kill maps to {@code killRobot}, an unload to {@code unloadRobot}. */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
    }

    /** Ph3 stub. The chunk-unload path — {@code unloadRobot}, never {@code killRobot}. */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
    }

    /** Drops the robot back into the world as items and removes it. Ph3 stub. */
    public void convertToItems() {
    }

    /** The stacks {@link #convertToItems()} spills: the robot item carrying board and charge, the held item,
     *  the four transfer slots and the wearables. Deliberately NOT the tank — 7.1.x voided it and there is no
     *  reason to make a robot a fluid-duplication vector. Ph3 stub. */
    public List<ItemStack> getDrops() {
        return new ArrayList<>();
    }

    // ── Sleep / charging (Decision 6) ───────────────────────────────────────

    /** 7.1.x called this {@code isActive()} and returned true when the robot was ASLEEP — the inverse of its
     *  name. Renamed here to say what it means. A robot with no AI at all (the whole of Ph3) is not sleeping,
     *  and a robot that is currently taking a charge is reported awake so the charge is watchable. */
    public boolean isSleeping() {
        return false;
    }

    @Override
    public RobotChargeReceiver getChargeReceiver() {
        return new RobotChargeReceiver(this);
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
        return entityData.get(ITEM_IN_USE);
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

    /** Typed {@code Vector3fc} on every node — {@code Vector3f} implements it, so only the accessor
     *  declaration forks. */
    public Vector3fc getSteamDirection() {
        return entityData.get(STEAM_DIR);
    }

    /** Ph3 stub. Takes a {@code Vec3} on purpose: 7.1.x stored three ints and truncated every diagonal to
     *  zero. */
    public void setSteamDirection(Vec3 direction) {
    }

    /** The client-visible sleep flag — {@link #isSleeping()} gated on not-currently-charging. */
    public boolean isSleepingClient() {
        return entityData.get(SLEEPING);
    }

    /** Resolved from {@link #getBoardId()} through the board registry. Entity textures are direct file paths,
     *  never atlas sprites. Ph3 stub. */
    public Identifier getTexture() {
        return null;
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

    /** Ph3 stub. */
    @Override
    public void aimItemAt(float yaw, float pitch) {
    }

    /** Ph3 stub. The 7.1.x formula is {@code atan2(dz, dx)}; the 1.12-era port swapped the arguments, which is
     *  a bug, not a fix. */
    @Override
    public void aimItemAt(BlockPos pos) {
    }

    /** Ph3 stub. */
    @Override
    public void setItemInUse(ItemStack stack) {
    }

    /** Ph3 stub. */
    @Override
    public void setItemActive(boolean active) {
    }

    // ── Laser (API kept, render branch deliberately not ported) ─────────────

    /** Ph3 stub. Nothing in 7.1.x ever called the laser setters; they are kept for addon surface and for NBT
     *  shape stability, and the render branch is deliberately not ported. */
    public void setLaserDestination(float x, float y, float z) {
    }

    /** Ph3 stub. See {@link #setLaserDestination}. */
    public void showLaser() {
    }

    /** Ph3 stub. See {@link #setLaserDestination}. */
    public void hideLaser() {
    }

    // ── Docking ─────────────────────────────────────────────────────────────

    @Override
    public void dock(DockingStation station) {
        this.dockingStation = station;
    }

    @Override
    public void undock() {
        this.dockingStation = null;
    }

    @Override
    public DockingStation getDockingStation() {
        return dockingStation;
    }

    @Override
    public DockingStation getLinkedStation() {
        return mainStation;
    }

    @Override
    public void setMainStation(DockingStation station) {
        this.mainStation = station;
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

    /** Ph3 stub. */
    @Override
    public IRobotRegistry getRegistry() {
        return null;
    }

    /** Ph3 stub. */
    @Override
    public void releaseResources() {
    }

    /** Ph3 stub — logs and defers {@code convertToItems}. {@code AIRobotShutdown} is Ph4. */
    public void shutdown(String reason) {
    }

    // ── AI (Ph4) ────────────────────────────────────────────────────────────

    /** Ph3 stub. */
    public AIRobot getOverridingAI() {
        return null;
    }

    /** Ph3 stub. */
    public void overrideAI(AIRobot ai) {
    }

    /** Ph3 stub — the empty board wires up with the implementation so the robot has a skin. */
    @Override
    public RedstoneBoardRobot getBoard() {
        return null;
    }

    @Override
    public boolean isMoving() {
        return false;
    }

    // ── Zones (Ph6) ─────────────────────────────────────────────────────────

    /** Ph6. */
    @Override
    public IZone getZoneToWork() {
        return null;
    }

    /** Ph6. */
    @Override
    public IZone getZoneToLoadUnload() {
        return null;
    }

    // ── Inventory ───────────────────────────────────────────────────────────

    @Override
    public int getInventorySize() {
        return INVENTORY_SIZE;
    }

    /** Ph3 stub. */
    @Override
    public ItemStack getInventoryStack(int slot) {
        return ItemStack.EMPTY;
    }

    /** Ph3 stub. Must push a {@code copy()} into the matching synched accessor, gated on
     *  {@code !ItemStack.matches(shadow, current)} — see the class javadoc. */
    @Override
    public void setInventoryStack(int slot, ItemStack stack) {
    }

    /** Ph3 stub. */
    @Override
    public boolean containsItems() {
        return false;
    }

    /** Ph3 stub. */
    @Override
    public boolean hasFreeSlot() {
        return false;
    }

    /** Ph3 stub — returns the stack unchanged, i.e. accepts nothing. The real one checks station adjacency
     *  and delegates to the AI when there is one. */
    @Override
    public ItemStack receiveItem(BlockEntity tile, ItemStack stack) {
        return stack;
    }

    /** Ph9 — the list, its NBT, its spawn-sync and its drop path all land in Ph3 so the save format never
     *  changes, but nothing can put a wearable on a robot until Ph9. */
    public List<ItemStack> getWearables() {
        return Collections.emptyList();
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

    /** Ph3 stub — position and energy land now, the AI walk stays null-guarded. */
    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
    }

    // ── Fluids ──────────────────────────────────────────────────────────────
    // Single 4000 mB tank behind the IFluidHandlerAdv fork (Transfer API >=1.21.10, classic IFluidHandler on
    // 1.21.1). Ph3 stubs.

    //? if >=1.21.10 {
    @Override
    public int size() {
        return 0;
    }

    @Override
    public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
        return net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        return 0;
    }

    @Override
    public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        return false;
    }

    @Override
    public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                      net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                       net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain,
                       net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
        return 0;
    }
    //?} else {
    /*@Override
    public int getTanks() {
        return 0;
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
        return false;
    }

    @Override
    public int fill(net.neoforged.neoforge.fluids.FluidStack resource,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        return 0;
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
        return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
    }

    @Override
    public int extract(buildcraft.api.core.IFluidFilter filter, int maxDrain, boolean simulate) {
        return 0;
    }*/
    //?}
}
