/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

//? if >=26.1 {
import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.core.marker.PathSavedData;
import buildcraft.core.marker.VolumeSavedData;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.lib.marker.MarkerSavedData;
import buildcraft.robotics.RobotRegistry;
import buildcraft.transport.wire.SavedDataWireSystems;
import buildcraft.transport.wire.WireSystem;
//?}

/**
 * What "holds no content" means for each real BuildCraft saved-data type - the question the recovery of the 1.21.x data
 * turns on ({@link LegacySavedData.Recoverable#hasNoContent}). The wrong answer in one direction orphans the old data
 * again; in the other it overwrites a 26.1+ file that has real content, so each type is checked both ways, through
 * the codec the storage reads its file with (an empty file written by a build without the migration is exactly what
 * a blank payload decodes to). The box type needs a level for any entry, so its non-empty side is the game test's.
 */
public class LegacySavedDataContentTest {

    //? if >=26.1 {
    /** Encodes and decodes {@code data} through the codec of {@code type}, as a save and a load would. */
    private static <T extends SavedData & LegacySavedData.Recoverable> T roundTrip(SavedDataType<T> type, T data) {
        Tag tag = type.codecFactory().create(null).encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        return type.codecFactory().create(null).parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static <T extends SavedData & LegacySavedData.Recoverable> T fresh(SavedDataType<T> type) {
        return type.factory().create(null);
    }

    // ---- the robot registry ----

    @Test
    public void aFreshRobotRegistryHoldsNoContent() {
        Assertions.assertTrue(roundTrip(RobotRegistry.TYPE, fresh(RobotRegistry.TYPE)).hasNoContent());
    }

    @Test
    public void anAdvancedRobotIdCounterAloneIsNotContent() {
        RobotRegistry registry = fresh(RobotRegistry.TYPE);
        registry.getNextRobotId();
        registry.getNextRobotId();

        Assertions.assertTrue(roundTrip(RobotRegistry.TYPE, registry).hasNoContent(),
                "robots created while the registry was empty only moved the counter; the old file's reservations "
                        + "and stations are what a player lost");
    }

    @Test
    public void aResourceReservationIsContent() {
        RobotRegistry registry = fresh(RobotRegistry.TYPE);
        Assertions.assertTrue(registry.take(new ResourceIdBlock(new BlockPos(1, 64, 2)), 1000L));

        Assertions.assertFalse(roundTrip(RobotRegistry.TYPE, registry).hasNoContent());
    }

    @Test
    public void theRobotRegistryKeepsTheFartherCounterOfTheTwoWhenItReplacesAnEmptyOne() {
        RobotRegistry recovered = fresh(RobotRegistry.TYPE);
        recovered.take(new ResourceIdBlock(new BlockPos(1, 64, 2)), 1000L);
        long recoveredNext = recovered.getNextRobotId();
        RobotRegistry empty = fresh(RobotRegistry.TYPE);
        for (int i = 0; i < 5; i++) {
            empty.getNextRobotId();
        }

        recovered.absorbBlank(empty);

        Assertions.assertEquals(empty.getNextRobotId(), recovered.getNextRobotId(),
                "the empty registry had handed out five ids, which live robots now carry");
        Assertions.assertTrue(recoveredNext < recovered.getNextRobotId());
    }

    @Test
    public void theRobotRegistryNeverLowersItsCounterToTheEmptyOnes() {
        RobotRegistry recovered = fresh(RobotRegistry.TYPE);
        for (int i = 0; i < 5; i++) {
            recovered.getNextRobotId();
        }
        long before = recovered.getNextRobotId();

        recovered.absorbBlank(fresh(RobotRegistry.TYPE));

        Assertions.assertEquals(before + 1, recovered.getNextRobotId(), "an empty registry has nothing to add");
    }

    // ---- wire systems ----

    @Test
    public void freshWireSystemsHoldNoContent() {
        Assertions.assertTrue(roundTrip(SavedDataWireSystems.TYPE, fresh(SavedDataWireSystems.TYPE)).hasNoContent());
    }

    @Test
    public void aWireSystemIsContent() {
        SavedDataWireSystems systems = fresh(SavedDataWireSystems.TYPE);
        systems.addWireSystem(new WireSystem(ImmutableList.of(
                new WireSystem.WireElement(new BlockPos(1, 64, 2), EnumWirePart.EAST_UP_SOUTH)), DyeColor.RED), false);

        SavedDataWireSystems back = roundTrip(SavedDataWireSystems.TYPE, systems);

        Assertions.assertFalse(back.hasNoContent());
        Assertions.assertEquals(1, back.wireSystems.size());
    }

    // ---- markers (both kinds share the base) ----

    private static void assertMarkerDataCounts(MarkerSavedData blank, MarkerSavedData withMarkers) {
        Assertions.assertTrue(blank.hasNoContent(), "no marker and no connection is no content");
        Assertions.assertFalse(withMarkers.hasNoContent(), "a marker is content");
    }

    @Test
    public void volumeMarkersHoldContentOnlyWhenTheyHoldAMarker() {
        VolumeSavedData withMarker = fresh(VolumeSavedData.TYPE);
        withMarker.markerPositions.add(new BlockPos(1, 64, 2));

        assertMarkerDataCounts(roundTrip(VolumeSavedData.TYPE, fresh(VolumeSavedData.TYPE)),
                roundTrip(VolumeSavedData.TYPE, withMarker));
    }

    @Test
    public void pathMarkersHoldContentOnlyWhenTheyHoldAMarker() {
        PathSavedData withMarker = fresh(PathSavedData.TYPE);
        withMarker.markerPositions.add(new BlockPos(1, 64, 2));

        assertMarkerDataCounts(roundTrip(PathSavedData.TYPE, fresh(PathSavedData.TYPE)),
                roundTrip(PathSavedData.TYPE, withMarker));
    }

    @Test
    public void aConnectionBetweenMarkersIsContentEvenIfTheMarkerListIsNot() {
        // Conservative: any entry at all makes the data "real", so an odd file is never overwritten.
        PathSavedData data = fresh(PathSavedData.TYPE);
        data.markerConnections.add(new java.util.ArrayList<>(java.util.List.of(new BlockPos(1, 64, 2))));

        Assertions.assertFalse(roundTrip(PathSavedData.TYPE, data).hasNoContent());
    }

    // ---- volume boxes ----

    @Test
    public void noVolumeBoxesIsNoContent() {
        Assertions.assertTrue(
                roundTrip(LevelSavedDataVolumeBoxes.createType(null), fresh(LevelSavedDataVolumeBoxes.createType(null)))
                        .hasNoContent());
    }
    //?}
}
