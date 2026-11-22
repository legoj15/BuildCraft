/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21.10 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
//?} else {
/^import java.util.Collection;

import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
^///?}

import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import buildcraft.platform.forge.ForgeEnchantments;

/^*
 * NeoForge's {@code supportsEnchantment} for {@code /enchant}, for an {@code EnchantmentFilterItem}: the command asks the
 * held item instead of the enchantment's supported-items tag, as NeoForge's patch of the same line does
 * ({@link ForgeEnchantments}); the sibling of {@code AnvilMenuMixin}.
 *
 * <p><b>Injection point.</b> The call to {@code Enchantment.canEnchant(ItemStack)} in the private {@code enchant}, the
 * method's only call to it, with the {@code Holder<Enchantment>} parameter the command was given (captured as an argument
 * of the method, so no ordinal and no local table is involved). Forge does not patch this class.
 *
 * <p><b>1.21.1.</b> Forge 52 has no MixinExtras: the call is redirected (plain Mixin) and the handler takes the target
 * method's own arguments after the call's, which a redirect may capture, the holder among them.
 *^/
@Mixin(EnchantCommand.class)
abstract class EnchantCommandMixin {
    //? if >=1.21.10 {
    @WrapOperation(method = "enchant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean buildcraft$supportsEnchantment(Enchantment enchantment, ItemStack stack,
                                                          Operation<Boolean> original,
                                                          @Local(argsOnly = true) Holder<Enchantment> holder) {
        return ForgeEnchantments.canEnchant(stack, holder, () -> original.call(enchantment, stack));
    }
    //?} else {
    /^@Redirect(method = "enchant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/Enchantment;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"))
    private static boolean buildcraft$supportsEnchantment(Enchantment enchantment, ItemStack stack,
                                                          CommandSourceStack source, Collection<? extends Entity> targets,
                                                          Holder<Enchantment> holder, int level) {
        return ForgeEnchantments.canEnchant(stack, holder, () -> enchantment.canEnchant(stack));
    }
    ^///?}
}
*///?}
