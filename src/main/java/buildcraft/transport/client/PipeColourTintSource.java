/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client;

//? if <1.21.10 {
/*import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.BCTransportItems;

/^* 1.21.1-only ItemColor that reads PIPE_COLOUR from a pipe item stack and returns the dye colour value for tinting
 * the painted overlay quads (tintIndex 0). Returns white (0xFFFFFF) when no colour is present so that white overlay
 * geometry is invisible. Later lines have no use for it: PipeItemModel paints pipe items itself there, and no item
 * JSON references a tint source for them. *^/
public final class PipeColourTintSource implements net.minecraft.client.color.item.ItemColor {
    public static final PipeColourTintSource INSTANCE = new PipeColourTintSource();

    private PipeColourTintSource() {}

    /^* Semi-transparent alpha for the overlay (76/255 ≈ 30%, matching overlay_stained.png). *^/
    private static final int OVERLAY_ALPHA = 76;

    @Override
    public int getColor(ItemStack stack, int tintIndex) {
        DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != null) {
            return (OVERLAY_ALPHA << 24) | ColourUtil.getLightHex(col);
        }
        return (OVERLAY_ALPHA << 24) | 0xFFFFFF;
    }
}*/
//?}
