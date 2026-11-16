/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import buildcraft.platform.forge.ForgeSavedDataLevels;

/^*
 * NeoForge's level-aware saved data on Forge: the storage remembers its dimension ({@code ServerChunkCacheMixin}), and
 * the calls that build, decode or encode data run with that level as {@code ForgeSavedDataLevels.current}, which is what
 * {@code ForgeVanillaExtensions#levelSavedDataType}'s factory and codec read. The server-wide storage has no level, so
 * its data sees null, as on NeoForge.
 *
 * <p><b>Why not Forge's own {@code LevelCapabilityData} pattern.</b> That class builds a fresh {@code SavedDataType} per
 * level ({@code type(level)}, closing over the level), with no patch at all. A {@code SavedDataType} is a record keyed by
 * its factory and codec instances in the storage's cache, so the pattern works only for one type per level, asked for
 * with the level in hand, and BuildCraft's types are static constants shared by every storage (the call sites would all
 * change to a per-level lookup, and the encode at save time would still need the level of the type's own storage). This
 * mixin is the narrower change: the same one Fabric makes, with the same three entry points.
 *
 * <p><b>Injection points.</b> {@code @WrapMethod} (MixinExtras 0.5.4, which Forge 26.3 bundles) on the three entry points
 * that run a type's factory or codec: {@code computeIfAbsent} and {@code get} (the factory, and the codec's decode through
 * {@code readSavedData}) and {@code collectDirtyTagsToSave} (the codec's encode). A wrap names the method and nothing in
 * its body, so it holds against Forge's patch of this class, whose only change is a null data-fix type in
 * {@code readTagFromDisk} (a method outside all three). Nesting is harmless: the inner call restores the outer level.
 *^/
@Mixin(SavedDataStorage.class)
abstract class SavedDataStorageMixin implements ForgeSavedDataLevels.LevelAwareStorage {
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
        return ForgeSavedDataLevels.with(buildcraft$level, () -> original.call(type));
    }

    @WrapMethod(method = "get")
    private SavedData buildcraft$get(SavedDataType<?> type, Operation<SavedData> original) {
        return ForgeSavedDataLevels.with(buildcraft$level, () -> original.call(type));
    }

    @WrapMethod(method = "collectDirtyTagsToSave")
    private Map<SavedDataType<?>, CompoundTag> buildcraft$collectDirtyTagsToSave(
            Operation<Map<SavedDataType<?>, CompoundTag>> original) {
        return ForgeSavedDataLevels.with(buildcraft$level, original::call);
    }
}
*///?}
