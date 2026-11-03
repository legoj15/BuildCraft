/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.compat;

import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

/**
 * Soft support for the mods that add extra equipment slots, so an item BC treats as worn (the Robot Goggles) also
 * counts when it sits in one of them. Which slot accepts which item is data, not code: the item tags under
 * {@code data/curios/} and {@code data/accessories/}, plus the {@code curios/entities} file that gives players the
 * Curios head slot (Curios binds none of its preset slots by default).
 *
 * <p>Curios has a build on every MC line BC supports; Accessories only on 1.21.1 and 1.21.10, so its lookup exists
 * only on those nodes. Each mod's API is named only inside its own nested class, which the JVM loads on first
 * call, so neither is needed at run time unless that mod is installed.
 */
public final class AccessorySlots {
    private AccessorySlots() {}

    private static final boolean CURIOS = ModList.get().isLoaded("curios");
    //? if <1.21.11 {
    /*private static final boolean ACCESSORIES = ModList.get().isLoaded("accessories");*/
    //?}

    /** Whether {@code entity} has a stack matching {@code filter} in any slot an installed accessory mod adds. */
    public static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        if (CURIOS && CuriosLookup.isEquipped(entity, filter)) {
            return true;
        }
        //? if <1.21.11 {
        /*if (ACCESSORIES && AccessoriesLookup.isEquipped(entity, filter)) {
            return true;
        }*/
        //?}
        return false;
    }

    private static final class CuriosLookup {
        static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .map(handler -> handler.isEquipped(filter))
                    .orElse(false);
        }
    }

    //? if <1.21.11 {
    /*private static final class AccessoriesLookup {
        static boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
            return io.wispforest.accessories.api.AccessoriesCapability.getOptionally(entity)
                    .map(capability -> capability.getFirstEquipped(filter) != null)
                    .orElse(false);
        }
    }*/
    //?}
}
