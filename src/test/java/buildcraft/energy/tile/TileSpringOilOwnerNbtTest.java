/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.tile;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import buildcraft.core.BCCoreBlocks;
import buildcraft.lib.test.ValueIoTestUtil;

/** An oil spring remembers, per pumping player, a name/id pair. A save that has the id but no name used to read back
 *  as the English word "Unknown" (persisted on the next save); a save with no usable id threw out of the load. */
public class TileSpringOilOwnerNbtTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static TileSpringOil newSpring() {
        return new TileSpringOil(BlockPos.ZERO, BCCoreBlocks.SPRING_OIL.get().defaultBlockState());
    }

    private static CompoundTag save(TileSpringOil spring) {
        return ValueIoTestUtil.write(spring::writeData);
    }

    private static CompoundTag onePump(boolean withName, boolean withId) {
        return ValueIoTestUtil.write(out -> {
            out.putInt("totalSources", 8);
            out.putInt("pumpCount", 1);
            if (withName) {
                out.putString("pump_0_name", "Steve");
            }
            if (withId) {
                out.putString("pump_0_id", ID.toString());
            }
            out.putLong("pump_0_tick", 5L);
            out.putInt("pump_0_pumped", 3);
        });
    }

    @Test
    public void namedPumperSurvivesASaveLoadCycle() {
        TileSpringOil spring = newSpring();
        spring.readData(ValueIoTestUtil.inputOf(onePump(true, true)));

        CompoundTag saved = save(spring);

        Assertions.assertEquals("Steve", ValueIoTestUtil.stringOr(saved, "pump_0_name", "<absent>"));
        Assertions.assertEquals(ID.toString(), ValueIoTestUtil.stringOr(saved, "pump_0_id", "<absent>"));
    }

    @Test
    public void pumperWithoutNameDoesNotInventAnEnglishName() {
        TileSpringOil spring = newSpring();

        spring.readData(ValueIoTestUtil.inputOf(onePump(false, true)));

        CompoundTag saved = save(spring);
        Assertions.assertEquals(ID.toString(), ValueIoTestUtil.stringOr(saved, "pump_0_id", "<absent>"),
                "the pumper is still tracked by id");
        Assertions.assertEquals("", ValueIoTestUtil.stringOr(saved, "pump_0_name", "<absent>"),
                "the load -> save cycle must leave the name empty, not bake a literal English name into the save");
    }

    @Test
    public void pumperWithoutAnIdIsSkippedInsteadOfFailingTheLoad() {
        TileSpringOil spring = newSpring();

        Assertions.assertDoesNotThrow(() -> spring.readData(ValueIoTestUtil.inputOf(onePump(true, false))),
                "a hand-edited or corrupt entry must not take the whole spring down on chunk load");

        Assertions.assertEquals(0, ValueIoTestUtil.inputOf(save(spring)).getIntOr("pumpCount", -1),
                "the entry has no id to key it by, so it is dropped");
    }
}
