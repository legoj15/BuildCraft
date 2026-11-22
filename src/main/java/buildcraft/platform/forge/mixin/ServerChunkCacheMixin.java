/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Final;
//? if >=26.1 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.SavedDataStorage;

import buildcraft.platform.forge.ForgeSavedDataLevels;

/^*
 * Tells a dimension's saved-data storage which level it belongs to ({@code SavedDataStorageMixin}), so NeoForge's
 * level-aware saved data works on Forge. The chunk source is where vanilla builds the dimension's storage, from its own
 * level.
 *
 * <p><b>Injection point.</b> The tail of the constructor, once both fields are set (a constructor tail is the one place
 * the two exist together, and no constructor local is read). Forge's patch of this class does not touch the
 * constructor. The storage is told after the constructor's own {@code computeIfAbsent(TicketStorage.TYPE)}, which is a
 * vanilla type that reads no level.
 *^/
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    private SavedDataStorage savedDataStorage;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void buildcraft$tellStorageItsLevel(CallbackInfo ci) {
        ((ForgeSavedDataLevels.LevelAwareStorage) savedDataStorage).buildcraft$setLevel(level);
    }
}
//?} else {
/^import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.server.level.ServerChunkCache;

// Level-aware saved data is a 26.1+ seam (see SavedDataStorageMixin): nothing to tell the storage before 26.1.
// Empty, so the mixin config names the same classes on every Forge line.
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
}
^///?}
*///?}
