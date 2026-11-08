/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import buildcraft.platform.VanillaExtensions;

/** {@link VanillaExtensions} on NeoForge: each call is the patched twin itself. */
public final class NeoForgeVanillaExtensions implements VanillaExtensions {
    @Override
    public SoundType soundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return state.getSoundType(level, pos, entity);
    }

    @Override
    public float destroySpeed(Player player, BlockState state, BlockPos pos) {
        //? if >=1.21.10 {
        return player.getDestroySpeed(state, pos);
        //?} else {
        /*return player.getDigSpeed(state, pos);*/
        //?}
    }

    @Override
    public float explosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getExplosionResistance(level, pos, explosion);
    }

    @Override
    public int expDrop(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity breaker,
            ItemStack tool) {
        return state.getExpDrop(level, pos, blockEntity, breaker, tool);
    }

    @Override
    public boolean customDataContains(CustomData data, String key) {
        return data.contains(key);
    }

    @Override
    public boolean canConvertToSource(FluidState fluid, ServerLevel level, BlockPos pos) {
        return fluid.canConvertToSource(level, pos);
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return entity.getPersistentData();
    }

    //? if >=1.21.10 && <26.3 {
    /*@Override
    public int burnTime(ItemStack stack, net.minecraft.world.level.Level level) {
        return stack.getBurnTime(null, level.fuelValues());
    }
    *///?}
    //? if <1.21.10 {
    /*@Override
    public int burnTime(ItemStack stack, net.minecraft.world.level.Level level) {
        // 1.21.1 predates the FuelValues lookup (MC 1.21.2); getBurnTime is single-arg.
        return stack.getBurnTime(null);
    }
    *///?}

    //? if >=1.21.10 && <26.1 {
    /*@Override
    public ItemStack craftingRemainder(ItemStack stack) {
        return stack.getCraftingRemainder();
    }
    *///?}

    //? if >=26.1 {
    @Override
    public <T extends net.minecraft.world.level.saveddata.SavedData> net.minecraft.world.level.saveddata.SavedDataType<T>
            levelSavedDataType(net.minecraft.resources.Identifier id,
                    java.util.function.Function<ServerLevel, T> factory,
                    java.util.function.Function<ServerLevel, com.mojang.serialization.Codec<T>> codec,
                    net.minecraft.util.datafix.DataFixTypes fixType) {
        return new net.minecraft.world.level.saveddata.SavedDataType<>(id, factory::apply, codec::apply, fixType);
    }
    //?}
}
//?}
