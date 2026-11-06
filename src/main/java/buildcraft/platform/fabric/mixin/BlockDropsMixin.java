/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import buildcraft.platform.fabric.FabricProtection;

/^*
 * NeoForge's {@code restoringBlockSnapshots} drop guard on Fabric: while {@link FabricProtection#useItemOn} puts a
 * refused placement back, a block that breaks for it (one half of a two-cell block whose other half is put back to
 * what stood there) pops no item and no experience, at the two points NeoForge's patched {@code Block} checks the
 * flag.
 *^/
@Mixin(Block.class)
abstract class BlockDropsMixin {

    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void buildcraft$noDropWhileRestoring(Level level, Supplier<ItemEntity> entityFactory, ItemStack stack,
                                                        CallbackInfo ci) {
        if (FabricProtection.isRestoring(level)) {
            ci.cancel();
        }
    }

    @Inject(method = "popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"), cancellable = true)
    private void buildcraft$noExperienceWhileRestoring(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (FabricProtection.isRestoring(level)) {
            ci.cancel();
        }
    }
}
*///?}
