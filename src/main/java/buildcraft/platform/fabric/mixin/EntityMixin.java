/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.fabric.FabricGameHooks;
import buildcraft.platform.hook.BlockParticleEffects;

/^*
 * NeoForge's {@code addRunningEffects} on Fabric: a {@link BlockParticleEffects} block draws its own sprint particles.
 * Also NeoForge's {@code captureDrops} ({@link FabricGameHooks.DropCapture}): while the entity's death collects its
 * drops, what {@code spawnAtLocation} drops is collected instead of put in the level.
 *^/
@Mixin(Entity.class)
abstract class EntityMixin implements FabricGameHooks.DropCapture {

    @Unique
    @Nullable
    private List<ItemEntity> buildcraft$capturedDrops;

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void buildcraft$runningEffects(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        // The block vanilla reads next, at the same position.
        BlockPos pos = self.getOnPosLegacy();
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof BlockParticleEffects block && block.spawnRunningEffects(self.level(), pos, self)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean buildcraft$captureDrop(ServerLevel level, Entity drop, Operation<Boolean> original) {
        return FabricGameHooks.captureDrop((Entity) (Object) this, (ItemEntity) drop) || original.call(level, drop);
    }

    @Override
    @Nullable
    public List<ItemEntity> buildcraft$capturedDrops() {
        return buildcraft$capturedDrops;
    }

    @Override
    public void buildcraft$setCapturedDrops(@Nullable List<ItemEntity> drops) {
        buildcraft$capturedDrops = drops;
    }
}
*///?}
