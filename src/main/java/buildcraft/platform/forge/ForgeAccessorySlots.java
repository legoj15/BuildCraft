/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.AccessorySlotServices;

/^*
 * {@link AccessorySlotServices} on MinecraftForge. Gap: Curios has no Forge build on the lines BuildCraft targets and
 * Accessories stops at 1.21.10, so no accessory slot exists to look in and only the vanilla head slot counts, as on
 * Fabric.
 *^/
public final class ForgeAccessorySlots implements AccessorySlotServices {
    @Override
    public boolean isEquipped(LivingEntity entity, Predicate<ItemStack> filter) {
        return false;
    }
}
*///?}
