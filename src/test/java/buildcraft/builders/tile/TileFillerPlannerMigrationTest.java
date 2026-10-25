/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.test.FakeLevels;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * Saves written while the Filler Planner existed. The planner was an addon of a Volume Box: the pattern and the invert
 * flag lived in the box's {@code addons} list, and a Filler beside such a box never copied the box into itself (it read
 * everything from the addon). With the planner gone those Fillers load with no box and a default pattern; the first
 * tick of one must take the box over together with what the planner held, and only that once.
 */
public class TileFillerPlannerMigrationTest {

    private static final BlockPos BOX_MIN = new BlockPos(10, 64, 10);
    private static final BlockPos BOX_MAX = new BlockPos(12, 66, 12);
    /** East of the box, facing east: the cell behind the Filler is the box's. */
    private static final BlockPos FILLER_POS = new BlockPos(13, 65, 11);

    private static BlockState fillerState() {
        return BCBuildersBlocks.FILLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
    }

    private static TileFiller newFiller() {
        return new TileFiller(FILLER_POS, fillerState());
    }

    private static CompoundTag oldPlannerFillerTag() {
        return PlannerSaveFixtures.fillerTag(FILLER_POS, fillerState());
    }

    private static TileFiller loadedFiller(CompoundTag tag) {
        TileFiller filler = newFiller();
        filler.readData(ValueIoTestUtil.inputOf(tag));
        return filler;
    }

    private static CompoundTag oldVolumeBoxTag(boolean withPlanner, boolean withFillerLock) {
        return PlannerSaveFixtures.volumeBoxTag(
            FakeLevels.fake(false, Level.OVERWORLD), BOX_MIN, BOX_MAX, FILLER_POS, withPlanner, withFillerLock);
    }

    private static LevelSavedDataVolumeBoxes savedDataWith(VolumeBox box) {
        LevelSavedDataVolumeBoxes data = new LevelSavedDataVolumeBoxes(FakeLevels.fake(false, Level.OVERWORLD));
        data.volumeBoxes.add(box);
        return data;
    }

    private static VolumeBox loadBox(CompoundTag tag) {
        return new VolumeBox(FakeLevels.fake(false, Level.OVERWORLD), tag);
    }

    private static long fillerLocks(VolumeBox box) {
        Block filler = BCBuildersBlocks.FILLER.get();
        return box.locks.stream()
            .filter(lock -> lock.cause instanceof Lock.Cause.CauseBlock cause
                && FILLER_POS.equals(cause.pos) && cause.block == filler)
            .count();
    }

