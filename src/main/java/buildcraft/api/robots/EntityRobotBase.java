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
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjBattery;

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

    // The energy scale (MAX_POWER / SAFETY_POWER / SHUTDOWN_POWER / NULL_ROBOT_ID / DAMAGE_ENERGY_PER_POINT)
    // lives on IRobotAccess and is inherited here — see the comment on that interface for why reading a robot
    // constant must not require class-loading Entity. Existing `EntityRobotBase.MAX_POWER` call sites are
    // unaffected: they resolve through this inheritance.

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
     * <p>Abstract, and typed against {@code IMjReceiver} rather than a concrete wrapper, on purpose: the obvious
     * convenience default ({@code new MjBatteryReceiver(getBattery())}) would be the first and only
     * {@code buildcraft.api} -> {@code buildcraft.lib} import in the whole API tree, which the
     * api-redistribution plan exists to prevent. Implementations supply their own.
     *
     * <p>Whatever is returned should ALSO be an {@code IMjReadable}: {@code TriggerPower} instanceof-checks the
     * station's receiver in order to read a docked robot's stored charge, and a receiver-only wrapper silently
     * kills that gate trigger with no other symptom. {@code EntityRobot} returns a {@code RobotChargeReceiver}
     * (which extends {@code MjBatteryReceiver}, so it is readable) that additionally latches "this robot is
     * being charged right now" for the sleep indicator.
     *
     * <p>May return null — a robot mid-construction, or one with no battery, yields no receiver rather than an
     * NPE inside a pipe tick. {@code RobotStationPluggable} null-guards the whole chain for that reason. */
    public abstract IMjReceiver getChargeReceiver();

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

    /** The robot's fluid tank, exposed to the pump board's AIs. The concrete robot implements
     *  {@link IFluidHandlerAdv} itself, so the answer is {@code this}. */
    @Override
    public IFluidHandlerAdv getFluidHandler() {
        return this;
    }

    /** Melee with the held item (the knight/butcher's only way to hurt).
     *
     *  <p>Red-baseline skeleton: the real damage pipeline (base 1.0 + the held item's ATTACK_DAMAGE
     *  attribute modifier + sharpness, knockback/fire aspect, {@code MOB_ATTACK} source, durability
     *  wear) lands in the foundations commit. */
    @Override
    public void attackTargetEntityWithCurrentItem(Entity target) {
        // Red-baseline skeleton — real melee implementation in the foundations commit.
    }

    public abstract void releaseResources();

    public abstract ItemStack receiveItem(BlockEntity tile, ItemStack stack);

    public abstract void setMainStation(DockingStation station);
}
