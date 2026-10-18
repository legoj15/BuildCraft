/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCCoreItems;

/** A SPOT map location stores the face as one byte. Read blind as a {@code Direction.values()} index, a byte outside
 *  0..5 (7.1.x's {@code ForgeDirection.UNKNOWN} was 6, and a corrupt or hand-edited stack holds anything) threw out of
 *  the tooltip - so the stack could not even be hovered in an inventory - and out of every reader a robot gate uses.
 *  It reads as "no face" instead, the way the robot readers treat the same byte. */
public class ItemMapLocationSideTest extends VanillaSetupBaseTester {

    private static final byte[] OUT_OF_RANGE = { 6, 7, 127, -1, -128 };

    private static ItemStack spotWithSideByte(byte side) {
        CompoundTag tag = new CompoundTag();
        tag.putString("mapType", "SPOT");
        tag.putInt("x", 11);
        tag.putInt("y", 64);
        tag.putInt("z", -5);
        tag.putByte("side", side);
        ItemStack stack = new ItemStack(BCCoreItems.MAP_LOCATION.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static List<Component> tooltipOf(ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
    }

    @Test
    public void everyRealSideReadsBackAsItself() {
        for (Direction side : Direction.values()) {
            ItemStack stack = spotWithSideByte((byte) side.ordinal());

            Assertions.assertEquals(side, BCCoreItems.MAP_LOCATION.get().getPointSide(stack),
                    "getPointSide reads " + side);
            Assertions.assertEquals(side, ItemMapLocation.getPointFace(stack), "getPointFace reads " + side);
            Assertions.assertTrue(tooltipOf(stack).stream().anyMatch(line -> line.getString().contains(side.toString())),
                    "the tooltip names " + side);
        }
    }

    @Test
    public void anOutOfRangeSideByteReadsAsNoSide() {
        for (byte bad : OUT_OF_RANGE) {
            ItemStack stack = spotWithSideByte(bad);

            Assertions.assertNull(Assertions.assertDoesNotThrow(
                    () -> BCCoreItems.MAP_LOCATION.get().getPointSide(stack), "getPointSide, side byte " + bad),
                    "side byte " + bad + " is not a face");
            Assertions.assertNull(Assertions.assertDoesNotThrow(
                    () -> ItemMapLocation.getPointFace(stack), "getPointFace, side byte " + bad),
                    "side byte " + bad + " is not a face");
        }
    }

    @Test
    public void aSpotWithAnOutOfRangeSideByteStillHasATooltipAndItsPoint() {
        for (byte bad : OUT_OF_RANGE) {
            ItemStack stack = spotWithSideByte(bad);

            List<Component> lines = Assertions.assertDoesNotThrow(() -> tooltipOf(stack),
                    "hovering a spot with side byte " + bad);
            Assertions.assertTrue(lines.stream().anyMatch(line -> line.getString().contains("11, 64, -5")),
                    "the point is still shown for side byte " + bad + ", got: " + lines);
            Assertions.assertEquals(11, BCCoreItems.MAP_LOCATION.get().getPoint(stack).getX(),
                    "the point itself is intact");
        }
    }
}
