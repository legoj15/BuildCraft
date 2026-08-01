/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.robots;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.core.IZone;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

import buildcraft.lib.mj.MjBatteryReceiver;

/** Abstract robot entity — the registry/event/station-facing anchor type.
 *
 * <p>Sits on bare {@link Entity}, NOT {@code LivingEntity}. 7.1.x only ever used three things from its living
 * parents (the living-attack hook, the enchantment melee path, and armour-model rendering); everything else was
 * suppression — {@code setHealth} no-op'd, all four equipment accessors stubbed, movement/ladder/despawn
 * overridden to neuter the parent. Modern {@code LivingEntity} adds mandatory attribute registration (whose
 * absence NPEs with no boot warning for a {@code MobCategory.MISC} entity), drowning, potion susceptibility,
 * player sweep attacks and pushability on top of that — every one a fresh suppression override and a cross-node
 * bug surface. A robot is a machine; bare {@code Entity} is the honest base.
 *
 * <p>Consequence worth knowing: {@link #isAlive()} is now removal-gated ({@code !isRemoved()}) rather than
 * health-gated. Its sole consumer is {@code RobotRegistry.robotIdTaking}'s lazy resource release, and since
 * 7.1.x no-op'd health anyway this is the intended fix, not a regression. */
public abstract class EntityRobotBase extends Entity implements IRobotAccess, IFluidHandlerAdv {

    /** 7.1.x stored 100 000 RF. BuildCraft's canonical bridge was 1 MJ = 10 RF, so the modern capacity is
     * 10 000 MJ. Chosen, not derived — 7.1.x 100 000 RF at 10 RF/MJ. (The old {@code 5000 * MJ} pin implied
     * 1 RF = 0.05 MJ while {@code AIRobot.getPowerCost()} implies 1 RF = 0.1 MJ; a robot would have run half
     * as long as its 7.1.x self.) Still comfortably larger than {@code Integer.MAX_VALUE}. */
    public static final long MAX_POWER = 10_000 * MjAPI.MJ;
    /** Chosen, not derived — 7.1.x 20 000 RF at 10 RF/MJ. */
    public static final long SAFETY_POWER = MAX_POWER / 5;
    /** Chosen, not derived — 7.1.x 0 RF. */
    public static final long SHUTDOWN_POWER = 0;
    public static final long NULL_ROBOT_ID = Long.MAX_VALUE;

    public EntityRobotBase(EntityType<? extends EntityRobotBase> type, Level level) {
        super(type, level);
    }

    /** Concrete and empty on purpose: bare {@link Entity} seeds its own synched-data entries before this hook
     * runs, so a subclass with no synched data of its own (a test fixture, say) needs nothing here — and one
     * that does have data overrides without a {@code super} call. */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** The MJ receiver a docking station hands to whatever is charging this robot.
     *
     * <p>Concrete so fixtures and subclasses compile; {@code EntityRobot} overrides it with a wrapper that also
     * latches "am I charging right now" for the sleep indicator. Must stay an {@code IMjReadable} as well as an
     * {@code IMjReceiver} — {@code TriggerPower} instanceof-checks the station's receiver to read a docked
     * robot's charge, and a receiver-only wrapper would silently kill that gate trigger.
     *
     * <p>Null-tolerant: a robot mid-construction (or a fixture that supplies no battery) yields no receiver
     * rather than an NPE inside a pipe tick.
     *
     * <p><b>Note:</b> this is the one and only place {@code buildcraft.api} reaches into {@code buildcraft.lib}
     * ({@code MjBatteryReceiver} lives there). If the API is ever split into its own jar, this method is what
     * has to move or be re-expressed against {@code IMjReceiver} + {@code IMjReadable} — nothing else in
     * {@code api/} carries a lib edge. */
    public MjBatteryReceiver getChargeReceiver() {
        MjBattery battery = getBattery();
        return battery == null ? null : new MjBatteryReceiver(battery);
    }

    public abstract void setItemInUse(ItemStack stack);

    public abstract void setItemActive(boolean b);

    public abstract boolean isMoving();

    public abstract DockingStation getLinkedStation();

    public abstract RedstoneBoardRobot getBoard();

    public abstract void aimItemAt(float yaw, float pitch);

    public abstract void aimItemAt(BlockPos pos);

    public abstract float getAimYaw();

    public abstract float getAimPitch();

    @Override
    public long getPower() {
        return getBattery().getStored();
    }

    public abstract MjBattery getBattery();

    public abstract DockingStation getDockingStation();

    public abstract void dock(DockingStation station);

    public abstract void undock();

    public abstract IZone getZoneToWork();

    public abstract IZone getZoneToLoadUnload();

    public abstract boolean containsItems();

    public abstract boolean hasFreeSlot();

    public abstract void unreachableEntityDetected(Entity entity);

    public abstract boolean isKnownUnreachable(Entity entity);

    public abstract long getRobotId();

    /** Assigns this robot's persistent id; called by the registry the first time an un-IDed robot registers.
     * Deliberately NOT on {@link IRobotAccess} — registry lifecycle, not an AI concern. */
    public abstract void setUniqueRobotId(long robotId);

    public abstract IRobotRegistry getRegistry();

    public abstract void releaseResources();

    public abstract ItemStack receiveItem(BlockEntity tile, ItemStack stack);

    public abstract void setMainStation(DockingStation station);
}
