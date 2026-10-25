/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.filler.IFillerPattern;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.filler.FillerType;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The saved data a Filler and a Volume Box held while the Filler Planner existed (commit {@code e717448c4} is the last
 * tree that wrote it): the shapes {@code TileFillerPlannerMigrationTest} and the {@code filler_planner_save_migrates}
 * game test load. The planner was an addon of the box, so its pattern and invert flag sat in the box's {@code addons}
 * list; the Filler beside it never copied the box in, and the lock it put on the box carried an extra {@code ADDON} target.
 */
final class PlannerSaveFixtures {
    private PlannerSaveFixtures() {}

    /** The planner's pattern in its saved state: a frame, inverted. */
    static IFillerPattern plannerPattern() {
        return BCBuildersStatements.PATTERN_FRAME;
    }

    /** A Filler tag the way a Filler beside a planner box saved it: bound to a box (markerBox false) it never copied. */
    static CompoundTag fillerTag(BlockPos fillerPos, BlockState fillerState) {
        CompoundTag tag = ValueIoTestUtil.write(new TileFiller(fillerPos, fillerState)::writeData);
        tag.putBoolean("markerBox", false);
        return tag;
    }

    /**
     * What {@code VolumeBox.writeToNBT} wrote while the planner existed: the box at {@code min}..{@code max}, with the
     * planner addon in {@code addons} when {@code withPlanner}, and the lock the Filler at {@code fillerPos} put on it
     * when {@code withFillerLock}.
     */
    static CompoundTag volumeBoxTag(Level level, BlockPos min, BlockPos max, BlockPos fillerPos, boolean withPlanner,
                                    boolean withFillerLock) {
        VolumeBox box = new VolumeBox(level, min);
        box.box.reset();
        box.box.setMin(min);
        box.box.setMax(max);
        CompoundTag tag = box.writeToNBT();

        ListTag addons = new ListTag();
        if (withPlanner) {
            CompoundTag entry = new CompoundTag();
            entry.putString("slot", "EAST_UP_SOUTH");
            entry.putString("addonClass", "buildcraftunofficial:filler_planner");
            entry.put("addonData", plannerAddonData());
            addons.add(entry);
        }
        tag.put("addons", addons);

        ListTag locks = new ListTag();
        if (withFillerLock) {
            locks.add(fillerLock(fillerPos));
        }
        tag.put("locks", locks);
        return tag;
    }

    private static CompoundTag plannerAddonData() {
        FullStatement<IFillerPattern> pattern = new FullStatement<>(FillerType.INSTANCE, 4, null);
        pattern.set(plannerPattern());
        CompoundTag data = new CompoundTag();
        data.put("patternStatement", pattern.writeToNbt());
        data.putBoolean("inverted", true);
        return data;
    }

    /** The lock {@code TileFiller.onPlacedBy} added for a planner box: the retired ADDON target ahead of the usual three. */
    private static CompoundTag fillerLock(BlockPos fillerPos) {
        CompoundTag pos = new CompoundTag();
        pos.putInt("X", fillerPos.getX());
        pos.putInt("Y", fillerPos.getY());
        pos.putInt("Z", fillerPos.getZ());
        CompoundTag causeData = new CompoundTag();
        causeData.put("pos", pos);
        causeData.putString("block", BuiltInRegistries.BLOCK.getKey(BCBuildersBlocks.FILLER.get()).toString());
        CompoundTag cause = new CompoundTag();
        cause.putString("type", "BLOCK");
        cause.put("data", causeData);

        CompoundTag addonData = new CompoundTag();
        addonData.putString("slot", "EAST_UP_SOUTH");
        CompoundTag machineData = new CompoundTag();
        machineData.putString("type", "STRIPES_WRITE");
        ListTag targets = new ListTag();
        targets.add(target("ADDON", addonData));
        targets.add(target("REMOVE", new CompoundTag()));
        targets.add(target("RESIZE", new CompoundTag()));
        targets.add(target("USED_BY_MACHINE", machineData));

        CompoundTag lock = new CompoundTag();
        lock.put("cause", cause);
        lock.put("targets", targets);
        return lock;
    }

    private static CompoundTag target(String type, CompoundTag data) {
        CompoundTag target = new CompoundTag();
        target.putString("type", type);
        target.put("data", data);
        return target;
    }
}
