/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;

import buildcraft.platform.fabric.FabricGameHooks;

/^*
 * NeoForge's {@code ExplosionEvent.Detonate} on Fabric ({@link FabricGameHooks#onExplosionDetonate}), at the same
 * point: once the blast has its block list and the entities in its reach, before it hurts any of them. The listeners
 * edit the very lists the blast goes on to use.
 *^/
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private List<BlockPos> buildcraft$toBlow = List.of();

    @ModifyExpressionValue(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
    private List<BlockPos> buildcraft$keepToBlow(List<BlockPos> toBlow) {
        buildcraft$toBlow = toBlow;
        return toBlow;
    }

    @ModifyExpressionValue(method = "hurtEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> buildcraft$detonate(List<Entity> entities) {
        List<Entity> affected = entities instanceof ArrayList<Entity> ? entities : new ArrayList<>(entities);
        FabricGameHooks.fireExplosionDetonate(level, (Explosion) (Object) this, buildcraft$toBlow, affected);
        return affected;
    }
}
*///?}
