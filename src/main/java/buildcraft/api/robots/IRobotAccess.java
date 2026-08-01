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

    // ── Inherited from vanilla Entity — declared, never implemented by hand ──

    Level level();

    Vec3 position();

    BlockPos blockPosition();

    int getId();

    Vec3 getDeltaMovement();

    void setDeltaMovement(Vec3 movement);

    AABB getBoundingBox();

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
