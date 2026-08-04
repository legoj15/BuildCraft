/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.robots;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IZone;
import buildcraft.api.inventory.IItemTransactor;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

/**
 * The surface a robot AI (or a redstone board) programs against — everything an {@link AIRobot} is allowed to
 * ask of the robot it drives, and nothing else.
 *
 * <p>This exists so the AI framework is testable without a {@code Level}: a mock {@code IRobotAccess} plus a real
 * {@link MjBattery} is enough to drive {@code AIRobot.cycle()/startDelegateAI/terminate/abort}. The concrete
 * anchor is still {@link EntityRobotBase} (which {@code implements} this), and the registry, {@code RobotEvent}
 * and {@link DockingStation#robotTaking()} deliberately keep the entity type — they are lifecycle owners, not AI
 * callers.
 *
 * <p>The entity-derived members below are declared with exactly the signatures vanilla {@code Entity} already
 * satisfies (verified present, public and identically named on the 1.21.1 node and the 26.2 node), so
 * {@code EntityRobotBase} inherits them for free and never has to re-declare a single one.
 *
 * <p>Deliberately NOT here: {@code setUniqueRobotId(long)} — that is registry lifecycle, called from
 * {@code RobotRegistry}, and no AI has any business assigning a robot's identity.
 */
public interface IRobotAccess {

    // ── The energy scale (Decision 3) ────────────────────────────────────────
    // These sit on the INTERFACE, not on EntityRobotBase, and that placement is load-bearing: reading a robot
    // constant must never class-load Entity. NeoForge's AttachmentHolder (Entity's supertype) asks
    // FMLEnvironment.isProduction() during its own static initialisation, which throws "There is no current FML
    // Loader" anywhere outside a running loader — so with the constants on the entity, a plain JUnit test (or an
    // addon's data generator) could not so much as read the battery capacity. EntityRobotBase implements this
    // interface, so every existing `EntityRobotBase.MAX_POWER` call site still resolves, by inheritance.
    //
    // The values themselves are 7.1.x's, converted at BuildCraft's canonical 1 MJ = 10 RF bridge.

    /** Robot battery capacity. Chosen, not derived — 7.1.x MAX_ENERGY 100 000 RF at 10 RF/MJ. Deliberately
     * larger than {@code Integer.MAX_VALUE}: every robot energy value is micro-MJ in a {@code long}, and the
     * moment one is narrowed to an {@code int} a full battery wraps negative. */
    long MAX_POWER = 10_000 * MjAPI.MJ;

    /** The reserve an AI is supposed to head home on. Chosen, not derived — 7.1.x 20 000 RF at 10 RF/MJ. */
    long SAFETY_POWER = MAX_POWER / 5;

    /** Chosen, not derived — 7.1.x 0 RF. A robot shuts down when it is actually flat. */
    long SHUTDOWN_POWER = 0;

    /** The registry's "no robot" sentinel. Ids are handed out ascending from {@code Long.MIN_VALUE}, so the top
     * of the range is the one value that can never be a real id. */
    long NULL_ROBOT_ID = Long.MAX_VALUE;

    /** What one point of damage costs a robot's battery. Chosen, not derived — 7.1.x debited 2 600 RF of a
     * 100 000 RF battery per point, i.e. 2.6 % of a full charge, and that ratio is what decides how many hits a
     * robot walks away from (38, dying on the 39th). Lives here rather than on the entity for the same
     * class-loading reason as the constants above. */
    long DAMAGE_ENERGY_PER_POINT = 260 * MjAPI.MJ;

    // ── Inherited from vanilla Entity — declared, never implemented by hand ──

    Level level();

    Vec3 position();

    BlockPos blockPosition();

    int getId();

    Vec3 getDeltaMovement();

    void setDeltaMovement(Vec3 movement);

    AABB getBoundingBox();

    /** Euclidean distance from the robot's current position to {@code (x, y, z)}. 7.1.x's AIs called this on
     *  every movement frame to detect arrival; declared as a default over {@link #position()} so a test
     *  {@code IRobotAccess} (which already supplies a position) never has to re-implement it. */
    default double getDistance(double x, double y, double z) {
        Vec3 pos = position();
        double dx = x - pos.x;
        double dy = y - pos.y;
        double dz = z - pos.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ── Energy ──

    MjBattery getBattery();

    long getPower();

    // ── Held item / aiming ──

    /** The stack the robot is currently holding out in front of itself (7.1.x {@code itemInUse}). */
    ItemStack getHeldItem();

    void setItemInUse(ItemStack stack);

    void setItemActive(boolean active);

    void aimItemAt(float yaw, float pitch);

    void aimItemAt(BlockPos pos);

    float getAimYaw();

    float getAimPitch();

    // ── Docking ──

    void dock(DockingStation station);

    void undock();

    DockingStation getDockingStation();

    DockingStation getLinkedStation();

    void setMainStation(DockingStation station);

    // ── Registry / identity ──

    IRobotRegistry getRegistry();

    long getRobotId();

    void releaseResources();

    // ── Zones (Ph6) ──

    IZone getZoneToWork();

    IZone getZoneToLoadUnload();

    // ── Inventory ──
    // main's robot contract no longer extends a vanilla container type, so the four transfer slots are
    // reached through this pair rather than through Container/IItemHandler.

    int getInventorySize();

    ItemStack getInventoryStack(int slot);

    void setInventoryStack(int slot, ItemStack stack);

    /** The robot's four transfer slots exposed as an {@link IItemTransactor}, the seam the load/unload AIs
     *  insert and extract through. 7.1.x reached the same surface through {@code ITransactor.getTransactorFor},
     *  which main has no equivalent of — the modern transactor interface already models the two operations the
     *  AIs need ({@code insert} with its {@code allOrNone}/{@code simulate} flags, {@code extract} against an
     *  {@code IStackFilter}). The concrete robot implements this over its live slot array, preserving 7.1.x's
     *  in-place mutation semantics. */
    IItemTransactor getTransactor();

    boolean containsItems();

    boolean hasFreeSlot();

    ItemStack receiveItem(BlockEntity tile, ItemStack stack);

    // ── Pathing hints ──

    void unreachableEntityDetected(Entity entity);

    boolean isKnownUnreachable(Entity entity);

    // ── Board / motion ──

    RedstoneBoardRobot getBoard();

    boolean isMoving();
}
