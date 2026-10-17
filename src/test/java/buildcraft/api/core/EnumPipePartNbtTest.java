/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;

/** {@link EnumPipePart}'s NBT form: the part's name as a string. Reading used to ignore its input and answer
 *  {@code CENTER} for everything, so the side a resource id (a block claim, a request slot) was saved with was
 *  lost on the next load. */
public class EnumPipePartNbtTest {

    @Test
    public void everyPartSurvivesARoundTrip() {
        for (EnumPipePart part : EnumPipePart.values()) {
            Assertions.assertSame(part, EnumPipePart.readFromNbt(part.writeToNbt()),
                    part + " must read back as itself");
        }
    }

    @Test
    public void aStoredNameDecodesToThatPart() {
        Assertions.assertSame(EnumPipePart.UP, EnumPipePart.readFromNbt(StringTag.valueOf("UP")));
        Assertions.assertSame(EnumPipePart.WEST, EnumPipePart.readFromNbt(StringTag.valueOf("WEST")));
    }

    @Test
    public void anAbsentOrUnreadableTagFallsBackToCenter() {
        Assertions.assertSame(EnumPipePart.CENTER, EnumPipePart.readFromNbt(null), "no tag stored");
        Assertions.assertSame(EnumPipePart.CENTER, EnumPipePart.readFromNbt(StringTag.valueOf("SIDEWAYS")),
                "a name no part has");
        Assertions.assertSame(EnumPipePart.CENTER, EnumPipePart.readFromNbt(StringTag.valueOf("")),
                "an empty name");
        Assertions.assertSame(EnumPipePart.CENTER, EnumPipePart.readFromNbt(IntTag.valueOf(2)),
                "a tag of the wrong kind");
    }
}
