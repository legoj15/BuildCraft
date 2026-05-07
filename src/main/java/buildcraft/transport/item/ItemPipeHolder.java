/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.energy.BCEnergyConfig;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.LocaleUtil;
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
     *  "Orange Diamond Kinesis Pipe" with "Orange" rendered in gold — matching 1.12.2.
     *  FE pipes (identifier ending in {@code _rf}) also flip between "FE"/"RF" wording
     *  based on {@link BCEnergyConfig#useRfNaming}; other pipe types are unaffected. */
    @Override
    public Component getName(ItemStack stack) {
        Component baseName = isFePipe()
                ? Component.translatable(BCEnergyConfig.rfFeKey(getDescriptionId()))
                : super.getName(stack);
        DyeColor col = stack.get(BCTransportItems.PIPE_COLOUR.get());
        if (col != null) {
            Component colourName = Component.literal(ColourUtil.getTextFullTooltip(col));
            return Component.literal("").append(colourName).append(" ").append(baseName);
        }
        return baseName;
    }

    private boolean isFePipe() {
        return definition.identifier != null && definition.identifier.endsWith("_rf");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        // Descriptive tip from lang file (e.g. "Extraction pipe", "Sorts items")
        String id = definition.identifier; // e.g. "buildcraftunofficial:wood_item"
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String tipKey = "tip.pipe." + path;
        if (I18n.exists(tipKey)) {
            tooltip.accept(Component.literal(I18n.get(tipKey)).withStyle(ChatFormatting.GRAY));
        }

        // Flow rate tooltip for fluid and power pipes
        if (definition.flowType == PipeApi.flowFluids) {
            PipeApi.FluidTransferInfo fti = PipeApi.getFluidTransferInfo(definition);
            tooltip.accept(Component.literal(LocaleUtil.localizeFluidFlow(fti.transferPerTick))
                    .withStyle(ChatFormatting.GRAY));
        } else if (definition.flowType == PipeApi.flowPower) {
            PipeApi.PowerTransferInfo pti = PipeApi.getPowerTransferInfo(definition);
            tooltip.accept(Component.literal(LocaleUtil.localizeMjFlow(pti.transferPerTick))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
