/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.transport.wire.WireManager;

/**
 * {@link WireManager#readFromNbt} reads the {@code parts} int array of a pipe save or of a blueprint's tile NBT: flat
 * {@code (part ordinal, colour id)} pairs. Neither source is guaranteed well formed, and an exception out of here
 * escapes {@code TilePipeHolder.readData}, so a bad pair is dropped and the good ones around it still load.
 */
public class WireManagerNbtTester {

    private static WireManager load(int... parts) {
        CompoundTag nbt = new CompoundTag();
        nbt.putIntArray("parts", parts);
        // readFromNbt never touches the holder: it only fills the part map.
        WireManager manager = new WireManager(null);
        manager.readFromNbt(nbt);
        return manager;
    }

    @Test
    public void outOfRangePartIndexIsSkipped() {
        Assertions.assertTrue(load(99, 0).parts.isEmpty(), "part 99 does not exist");
        Assertions.assertTrue(load(-1, 0).parts.isEmpty(), "a negative part index does not exist");
        Assertions.assertTrue(load(EnumWirePart.VALUES.length, 0).parts.isEmpty(), "one past the last part");
    }

    @Test
    public void unknownColourIdIsSkipped() {
        // DyeColor.byId maps an unknown id to white instead of failing, so without a check the part would load white.
        Assertions.assertTrue(load(0, 99).parts.isEmpty(), "colour 99 does not exist");
        Assertions.assertTrue(load(0, -1).parts.isEmpty(), "a negative colour id does not exist");
    }

    @Test
    public void oddLengthArrayIgnoresTheUnpairedElement() {
        Assertions.assertTrue(load(5).parts.isEmpty(), "a lone element has no colour to pair with");

        WireManager manager = load(0, 3, 1);
        Assertions.assertEquals(1, manager.parts.size(), "the trailing element is dropped, the pair before it kept");
        Assertions.assertEquals(DyeColor.byId(3), manager.parts.get(EnumWirePart.VALUES[0]));
    }

    @Test
    public void validPairsBesideBadOnesStillLoad() {
        WireManager manager = load(99, 0, 2, 5, 1, 99, 3, 7, 4);
        Assertions.assertEquals(2, manager.parts.size(), "only the two well-formed pairs load");
        Assertions.assertEquals(DyeColor.byId(5), manager.parts.get(EnumWirePart.VALUES[2]));
        Assertions.assertEquals(DyeColor.byId(7), manager.parts.get(EnumWirePart.VALUES[3]));
    }

    @Test
    public void whiteIsStillALegalColour() {
        // The unknown-colour check must not reject id 0, the one id byId also returns for garbage.
        WireManager manager = load(4, 0);
        Assertions.assertEquals(DyeColor.WHITE, manager.parts.get(EnumWirePart.VALUES[4]));
    }

    @Test
    public void writtenNbtReadsBackUnchanged() {
        WireManager source = new WireManager(null);
        source.parts.put(EnumWirePart.VALUES[1], DyeColor.RED);
        source.parts.put(EnumWirePart.VALUES[6], DyeColor.BLACK);

        WireManager copy = new WireManager(null);
        copy.readFromNbt(source.writeToNbt());
        Assertions.assertEquals(source.parts, copy.parts);
    }
}