    @Test
    public void aFillerBesideAPlannerBoxTakesOverTheBoxItsPatternAndItsInvertFlag() {
        TileFiller filler = loadedFiller(oldPlannerFillerTag());
        Assertions.assertFalse(filler.box.isInitialized(), "precondition: the old save never copied the box into the Filler");
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));
        LevelSavedDataVolumeBoxes data = savedDataWith(box);

        filler.migrateOrphanedBoxOnce(() -> data);

        Assertions.assertTrue(filler.box.isInitialized(), "the Filler should have taken the Volume Box over");
        Assertions.assertEquals(BOX_MIN, filler.box.min());
        Assertions.assertEquals(BOX_MAX, filler.box.max());
        Assertions.assertSame(PlannerSaveFixtures.plannerPattern(), filler.patternStatement.get(),
            "the planner's pattern should have moved to the Filler");
        Assertions.assertTrue(filler.inverted, "the planner's invert flag should have moved to the Filler");
        Assertions.assertNotNull(filler.getTemplateBuildingInfo(), "with a box and a pattern the Filler can build");
        Assertions.assertFalse(filler.markerBox, "a Filler bound to a Volume Box is not a marker-box Filler");
    }

    @Test
    public void theFillersLockFromTheOldSaveIsKeptAndNotDoubled() {
        TileFiller filler = loadedFiller(oldPlannerFillerTag());
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));
        Assertions.assertEquals(1, fillerLocks(box), "precondition: the old save holds the Filler's lock");
        Assertions.assertEquals(3, box.locks.get(0).targets.size(),
            "the retired ADDON target is dropped on load, leaving remove, resize and machine");

        filler.migrateOrphanedBoxOnce(() -> savedDataWith(box));

        Assertions.assertEquals(1, fillerLocks(box), "the Filler already holds its lock; adopting must not add a second");
    }

    @Test
    public void thePlannerDataIsTakenOnceAndNoLongerSavedOnceTaken() {
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));
        LevelSavedDataVolumeBoxes data = savedDataWith(box);
        TileFiller first = loadedFiller(oldPlannerFillerTag());
        first.migrateOrphanedBoxOnce(() -> data);
        Assertions.assertTrue(first.inverted, "precondition: the first Filler took the planner's state");

        Assertions.assertNull(box.takeRetiredPlanner(), "the planner's state is consumed by the Filler that took it");
        Assertions.assertFalse(savedBox(data).contains("addons"), "once a Filler has the planner's state, the save drops it");
    }

    /**
     * The world can save (an autosave after any marker edit, a quit) before the Filler's chunk ever loads and ticks.
     * The planner's state must ride along in the Volume Box's save until a Filler takes it, or that Filler migrates
     * with the default pattern.
     */
    @Test
    public void thePlannerDataSurvivesSavesOfTheBoxesBeforeAnyFillerTicks() {
        LevelSavedDataVolumeBoxes data = savedDataWith(loadBox(oldVolumeBoxTag(true, true)));

        // Two saves and reloads with no Filler in between.
        for (int cycle = 0; cycle < 2; cycle++) {
            data = LevelSavedDataVolumeBoxes.fromNbt(LevelSavedDataVolumeBoxes.toNbt(data), FakeLevels.fake(false, Level.OVERWORLD));
        }
        LevelSavedDataVolumeBoxes reloaded = data;
        TileFiller filler = loadedFiller(oldPlannerFillerTag());
        filler.migrateOrphanedBoxOnce(() -> reloaded);

        Assertions.assertTrue(filler.box.isInitialized(), "the Filler takes the box over after the reloads");
        Assertions.assertSame(PlannerSaveFixtures.plannerPattern(), filler.patternStatement.get(),
            "the planner's pattern should have survived the saves");
        Assertions.assertTrue(filler.inverted, "the planner's invert flag should have survived the saves");
    }

    @Test
    public void clientsAreNeverSentThePlannerData() {
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));

        Assertions.assertFalse(box.writeToNBT().contains("addons"),
            "the tag that is broadcast and diffed carries no retired addon list: only the save does");
        Assertions.assertTrue(savedBox(savedDataWith(box)).contains("addons"), "precondition: the save still holds it");
    }

    /** The one Volume Box of {@code data} as the world save writes it. */
    private static CompoundTag savedBox(LevelSavedDataVolumeBoxes data) {
        return NBTUtilBC.getCompound(NBTUtilBC.getList(LevelSavedDataVolumeBoxes.toNbt(data), "volumeBoxes", Tag.TAG_COMPOUND), 0);
    }

    @Test
    public void aBoxFromAnOldSaveWithoutAPlannerStillAdoptsWithTheDefaultPattern() {
        TileFiller filler = loadedFiller(oldPlannerFillerTag());
        VolumeBox box = loadBox(oldVolumeBoxTag(false, true));

        filler.migrateOrphanedBoxOnce(() -> savedDataWith(box));

        Assertions.assertTrue(filler.box.isInitialized(), "the box is still taken over");
        Assertions.assertEquals(newFiller().patternStatement.get(), filler.patternStatement.get(),
            "no planner data: the Filler keeps its own pattern");
        Assertions.assertFalse(filler.inverted, "no planner data: the Filler keeps its own invert flag");
    }

    @Test
    public void theLookIsMadeOncePerLoadAndOnlyForAFillerBoundToABoxWithoutIt() {
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));
        AtomicInteger lookups = new AtomicInteger();

        TileFiller orphan = loadedFiller(oldPlannerFillerTag());
        orphan.migrateOrphanedBoxOnce(() -> {
            lookups.incrementAndGet();
            return savedDataWith(box);
        });
        orphan.migrateOrphanedBoxOnce(() -> {
            lookups.incrementAndGet();
            return savedDataWith(box);
        });
        Assertions.assertEquals(1, lookups.get(), "an orphaned Filler looks once, not on every tick");

        // One whose box is not to be found looks once as well, not on every tick of the load.
        AtomicInteger fruitless = new AtomicInteger();
        TileFiller lost = loadedFiller(oldPlannerFillerTag());
        for (int tick = 0; tick < 3; tick++) {
            lost.migrateOrphanedBoxOnce(() -> {
                fruitless.incrementAndGet();
                return new LevelSavedDataVolumeBoxes(FakeLevels.fake(false, Level.OVERWORLD));
            });
        }
        Assertions.assertEquals(1, fruitless.get(), "a Filler with no box to find must not search on every tick");
        Assertions.assertFalse(lost.box.isInitialized());

        // A Filler placed with no marker box behind it saves markerBox true and no box: it is not a planner's.
        TileFiller boxless = loadedFiller(ValueIoTestUtil.write(newFiller()::writeData));
        boxless.migrateOrphanedBoxOnce(() -> {
            lookups.incrementAndGet();
            return savedDataWith(box);
        });
        Assertions.assertEquals(1, lookups.get(), "a Filler that was never bound to a box must not go looking for one");
        Assertions.assertFalse(boxless.box.isInitialized());

        // A Filler that already holds its box has nothing to migrate.
        TileFiller boxed = newFiller();
        boxed.box.reset();
        boxed.box.setMin(BOX_MIN);
        boxed.box.setMax(BOX_MAX);
        boxed.markerBox = false;
        TileFiller reloaded = loadedFiller(ValueIoTestUtil.write(boxed::writeData));
        reloaded.migrateOrphanedBoxOnce(() -> {
            lookups.incrementAndGet();
            return savedDataWith(box);
        });
        Assertions.assertEquals(1, lookups.get(), "a Filler with its box needs no look");
    }

    @Test
    public void aVolumeBoxFromAnOldSaveLosesTheRetiredAddonTargetAndWritesNoAddons() {
        VolumeBox box = loadBox(oldVolumeBoxTag(true, true));

        List<Lock.Target> targets = box.locks.get(0).targets;

        Assertions.assertEquals(3, targets.size());
        Assertions.assertTrue(targets.get(0) instanceof Lock.Target.TargetRemove);
        Assertions.assertTrue(targets.get(1) instanceof Lock.Target.TargetResize);
        Assertions.assertTrue(targets.get(2) instanceof Lock.Target.TargetUsedByMachine);
        Assertions.assertFalse(box.writeToNBT().contains("addons"), "the box no longer writes an addon list");
    }
}
