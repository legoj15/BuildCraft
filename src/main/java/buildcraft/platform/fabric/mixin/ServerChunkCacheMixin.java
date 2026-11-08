/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.level.ServerChunkCache;
//? if >=26.1 {
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.SavedDataStorage;

import buildcraft.platform.fabric.FabricSavedDataLevels;
//?}

/^* Tells a dimension's saved-data storage which level it belongs to ({@code SavedDataStorageMixin}). Nothing before
 *  26.1, where vanilla's {@code SavedData.Context} carries the level. ^/
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    //? if >=26.1 {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private SavedDataStorage savedDataStorage;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void buildcraft$tellStorageItsLevel(CallbackInfo ci) {
        ((FabricSavedDataLevels.LevelAwareStorage) savedDataStorage).buildcraft$setLevel(level);
    }
    //?}
}
*///?}
