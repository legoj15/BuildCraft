/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

import buildcraft.platform.fabric.FabricGameHooks;

/^*
 * NeoForge's {@code FinalizeSpawnEvent} on Fabric ({@link FabricGameHooks#onFinalizeSpawn}). NeoForge posts it at every
 * caller of {@code finalizeSpawn}; here it runs when the finalize reaches {@code Mob}'s own, which every vanilla
 * override calls.
 *^/
@Mixin(Mob.class)
abstract class MobMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void buildcraft$finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                          EntitySpawnReason reason, @Nullable SpawnGroupData groupData,
                                          CallbackInfoReturnable<SpawnGroupData> cir) {
        FabricGameHooks.fireFinalizeSpawn((Mob) (Object) this, reason == EntitySpawnReason.CONVERSION);
    }
}
*///?}
