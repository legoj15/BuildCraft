/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** A direction parameter stores its face as one byte. A byte outside 0..5 (a corrupt or hand-edited gate save) used to
 *  index {@code Direction.values()} blind and throw out of the gate's load; it reads as "no direction" instead, the
 *  same value a parameter saved without a direction has. */
@SuppressWarnings("deprecation") // StatementParameterDirection is @Deprecated by upstream's own note; still loaded.
public class StatementParameterDirectionTest {

    private static CompoundTag savedWithByte(byte direction) {
        CompoundTag nbt = new CompoundTag();
        nbt.putByte("direction", direction);
        return nbt;
    }

    @Test
    public void everyRealDirectionRoundTrips() {
        for (Direction side : Direction.values()) {
            CompoundTag nbt = new CompoundTag();
            new StatementParameterDirection(side).writeToNbt(nbt);

            Assertions.assertEquals(side, StatementParameterDirection.readFromNbt(nbt).getDirection(),
                    side + " survives a save and load");
        }
    }

    @Test
    public void anOutOfRangeDirectionByteLoadsAsNoDirection() {
        for (byte bad : new byte[] { 6, 7, 127, -1, -128 }) {
            StatementParameterDirection loaded = Assertions.assertDoesNotThrow(
                    () -> StatementParameterDirection.readFromNbt(savedWithByte(bad)),
                    "direction byte " + bad + " must not fail the load");

            Assertions.assertNull(loaded.getDirection(), "direction byte " + bad + " is not a face");
        }
    }

    @Test
    public void aParameterSavedWithoutADirectionLoadsAsNoDirection() {
        Assertions.assertNull(StatementParameterDirection.readFromNbt(new CompoundTag()).getDirection());
    }
}
