/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/ */
package buildcraft.api.robots;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.world.Container;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?} else {
/*import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}
import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IStackFilter;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.transport.IInjectable;

import buildcraft.api.core.NbtApiUtil;

public abstract class DockingStation {
    public Direction side;
    public Level world;

    private long robotTakingId = EntityRobotBase.NULL_ROBOT_ID;
    private EntityRobotBase robotTaking;

    private boolean linkIsMain = false;

    private BlockPos pos;

    public DockingStation(BlockPos iIndex, Direction iSide) {
        pos = iIndex;
        side = iSide;
    }

    public DockingStation() {
    }

    public boolean isMainStation() {
        return linkIsMain;
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction side() {
        return side;
    }

    /** The player this station acts for (e.g. whoever placed the pipe it sits on), or null when there is none.
     *  A robot homed here acts as the player who deployed it, and protection mods must permit this player as well
     *  for anything it does; a robot with no recorded deployer acts and asks as this player alone. */
    @Nullable
    public GameProfile getOwner() {
        return null;
    }

    public EntityRobotBase robotTaking() {
        if (robotTakingId == EntityRobotBase.NULL_ROBOT_ID) {
            return null;
        } else if (robotTaking == null) {
            robotTaking = RobotManager.registryProvider.getRegistry(world).getLoadedRobot(robotTakingId);
        }

        return robotTaking;
    }

    public void invalidateRobotTakingEntity() {
        robotTaking = null;
    }

    public long linkedId() {
        return robotTakingId;
    }

    public boolean takeAsMain(EntityRobotBase robot) {
        if (robotTakingId == EntityRobotBase.NULL_ROBOT_ID) {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(world);
            linkIsMain = true;
            robotTaking = robot;
            robotTakingId = robot.getRobotId();
            registry.registryMarkDirty();
            robot.setMainStation(this);
            registry.take(this, robot.getRobotId());

            return true;
        } else {
            return robotTakingId == robot.getRobotId();
        }
    }

    /** Claims the station for a plain visit. True when {@code robot} holds it on return: it was free and is now
     *  taken, or the robot held it already (as its home, its dock or a visit), which is left exactly as it was.
     *
     *  <p>The holder is compared by id, never by the cached entity: 7.1.x tested {@code robotTaking == null}, which is
     *  also true for a station whose holder is linked but merely not loaded (an unload drops the cached entity, and
     *  so does reading the station from NBT) - another robot then took over the unloaded robot's home or dock, and
     *  the holder's own re-take reset its home link to a plain claim. */
    public boolean take(EntityRobotBase robot) {
        if (robotTakingId == EntityRobotBase.NULL_ROBOT_ID) {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(world);
            linkIsMain = false;
            robotTaking = robot;
            robotTakingId = robot.getRobotId();
            registry.registryMarkDirty();
            registry.take(this, robot.getRobotId());

            return true;
        } else {
            return robot.getRobotId() == robotTakingId;
        }
    }

    /** Gives back {@code robot}'s plain claim; a main link is never released here. By id, like {@link #take}: the
     *  cached entity is gone after an unload, and a reloaded robot undocking must still free its dock. */
    public void release(EntityRobotBase robot) {
        if (robotTakingId == robot.getRobotId() && robotTakingId != EntityRobotBase.NULL_ROBOT_ID && !linkIsMain) {
            IRobotRegistry registry = RobotManager.registryProvider.getRegistry(world);
            unsafeRelease(robot.getRobotId());
            registry.registryMarkDirty();
            registry.release(this, robot.getRobotId());
        }
    }

    /**
     * Same a release but doesn't clear the registry (presumably called from the
     * registry).
     */
    public void unsafeRelease(EntityRobotBase robot) {
        unsafeRelease(robot.getRobotId());
    }

    /** {@link #unsafeRelease(EntityRobotBase)} by robot id: drops any link (main included) that {@code robotId} holds,
     *  without touching the registry. The registry's forced paths use this one, because the robot they release may
     *  be unloaded already, and an unload drops the station's cached entity - an entity comparison then never
     *  matched, and a killed robot's home stayed linked to its dead id. */
    public void unsafeRelease(long robotId) {
        if (robotId != EntityRobotBase.NULL_ROBOT_ID && robotTakingId == robotId) {
            linkIsMain = false;
            robotTaking = null;
            robotTakingId = EntityRobotBase.NULL_ROBOT_ID;
        }
    }

    public void writeToNbt(CompoundTag nbt) {
        nbt.putIntArray("pos", new int[] { getPos().getX(), getPos().getY(), getPos().getZ() });
        nbt.putByte("side", (byte) side.ordinal());
        nbt.putBoolean("isMain", linkIsMain);
        nbt.putLong("robotId", robotTakingId);
    }

    /** Reads this station back from its saved record.
     *
     *  <p>The side is the station's identity (a registry key, and the face of the pipe it sits on), so a record whose
     *  side byte is not a real {@link Direction} - 7.1.x's {@code ForgeDirection.UNKNOWN} was 6, and a corrupt save
     *  holds anything - is unusable, and is refused with an {@link IllegalArgumentException}: it is not given a default
     *  side, which would plant a phantom station on whichever face happened to be NORTH. The registry skips such a
     *  record and loads the rest (a pipe's pluggable registers a fresh station for its own face when it loads; the
     *  robots that were homed on the skipped one find no station there and shut down, as for any station that is
     *  gone).
     *
     *  @throws IllegalArgumentException when the side byte is not a {@link Direction} ordinal */
    public void readFromNbt(CompoundTag nbt) {
        if (nbt.contains("index")) {
            // For compatibility with older versions of minecraft and buildcraft
            CompoundTag indexNBT = NbtApiUtil.getCompound(nbt, "index");
            int x = NbtApiUtil.getInt(indexNBT, "i", 0);
            int y = NbtApiUtil.getInt(indexNBT, "j", 0);
            int z = NbtApiUtil.getInt(indexNBT, "k", 0);
            pos = new BlockPos(x, y, z);
        } else {
            int[] array = NbtApiUtil.getIntArray(nbt, "pos", new int[0]);
            if (array.length == 3) {
                pos = new BlockPos(array[0], array[1], array[2]);
            } else if (array.length != 0) {
                BCLog.logger
                        .warn("Found an integer array that was not the right length! (" + Arrays.toString(array) + ")");
            } else {
                BCLog.logger.warn("Did not find any integer positions! This is a bug!");
            }
        }
        // Unsigned and range-checked, as AIRobotGotoStation and EntityRobot.readStation read theirs.
        int sideOrdinal = NbtApiUtil.getByte(nbt, "side", (byte) 0) & 0xFF;
        Direction[] sides = Direction.values();
        if (sideOrdinal >= sides.length) {
            throw new IllegalArgumentException("Docking station record has an out-of-range side byte " + sideOrdinal
                    + " (a real side is 0.." + (sides.length - 1) + ")");
        }
        side = sides[sideOrdinal];
        linkIsMain = NbtApiUtil.getBoolean(nbt, "isMain", false);
        // "No robot" is the null id and nothing else: every build that ever wrote this key (7.1.x onwards) wrote
        // NULL_ROBOT_ID for a free station, never 0 or -1, so there is no second sentinel to map. A record without
        // the key therefore reads as free - defaulting it to 0 made it look held by a robot that never existed, and
        // take()/release() (which decide by this id) refused every robot for good.
        robotTakingId = NbtApiUtil.getLong(nbt, "robotId", EntityRobotBase.NULL_ROBOT_ID);
    }

    public boolean isTaken() {
        return robotTakingId != EntityRobotBase.NULL_ROBOT_ID;
    }

    public long robotIdTaking() {
        return robotTakingId;
    }

    public BlockPos index() {
        return pos;
    }

    @Override
    public String toString() {
        return "{" + pos + ", " + side + " :" + robotTakingId + "}";
    }

    public boolean linkIsDocked() {
        if (robotTaking() != null) {
            return robotTaking().getDockingStation() == this;
        } else {
            return false;
        }
    }

    public boolean canRelease() {
        return !isMainStation() && !linkIsDocked();
    }

    /** Whether this station's chunk is loaded, i.e. whether a robot could fly to it now: robots path only through
     *  loaded cells, so a station in an unloaded chunk is never a destination worth choosing. A station with no world
     *  (a test double) has no chunk to check and counts as loaded. */
    public boolean isChunkLoaded() {
        return world == null || world.isLoaded(pos);
    }

    public boolean isInitialized() {
        return true;
    }

    public abstract Iterable<StatementSlot> getActiveActions();

    public IInjectable getItemOutput() {
        return null;
    }

    public EnumPipePart getItemOutputSide() {
        return EnumPipePart.CENTER;
    }

    public Container getItemInput() {
        return null;
    }

    public EnumPipePart getItemInputSide() {
        return EnumPipePart.CENTER;
    }

    //? if >=1.21.10 {
    public ResourceHandler<FluidResource> getFluidOutput() {
        return null;
    }
    //?} else {
    /*public IFluidHandler getFluidOutput() {
        return null;
    }*/
    //?}

    public EnumPipePart getFluidOutputSide() {
        return EnumPipePart.CENTER;
    }

    //? if >=1.21.10 {
    public ResourceHandler<FluidResource> getFluidInput() {
        return null;
    }
    //?} else {
    /*public IFluidHandler getFluidInput() {
        return null;
    }*/
    //?}

    public EnumPipePart getFluidInputSide() {
        return EnumPipePart.CENTER;
    }

    public boolean providesPower() {
        return false;
    }

    public IRequestProvider getRequestProvider() {
        return null;
    }

    // ── Station access policy (the D1 seam) ────────────────────────────────
    // These four answer whether a robot may interact with this station's item flow. In 7.1.x they were
    // the gate-action helpers ActionStationProvideItems / ActionRobotFilter / ActionStationForbidRobot,
    // each reading a statement the station's pipe held. Those statements are Ph6, so Ph4 carries the
    // policy HERE with permissive defaults, and Ph6 overrides them to read the real gate actions. Every
    // AI call site swapped the statement-helper call for one of these, so the AI logic is identical to
    // 7.1.x's shape and Ph6 fills the gate-driven behaviour in a single place.

    /** Whether a docked robot may EXTRACT {@code stack} from this station's item input (7.1.x:
     *  {@code ActionStationProvideItems.canExtractItem}). */
    public boolean canRobotExtractItem(ItemStack stack) {
        return true;
    }

    /** Whether a docked robot may INSERT {@code stack} into this station's item output (7.1.x:
     *  {@code ActionRobotFilter.canInteractWithItem(station, filter, ActionStationAcceptItems.class)}). */
    public boolean canRobotAcceptItem(ItemStack stack) {
        return true;
    }

    /** Whether {@code robot} is forbidden from using this station at all (7.1.x:
     *  {@code ActionStationForbidRobot.isForbidden}). */
    public boolean isRobotForbidden(IRobotAccess robot) {
        return false;
    }

    /** The stack filter restricting what this station is willing to trade (7.1.x:
     *  {@code ActionRobotFilter.getGateFilter}). Ph4: no gates, so every item matches. */
    public IStackFilter getRobotItemFilter() {
        return stack -> true;
    }

    /** The fluid twin of {@link #getRobotItemFilter} — which fluid this station is willing to trade
     *  (7.1.x: {@code ActionRobotFilter.getGateFluidFilter}, the fluid carrier board's load filter). The
     *  permissive default is the GATELESS contract; {@code DockingStationPipe} overrides it to read the
     *  real gate actions. */
    public IFluidFilter getRobotFluidFilter() {
        return fluid -> fluid != null && !fluid.isEmpty();
    }

    /** Whether a docked robot may EXTRACT fluid matching {@code filter} from this station's fluid input
     *  — the fluid twin of {@link #canRobotExtractItem}, the pump board's load side. 7.1.x gated this on
     *  {@code ActionRobotFilter.canInteractWithFluid(station, filter, ActionStationProvideFluids.class)};
     *  the permissive default here is the GATELESS contract, and {@code DockingStationPipe} overrides it
     *  to read the real gate actions. */
    public boolean canRobotExtractFluid(IFluidFilter filter) {
        return true;
    }

    /** Whether a docked robot may INSERT fluid matching {@code filter} into this station's fluid output
     *  — the fluid twin of {@link #canRobotAcceptItem}, the pump board's unload side. 7.1.x gated this on
     *  {@code ActionRobotFilter.canInteractWithFluid(station, filter, ActionStationAcceptFluids.class)};
     *  the permissive default here is the GATELESS contract, and {@code DockingStationPipe} overrides it
     *  to read the real gate actions. */
    public boolean canRobotAcceptFluid(IFluidFilter filter) {
        return true;
    }
}
