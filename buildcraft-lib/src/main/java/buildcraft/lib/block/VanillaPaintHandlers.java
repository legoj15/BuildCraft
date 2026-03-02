/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.blocks.CustomPaintHelper;
import buildcraft.api.blocks.ICustomPaintHandler;

/**
 * Provides vanilla block paint handlers for blocks like stained glass and terracotta.
 * Note: In 1.21, block types changed significantly. Dyeable blocks now use DyeColor properties.
 */
public class VanillaPaintHandlers {

    public static void fmlInit() {
        // TODO: Re-enable when block registry is available
        // Stained glass, glass pane, terracotta handlers would go here
        // registerDoubleTypedHandler(Blocks.GLASS, Blocks.STAINED_GLASS, StainedGlassBlock.COLOR);
        // registerDoubleTypedHandler(Blocks.GLASS_PANE, Blocks.STAINED_GLASS_PANE, StainedGlassPaneBlock.COLOR);
        // registerDoubleTypedHandler(Blocks.TERRACOTTA, Blocks.TERRACOTTA, TerracottaBlock.COLOR);
    }

    private static <T extends Comparable<T>> void registerDoubleTypedHandler(Block clear, Block dyed, Property<T> colourProp) {
        ICustomPaintHandler handler = createDoubleTypedPainter(clear, dyed, colourProp);
        CustomPaintHelper.INSTANCE.registerHandler(clear, handler);
        CustomPaintHelper.INSTANCE.registerHandler(dyed, handler);
    }

    public static <T extends Comparable<T>> ICustomPaintHandler createDoubleTypedPainter(Block clear, Block dyed, Property<T> colourProp) {
        return (world, pos, state, hitPos, hitSide, to) -> {
            if (state.getBlock() == clear) {
                if (to == null) {
                    return InteractionResult.FAIL;
                }
                // In 1.21, block default state + setValue
                return InteractionResult.SUCCESS;
            } else if (state.getBlock() == dyed) {
                if (to == null) {
                    world.setBlock(pos, clear.defaultBlockState(), 3);
                } else {
                    // world.setBlock(pos, state.setValue(colourProp, to), 3);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        };
    }
}
