/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import buildcraft.platform.fabric.FabricSavedDataLevels;

/^*
 * NeoForge's level-aware saved data on Fabric: the storage remembers its dimension ({@code ServerChunkCacheMixin}),
 * and the calls that build, decode or encode data run with that level as {@link FabricSavedDataLevels#current}.
 *^/
@Mixin(SavedDataStorage.class)
abstract class SavedDataStorageMixin implements FabricSavedDataLevels.LevelAwareStorage {
    @Unique
    private ServerLevel buildcraft$level;

    @Override
    public void buildcraft$setLevel(ServerLevel level) {
        this.buildcraft$level = level;
    }

    @Override
    public ServerLevel buildcraft$level() {
        return buildcraft$level;
    }

    @WrapMethod(method = "computeIfAbsent")
    private SavedData buildcraft$computeIfAbsent(SavedDataType<?> type, Operation<SavedData> original) {
        return FabricSavedDataLevels.with(buildcraft$level, () -> original.call(type));
    }

    @WrapMethod(method = "get")
    private SavedData buildcraft$get(SavedDataType<?> type, Operation<SavedData> original) {
        return FabricSavedDataLevels.with(buildcraft$level, () -> original.call(type));
    }

    @WrapMethod(method = "collectDirtyTagsToSave")
    private Map<SavedDataType<?>, CompoundTag> buildcraft$collectDirtyTagsToSave(
            Operation<Map<SavedDataType<?>, CompoundTag>> original) {
        return FabricSavedDataLevels.with(buildcraft$level, original::call);
    }
}
*///?}
