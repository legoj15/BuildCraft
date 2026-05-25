/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.lib.misc.HashUtil;

import buildcraft.builders.snapshot.Snapshot.Header;

/**
 * Snapshot item — represents either a Blueprint or a Template, in clean or used state.
 * <p>
 * In 1.12.2 these were 4 metadata sub-types of a single item {@code buildcraftbuilders:snapshot}.
 * In 1.21.11 they are registered as 4 separate items.
 */
@SuppressWarnings("deprecation")
public class ItemSnapshot extends Item {
    private final EnumSnapshotType snapshotType;
    private final boolean used;

    public ItemSnapshot(Item.Properties properties, EnumSnapshotType snapshotType, boolean used) {
        super(properties);
        this.snapshotType = snapshotType;
        this.used = used;
    }

    public EnumSnapshotType getSnapshotType() {
        return snapshotType;
    }

    public boolean isUsed() {
        return used;
    }

    /**
     * Create a "used" ItemStack with the given snapshot header stored in NBT.
     */
    public ItemStack createUsedStack(Header header) {
        ItemStack stack = new ItemStack(this);
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.put("header", header.serializeNBT());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    /**
     * Read the snapshot header from the stack's custom data, or null if not present.
     */
    public static Header getHeader(ItemStack stack) {
        if (stack.getItem() instanceof ItemSnapshot snapshotItem && snapshotItem.used) {
            var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt.contains("header")) {
                    return new Header(nbt.getCompoundOrEmpty("header"));
                }
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        Header header = getHeader(stack);
        // Gray on every line except the item name. 1.12.2's vanilla tooltip pipeline auto-applied
        // ChatFormatting.GRAY to anything added via addInformation; modern MC dropped that default
        // and now renders unstyled Components in the item-name color, which made the hash / date /
        // owner lines fight the "Blueprint" title for attention. We restore the old look by
        // styling each line explicitly.
        if (header == null) {
            tooltip.accept(Component.translatable("item.blueprint.blank").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.literal(header.name).withStyle(ChatFormatting.GRAY));
            if (flag.isAdvanced()) {
                tooltip.accept(Component.literal("Hash: " + HashUtil.convertHashToString(header.key.hash))
                        .withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.literal("Date: " + header.created).withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.literal("Owner UUID: " + header.owner).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // The 3D preview is drawn as a SECOND tooltip panel below this item's main tooltip — see
    // buildcraft.builders.client.tooltip.BlueprintTooltipOverlay. We deliberately do NOT
    // implement getTooltipImage here: Minecraft's tooltip-image slot forces a ClientTooltipComponent
    // of fixed width inside the main tooltip, which produces ugly dead space when the hash/UUID
    // lines stretch the tooltip wider than the 100-pixel preview. The separate-panel approach
    // mirrors the original 1.12.2 behavior (BCBuildersEventDist#onPostText) and keeps the main
    // tooltip sized purely by its text.
}

