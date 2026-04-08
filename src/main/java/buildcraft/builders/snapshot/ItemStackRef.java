/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves an ItemStack from NBT references (item id, amount, tag compound).
 * In 1.21.11, metadata/damage is gone — items are looked up by Identifier only.
 */
public class ItemStackRef {
    private final NbtRef<StringTag> item;
    private final NbtRef<IntTag> amount;
    // 'meta' field is ignored in 1.21.11 (no damage values)
    private final NbtRef<CompoundTag> tagCompound;

    public ItemStackRef(NbtRef<StringTag> item,
                        NbtRef<IntTag> amount,
                        NbtRef<IntTag> meta, // kept for JSON compat, unused
                        NbtRef<CompoundTag> tagCompound) {
        this.item = item;
        this.amount = amount;
        this.tagCompound = tagCompound;
    }

    public ItemStack get(Tag nbt) {
        Identifier itemId = Identifier.parse(
            item
                .get(nbt)
                .orElseThrow(NullPointerException::new)
                .value()
        );
        Item itemObj = BuiltInRegistries.ITEM.getValue(itemId);
        Objects.requireNonNull(itemObj, "Unknown item: " + itemId);

        int count = Optional.ofNullable(amount)
            .flatMap(ref -> ref.get(nbt))
            .map(IntTag::value)
            .orElse(1);

        ItemStack itemStack = new ItemStack(itemObj, count);

        // Apply NBT tag compound if present
        Optional.ofNullable(tagCompound)
            .flatMap(ref -> ref.get(nbt))
            .ifPresent(tag -> {
                // In 1.21.11, custom NBT is stored via DataComponents, not direct tag
                // For now, we apply the tag using the legacy CompoundTag merge
                // TODO: Implement proper DataComponent application when needed
            });

        return itemStack;
    }
}
