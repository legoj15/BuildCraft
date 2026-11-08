/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.world.entity.EntityEquipment;
//?}
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import buildcraft.platform.fabric.FabricGameHooks;

/^* One of NeoForge's {@code captureDrops} sites ({@code LivingEntityMixin}): the equipment a dying entity drops. *^/
//? if >=1.21.10 {
@Mixin(EntityEquipment.class)
//?} else {
/^// 1.21.1 has no EntityEquipment (the armour lists live on the entity, dropped through LivingEntity.drop like the
// rest): an empty mixin on the entity keeps this class's config entry valid.
@Mixin(LivingEntity.class)
^///?}
abstract class EntityEquipmentMixin {

    // From 26.3 dropAll spawns the items itself; before, it goes through LivingEntity.drop(), whose spawn
    // LivingEntityMixin captures, so this is empty there.
    //? if >=26.3 {
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(Level level, Entity drop, Operation<Boolean> original,
                                           @Local(argsOnly = true) LivingEntity dropper) {
        return FabricGameHooks.captureDrop(dropper, (ItemEntity) drop) || original.call(level, drop);
    }
    //?}
}
*///?}
