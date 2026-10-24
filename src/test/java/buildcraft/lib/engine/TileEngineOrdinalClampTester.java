/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.engine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * The engine save stores {@code orientation} and {@code powerStage} as enum ordinals. Loading used to clamp them with
 * {@code Math.min} only, so a negative byte (a corrupt, hand-edited, or foreign save) indexed before the arrays and
 * threw {@code ArrayIndexOutOfBoundsException} during chunk load. The clamp must cover the whole
 * {@code [0, length - 1]} range, degrading a bad ordinal to the first entry instead of crashing the load.
 */
public class TileEngineOrdinalClampTester {

    private static TileEngineBase_BC8 engineReading(CompoundTag save) {
        // Typed as the base so readData resolves against its buildcraft.lib.engine declaration
        // (the stone engine's own override is protected in buildcraft.energy.tile).
        TileEngineBase_BC8 engine = new TileEngineStone_BC8(
            BlockPos.ZERO, BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState()
        );
        engine.readData(ValueIoTestUtil.inputOf(save));
        return engine;
    }

    @Test
    public void aNegativeOrientationClampsToTheFirstDirection() {
        CompoundTag corrupt = ValueIoTestUtil.write(out -> {
            out.putByte("orientation", (byte) -5);
            out.putByte("powerStage", (byte) 3);
        });

        TileEngineBase_BC8 engine = engineReading(corrupt);

        Assertions.assertEquals(Direction.values()[0], engine.orientation,
                "a negative orientation ordinal must clamp to the first direction, not throw");
        Assertions.assertEquals(EnumPowerStage.VALUES[3], engine.getPowerStage(),
                "an in-range power stage beside the bad one still loads exactly");
    }

    @Test
    public void aNegativePowerStageClampsToTheFirstStage() {
        CompoundTag corrupt = ValueIoTestUtil.write(out -> {
            out.putByte("orientation", (byte) 2);
            out.putByte("powerStage", (byte) -2);
        });

        TileEngineBase_BC8 engine = engineReading(corrupt);

        Assertions.assertEquals(EnumPowerStage.VALUES[0], engine.getPowerStage(),
                "a negative power-stage ordinal must clamp to the first stage, not throw");
        Assertions.assertEquals(Direction.values()[2], engine.orientation,
                "an in-range orientation beside the bad one still loads exactly");
    }

    @Test
    public void oversizedOrdinalsClampToTheLastEntries() {
        CompoundTag corrupt = ValueIoTestUtil.write(out -> {
            out.putByte("orientation", (byte) 99);
            out.putByte("powerStage", (byte) 99);
        });

        TileEngineBase_BC8 engine = engineReading(corrupt);

        Assertions.assertEquals(Direction.values()[Direction.values().length - 1], engine.orientation,
                "an oversized orientation ordinal keeps clamping to the last direction");
        Assertions.assertEquals(EnumPowerStage.VALUES[EnumPowerStage.VALUES.length - 1], engine.getPowerStage(),
                "an oversized power-stage ordinal keeps clamping to the last stage");
    }

    @Test
    public void anEmptySaveFallsBackToTheDefaults() {
        TileEngineBase_BC8 engine = engineReading(ValueIoTestUtil.write(out -> {}));

        Assertions.assertEquals(Direction.UP, engine.orientation, "no orientation entry defaults to UP");
        Assertions.assertEquals(EnumPowerStage.VALUES[0], engine.getPowerStage(),
                "no power-stage entry defaults to the first stage");
    }
}
