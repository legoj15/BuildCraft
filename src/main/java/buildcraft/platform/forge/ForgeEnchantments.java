/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.BooleanSupplier;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import buildcraft.platform.hook.EnchantmentFilterItem;

/^*
 * NeoForge's {@code IItemExtension#supportsEnchantment} for an {@link EnchantmentFilterItem} on MinecraftForge, which
 * has no such hook. NeoForge consults it wherever vanilla asks "may this item take this enchantment" outside the
 * enchanting table: the anvil, {@code /enchant} and the random-enchant loot function. Forge's two enchantment extensions
 * cannot stand in: {@code IForgeItem#canApplyAtEnchantingTable} is the table's alone (and BuildCraft's machines must stay
 * out of the table), and {@code isBookEnchantable} can only veto an anvil result, never admit an enchantment the item's
 * supported-items tag leaves out. So {@code AnvilMenuMixin} and {@code EnchantCommandMixin} (in
 * {@code platform.forge.mixin}) replace the one vanilla test, {@code Enchantment#canEnchant(ItemStack)}, at the call
 * sites that have the enchantment's holder in hand, and route it here.
 *
 * <p>The answer is the item's own for an {@link EnchantmentFilterItem}, vanilla's for every other item. The enchanting
 * table asks neither of these call sites, so what it offers is unchanged: the machine items stay out of it.
 *
 * <p>Not routed: the {@code enchant_randomly} loot function's {@code only_compatible} test (NeoForge's third site), which
 * tests inside a lambda of the function; a machine item is never the target of that function in BuildCraft's data.
 *^/
public final class ForgeEnchantments {
    private ForgeEnchantments() {}

    /^* Whether {@code stack} may take {@code enchantment}: the item's own answer if it filters, else {@code vanilla}. ^/
    public static boolean canEnchant(ItemStack stack, Holder<Enchantment> enchantment, BooleanSupplier vanilla) {
        return stack.getItem() instanceof EnchantmentFilterItem item
                ? item.acceptsEnchantment(stack, enchantment)
                : vanilla.getAsBoolean();
    }
}
*///?}
