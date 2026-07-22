/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.SoundUtil;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.PluggableLens;

public class ItemPluggableLens extends Item implements IItemPluggable {
    public ItemPluggableLens(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        DyeColor colour = getColour(stack);
        boolean filter = isFilter(stack);
        // Composed from a colour word and a kind word ("Clear Lens", "Red Filter"). The order lives in
        // the ".name" format key rather than being concatenated here, so languages that put the colour
        // after the noun can reorder it. The base key doubles as the "Lens" kind word.
        // Object-typed so it accepts either the String or the Component form of the colour name —
        // Component.translatable wraps a non-Component argument in a literal either way.
        Object colourName = colour == null
            ? Component.translatable(getDescriptionId() + ".clear")
            : ColourUtil.getTextFullTooltip(colour);
        Component typeName = Component.translatable(filter ? getDescriptionId() + ".filter" : getDescriptionId());
        return Component.translatable(getDescriptionId() + ".name", colourName, typeName);
    }

    /** Creates a lens/filter item stack with the given colour and filter state. */
    @Nonnull
    public ItemStack getStack(@Nullable DyeColor colour, boolean isFilter) {
        ItemStack stack = new ItemStack(this);
        CompoundTag nbt = NBTUtilBC.getItemData(stack);
        if (colour != null) {
            nbt.putString("colour", colour.getName());
        }
        nbt.putBoolean("isFilter", isFilter);
        NBTUtilBC.setItemData(stack, nbt);
        return stack;
    }

    /** Reads the colour and filter state from an item stack. */
    @Nullable
    public static DyeColor getColour(@Nonnull ItemStack stack) {
        CompoundTag nbt = NBTUtilBC.getItemData(stack);
        if (nbt.contains("colour")) {
            return DyeColor.byName(NBTUtilBC.getString(nbt, "colour", ""), null);
        }
        return null;
    }

    public static boolean isFilter(@Nonnull ItemStack stack) {
        return NBTUtilBC.getBoolean(NBTUtilBC.getItemData(stack), "isFilter", false);
    }

    @Nullable
    @Override
    public PipePluggable onPlace(@Nonnull ItemStack stack, IPipeHolder holder, Direction side, Player player,
        InteractionHand hand) {
        IPipe pipe = holder.getPipe();
        if (pipe == null || !(pipe.getFlow() instanceof IFlowItems)) {
            return null;
        }
        DyeColor colour = getColour(stack);
        boolean filter = isFilter(stack);
        SoundUtil.playBlockPlace(holder.getPipeWorld(), holder.getPipePos(), Blocks.GLASS.defaultBlockState());
        return new PluggableLens(BCSiliconPlugs.lens, holder, side, colour, filter);
    }

    @Nonnull
    @Override
    public AABB getPlacementBoundingBox(@Nonnull ItemStack stack, Direction side) {
        return PluggableLens.boundingBoxFor(side);
    }
}
