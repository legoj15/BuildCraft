/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot.pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

import buildcraft.builders.snapshot.pattern.parameter.PatternParameterCenter;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;

/**
 * A pattern parameter saved with a value that is no constant of its type (a hand-edited or damaged blueprint) reads
 * back as the parameter's default instead of throwing, and does not pick some other real value: the default of a
 * facing is DOWN (what a sphere part starts with, and what a tag with no value reads as) and that of a pyramid's center
 * is CENTER.
 */
public class PatternParameterReadTest {

    private static CompoundTag with(String key, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putByte(key, (byte) value);
        return tag;
    }

    @Test
    public void aFacingIndexPastItsLastValueReadsAsTheDefault() {
        for (int index : new int[] {PatternParameterFacing.values().length, 7, 127, -1, -128}) {
            Assertions.assertSame(PatternParameterFacing.DOWN, PatternParameterFacing.readFromNbt(with("v", index)),
                "a facing saved as " + index);
        }
        Assertions.assertSame(PatternParameterFacing.DOWN, PatternParameterFacing.readFromNbt(new CompoundTag()));
        for (PatternParameterFacing facing : PatternParameterFacing.values()) {
            Assertions.assertSame(facing, PatternParameterFacing.readFromNbt(with("v", facing.ordinal())));
        }
    }

    @Test
    public void aCenterIndexOutsideItsValuesReadsAsTheDefault() {
        for (int index : new int[] {PatternParameterCenter.values().length, 99, -1}) {
            Assertions.assertSame(PatternParameterCenter.CENTER, PatternParameterCenter.readFromNbt(with("dir", index)),
                "a center saved as " + index);
        }
        for (PatternParameterCenter center : PatternParameterCenter.values()) {
            Assertions.assertSame(center, PatternParameterCenter.readFromNbt(with("dir", center.ordinal())));
        }
    }
}
