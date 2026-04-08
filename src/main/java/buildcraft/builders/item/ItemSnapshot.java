/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.item;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.lib.misc.HashUtil;

import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Snapshot.Header;

/**
 * Snapshot item — represents either a Blueprint or a Template, in clean or used state.
 * <p>
 * In 1.12.2 these were 4 metadata sub-types of a single item {@code buildcraftbuilders:snapshot}.
 * In 1.21.11 they are registered as 4 separate items.
 */
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
        if (header == null) {
            tooltip.accept(Component.translatable("item.blueprint.blank"));
        } else {
            tooltip.accept(Component.literal(header.name));
            if (flag.isAdvanced()) {
                tooltip.accept(Component.literal("Hash: " + HashUtil.convertHashToString(header.key.hash)));
                tooltip.accept(Component.literal("Date: " + header.created));
                tooltip.accept(Component.literal("Owner UUID: " + header.owner));
            }
        }
    }
}

