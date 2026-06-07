/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.item;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import buildcraft.api.facades.FacadeType;
import buildcraft.api.facades.IFacade;
import buildcraft.api.facades.IFacadeItem;
import buildcraft.api.transport.IItemPluggable;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.SoundUtil;

import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadePhasedState;
import buildcraft.silicon.plug.FacadeStateManager;
import buildcraft.silicon.plug.PluggableFacade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

@SuppressWarnings("deprecation")
public class ItemPluggableFacade extends Item implements IItemPluggable, IFacadeItem {
    public ItemPluggableFacade(Item.Properties properties) {
        super(properties);
    }

    @Nonnull
    public ItemStack createItemStack(FacadeInstance state) {
        ItemStack item = new ItemStack(this);
        CompoundTag nbt = NBTUtilBC.getItemData(item);
        nbt.put("facade", state.writeToNbt());
        NBTUtilBC.setItemData(item, nbt);
        return item;
    }

    public static FacadeInstance getStates(@Nonnull ItemStack item) {
        CompoundTag nbt = NBTUtilBC.getItemData(item);

        String strPreview = NBTUtilBC.getString(nbt, "preview", "");
        if ("basic".equalsIgnoreCase(strPreview)) {
            return FacadeInstance.createSingle(FacadeStateManager.previewState, false);
        }

        // Handle legacy data migration from pre-facade format
        if (!nbt.contains("facade") && nbt.contains("states")) {
            // Only migrate if we actually have a facade to migrate.
            var statesList = NBTUtilBC.getList(nbt, "states", Tag.TAG_COMPOUND);
            if (!statesList.isEmpty()) {
                var firstElement = statesList.get(0);
                boolean isHollow = firstElement instanceof CompoundTag ct && NBTUtilBC.getBoolean(ct, "isHollow", false);
                CompoundTag tagFacade = new CompoundTag();
                tagFacade.putBoolean("isHollow", isHollow);
                tagFacade.put("states", statesList);
                nbt.put("facade", tagFacade);
            }
        }

        return FacadeInstance.readFromNbt(NBTUtilBC.getCompound(nbt, "facade"));
    }

    @Nonnull
    @Override
    public ItemStack getFacadeForBlock(BlockState state) {
        FacadeBlockStateInfo info = FacadeStateManager.validFacadeStates.get(state);
        if (info == null) {
            return ItemStack.EMPTY;
        } else {
            return createItemStack(FacadeInstance.createSingle(info, false));
        }
    }

    @Override
    public PipePluggable onPlace(@Nonnull ItemStack stack, IPipeHolder holder, Direction side, Player player,
        InteractionHand hand) {
        FacadeInstance fullState = getStates(stack);
        SoundUtil.playBlockPlace(holder.getPipeWorld(), holder.getPipePos(), fullState.phasedStates[0].stateInfo.state);
        return new PluggableFacade(BCSiliconPlugs.facade, holder, side, fullState);
    }

    @Nonnull
    @Override
    public AABB getPlacementBoundingBox(@Nonnull ItemStack stack, Direction side) {
        return PluggableFacade.boundingBoxFor(side);
    }

    @Override
    public Component getName(ItemStack stack) {
        FacadeInstance fullState = getStates(stack);
        if (fullState.type == FacadeType.Basic) {
            String displayName = getFacadeStateDisplayName(fullState.phasedStates[0]);
            // Empty when the facade state hasn't resolved to a real block — a bare/default facade
            // stack, or one named before FacadeStateManager.init() has populated defaultState on a
            // multiplayer client (the guide/JEI indexing every item on login). Drop the ": <block>"
            // suffix rather than appending ": Air" or dereferencing a null stateInfo.
            if (displayName.isEmpty()) {
                return super.getName(stack);
            }
            return super.getName(stack).copy().append(": " + displayName);
        } else {
            return Component.translatable("item.buildcraftunofficial.plug_facade_phased");
        }
    }

    /** Display name of the block this facade mimics, or {@code ""} when the facade state hasn't
     *  resolved to a real block: a null {@link FacadePhasedState#stateInfo} (e.g. a bare facade
     *  named before {@link FacadeStateManager#init()} runs on a multiplayer client, where
     *  {@code defaultState} is still null) or the empty/AIR default state. Callers treat an empty
     *  result as "no block suffix" instead of dereferencing null. */
    public static String getFacadeStateDisplayName(FacadePhasedState state) {
        if (state == null || state.stateInfo == null) {
            return "";
        }
        ItemStack assumedStack = state.stateInfo.requiredStack;
        if (assumedStack == null || assumedStack.isEmpty()) {
            return "";
        }
        return assumedStack.getHoverName().getString();
    }

    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;*/
    //?}
        FacadeInstance states = getStates(stack);
        if (states.type == FacadeType.Phased) {
            FacadePhasedState defaultState = null;
            for (FacadePhasedState state : states.phasedStates) {
                if (state.activeColour == null) {
                    defaultState = state;
                    continue;
                }
                tooltip.accept(Component.translatable("item.buildcraftunofficial.plug_facade_phased.state",
                    Component.translatable("color.minecraft." + state.activeColour.getName()),
                    getFacadeStateDisplayName(state)));
            }
            if (defaultState != null) {
                tooltip.accept(Component.translatable("item.buildcraftunofficial.plug_facade_phased.state_default",
                    getFacadeStateDisplayName(defaultState)));
            }
        } else {
            if (flag.isAdvanced()) {
                tooltip.accept(Component.literal(
                    BuiltInRegistries.BLOCK.getKey(states.phasedStates[0].stateInfo.state.getBlock()).toString()));
            }
            // Show varying blockstate properties so duplicates can be distinguished
            FacadeBlockStateInfo info = states.phasedStates[0].stateInfo;
            BlockState state = info.state;
            for (Property<?> prop : info.varyingProperties) {
                String name = prop.getName();
                String value = getPropertyValueName(state, prop);
                tooltip.accept(Component.literal(name + " = " + value)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueName(BlockState state, Property<T> prop) {
        return prop.getName(state.getValue(prop));
    }

    // IFacadeItem

    @Override
    public ItemStack createFacadeStack(IFacade facade) {
        return createItemStack((FacadeInstance) facade);
    }

    @Override
    public IFacade getFacade(ItemStack facade) {
        return getStates(facade);
    }
}
