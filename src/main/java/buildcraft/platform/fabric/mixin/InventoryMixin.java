/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import buildcraft.platform.fabric.FabricGameHooks;

/^* One of NeoForge's {@code captureDrops} sites ({@code LivingEntityMixin}): the inventory a dying player drops. *^/
@Mixin(Inventory.class)
abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;

    // From 26.3 dropAll spawns the items itself; before, it goes through LivingEntity.drop(), whose spawn
    // LivingEntityMixin captures, so this is empty there.
    //? if >=26.3 {
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(Level level, Entity drop, Operation<Boolean> original) {
        return FabricGameHooks.captureDrop(player, (ItemEntity) drop) || original.call(level, drop);
    }
    //?}
}
*///?}
