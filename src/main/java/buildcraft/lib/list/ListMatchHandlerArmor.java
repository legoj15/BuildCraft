/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.10 {
import net.minecraft.world.item.equipment.Equippable;
//?}

/** Modern replacement for the 1.12.2 ItemArmor-based handler. Uses the {@link DataComponents#EQUIPPABLE}
 * data component, which is how vanilla and mods now expose "this is wearable in slot X". */
public class ListMatchHandlerArmor extends buildcraft.api.lists.ListMatchHandler {

    @Nullable
    private static EquipmentSlot slotOf(@Nonnull ItemStack stack) {
        //? if >=1.21.10 {
        Equippable e = stack.get(DataComponents.EQUIPPABLE);
        return e == null ? null : e.slot();
        //?} else {
        /*// 1.21.1 has no EQUIPPABLE component; the Equipable interface gives the slot (null if not wearable).
        net.minecraft.world.item.Equipable e = net.minecraft.world.item.Equipable.get(stack);
        return e == null ? null : e.getEquipmentSlot();*/
        //?}
    }

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        if (stack.isEmpty() || type != Type.TYPE) return false;
        return slotOf(stack) != null;
    }

    @Override
    public boolean matches(Type type, @Nonnull ItemStack source, @Nonnull ItemStack target, boolean precise) {
        if (type != Type.TYPE) return false;
        EquipmentSlot src = slotOf(source);
        EquipmentSlot tgt = slotOf(target);
        return src != null && src == tgt;
    }

    @Nonnull
    @Override
    public java.util.List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        if (type != Type.TYPE) return java.util.List.of();
        EquipmentSlot slot = slotOf(stack);
        if (slot == null) return java.util.List.of();
        return java.util.List.of("equipment slot: " + slot.getName());
    }

    @Nullable
    @Override
    public NonNullList<ItemStack> getClientExamples(Type type, @Nonnull ItemStack stack) {
        if (type != Type.TYPE) return null;
        EquipmentSlot wanted = slotOf(stack);
        if (wanted == null) return null;
        NonNullList<ItemStack> out = NonNullList.create();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack def = item.getDefaultInstance();
            if (def.isEmpty()) continue;
            EquipmentSlot s = slotOf(def);
            if (s == wanted) {
                out.add(def);
            }
        }
        return out;
    }
}
