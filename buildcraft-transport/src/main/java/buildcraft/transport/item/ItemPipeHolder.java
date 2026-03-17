/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.BCTransportItems;

/** An item that, when placed, creates a pipe block with the associated {@link PipeDefinition}. */
public class ItemPipeHolder extends BlockItem implements IItemPipe {
    public final PipeDefinition definition;

    public ItemPipeHolder(Block block, PipeDefinition definition, Item.Properties props) {
        super(block, props);
        this.definition = definition;
    }

    @Override
    public PipeDefinition getDefinition() {
        return definition;
    }

    /** Registers this item as the canonical item for its definition in PipeApi. */
    public ItemPipeHolder registerWithPipeApi() {
        PipeApi.pipeRegistry.setItemForPipe(definition, this);
        return this;
    }

    /** Prepends the paint colour name (in matching chat colour) to the item name, e.g.
     *  "Orange Diamond Kinesis Pipe" with "Orange" rendered in gold — matching 1.12.2. */
    @Override
    public Component getName(ItemStack stack) {
        DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != null) {
            Component colourName = Component.literal(ColourUtil.getTextFullTooltip(col))
                    .withStyle(ColourUtil.convertColourToTextFormat(col));
            return Component.literal("").append(colourName).append(" ").append(super.getName(stack));
        }
        return super.getName(stack);
    }
}
