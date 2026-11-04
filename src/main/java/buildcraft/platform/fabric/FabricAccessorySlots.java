/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.AccessorySlotServices;

/^*
 * {@link AccessorySlotServices} on Fabric. Gap: neither accessory mod BC supports has a Fabric 26.3 build (Curios is
 * NeoForge-only, Accessories stops at 1.21.10), so only the vanilla head slot counts.
 *^/
public final class FabricAccessorySlots implements AccessorySlotServices {
    @Override
    public boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return false;
    }
}
*///?}
