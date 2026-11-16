/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import buildcraft.platform.forge.ForgeEnchantments;

/^*
 * NeoForge's {@code supportsEnchantment} at the anvil, for an {@code EnchantmentFilterItem} (the Quarry and the Mining
 * Well items): the anvil's per-enchantment compatibility test asks the item instead of the enchantment's supported-items
 * tag, as NeoForge's patch of the same line does ({@link ForgeEnchantments}).
 *
 * <p><b>Injection point.</b> The call to {@code Enchantment.canEnchant(ItemStack)} inside {@code createResult}'s loop over
 * the added enchantments (wrapped, so another mod's wrap composes; the holder of the enchantment being tested is the one
 * {@code Holder<Enchantment>} local in scope there, captured by type). Forge's patch of {@code createResult} adds an
 * event call before the loop and a book test after it, and moves the {@code usingBook} flag out of the loop's
 * {@code if}; neither touches the call, and the call is the only {@code canEnchant} in the method.
 *^/
@Mixin(AnvilMenu.class)
abstract class AnvilMenuMixin {
    @WrapOperation(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean buildcraft$supportsEnchantment(Enchantment enchantment, ItemStack stack, Operation<Boolean> original,
                                                   @Local Holder<Enchantment> holder) {
        return ForgeEnchantments.canEnchant(stack, holder, () -> original.call(enchantment, stack));
    }
}
*///?}
