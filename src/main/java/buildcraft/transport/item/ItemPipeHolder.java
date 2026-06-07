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
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.level.block.Block;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.energy.BCEnergyConfig;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.transport.BCTransportItems;

/** An item that, when placed, creates a pipe block with the associated {@link PipeDefinition}. */
@SuppressWarnings("deprecation")
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

    //? if <1.21.10 {
    /*@Override
    public String getDescriptionId() {
        // 1.21.1: BlockItem.getDescriptionId() returns the BLOCK's id, and every pipe item wraps the
        // single shared pipe_holder block -- so all pipes would display the untranslated
        // "block.buildcraftunofficial.pipe_holder". Return the ITEM's own key
        // (item.buildcraftunofficial.pipe_<x>_item), which the lang file is keyed on. 26.1+ resolves
        // names via the ITEM_NAME data component instead, so no override is needed there (and this
        // branch is absent on those nodes, keeping them byte-identical).
        return this.getOrCreateDescriptionId();
    }*/
    //?}

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
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;*/
    //?}
        // Descriptive tip from lang file (e.g. "Extraction pipe", "Sorts items")
        String id = definition.identifier; // e.g. "buildcraftunofficial:wood_item"
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String tipKey = "tip.pipe." + path;
        if (I18n.exists(tipKey)) {
            tooltip.accept(Component.literal(I18n.get(tipKey)).withStyle(ChatFormatting.GRAY));
        }

        // Flow rate tooltip. Fluid pipes have a true per-segment cap (center-section
        // bottleneck), so no qualifier; power/RF pipes cap per-face per-tick on the pull
        // side and can carry multi-face convergence above the listed number, so we suffix
        // " per face" to set the right expectation.
        if (definition.flowType == PipeApi.flowFluids) {
            PipeApi.FluidTransferInfo fti = PipeApi.getFluidTransferInfo(definition);
            tooltip.accept(Component.literal(LocaleUtil.localizeFluidFlow(fti.transferPerTick))
                    .withStyle(ChatFormatting.GRAY));
        } else if (definition.flowType == PipeApi.flowPower) {
            PipeApi.PowerTransferInfo pti = PipeApi.getPowerTransferInfo(definition);
            tooltip.accept(Component.literal(LocaleUtil.localizeMjFlow(pti.transferPerTick) + " per face")
                    .withStyle(ChatFormatting.GRAY));
        } else if (definition.flowType == PipeApi.flowRf) {
            PipeApi.RedstoneFluxTransferInfo rti = PipeApi.getRfTransferInfo(definition);
            tooltip.accept(Component.literal(LocaleUtil.localizeRfFlow(rti.transferPerTick) + " per face")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
