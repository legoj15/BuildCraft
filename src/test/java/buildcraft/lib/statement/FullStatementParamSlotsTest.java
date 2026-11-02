/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.statement;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.filler.IFillerPattern;

import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;

/** A statement's parameter slots end at its maxParameters(): the GUI draws the rest blocked, so nothing may live
 *  there for the statement to read (#90: the slot at index maxParameters() survived a switch, a save and a drag). */
public class FullStatementParamSlotsTest {
    private static FullStatement<IFillerPattern> turnedCircle() {
        FullStatement<IFillerPattern> full = new FullStatement<>(FillerType.INSTANCE, 4, null);
        full.set(BCBuildersStatements.PATTERN_CIRCLE);
        full.set(2, PatternParameterRotation.QUARTER);
        Assertions.assertEquals(PatternParameterRotation.QUARTER, full.get(2));
        return full;
    }

    @Test
    public void switchingToFewerParametersClearsTheSlotsPastThem() {
        FullStatement<IFillerPattern> full = turnedCircle();
        full.set(BCBuildersStatements.PATTERN_SQUARE);
        Assertions.assertNull(full.get(2), "Square has two parameters but kept Circle's rotation");
    }

    @Test
    public void loadingDropsParametersPastTheStatementsLast() {
        CompoundTag nbt = turnedCircle().writeToNbt();
        FullStatement<IFillerPattern> square = new FullStatement<>(FillerType.INSTANCE, 4, null);
        square.set(BCBuildersStatements.PATTERN_SQUARE);
        // An older save: Square with the rotation an earlier Circle left in slot 2.
        nbt.put("s", square.writeToNbt().get("s"));

        FullStatement<IFillerPattern> loaded = new FullStatement<>(FillerType.INSTANCE, 4, null);
        loaded.readFromNbt(nbt);
        Assertions.assertEquals(BCBuildersStatements.PATTERN_SQUARE, loaded.get());
        Assertions.assertNull(loaded.get(2), "a saved rotation came back on a pattern without a rotation slot");
    }

    @Test
    public void blockedSlotsRefuseADroppedParameter() {
        FullStatement<IFillerPattern> full = new FullStatement<>(FillerType.INSTANCE, 4, null);
        full.set(BCBuildersStatements.PATTERN_SQUARE);
        Assertions.assertFalse(full.canSet(2, PatternParameterRotation.QUARTER));
        full.set(BCBuildersStatements.PATTERN_CIRCLE);
        Assertions.assertTrue(full.canSet(2, PatternParameterRotation.QUARTER));
    }
}
