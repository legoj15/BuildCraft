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

import net.minecraft.world.entity.item.ItemEntity;

import buildcraft.platform.fabric.FabricFluidPhysics;

/^*
 * NeoForge's item movement in a fluid type ({@code FluidType.setItemMovement}) on Fabric: {@code ItemEntity.tick} gives
 * a fluid that is not vanilla's, deeper than a tenth of a block, the movement of an item in water whether or not the
 * fluid is water-like. Vanilla gives it only to an item {@code isInWater()}, which a fluid that is not water-like
 * (crude oil, residue) never is ({@link FabricFluidPhysics}), so there the item fell to the floor of the lake.
 *^/
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {

    /^* In a BuildCraft fluid counts as in water for the item's float; vanilla's depth test (more than a tenth of a block
     *  of the water tag, which every BuildCraft fluid is in) follows. *^/
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/ItemEntity;isInWater()Z"))
    private boolean buildcraft$floatsInBuildCraftFluid(ItemEntity item, Operation<Boolean> original) {
        return original.call(item)
                || FabricFluidPhysics.interaction(item).buildcraft$inBuildCraftFluid();
    }
}
*///?}
