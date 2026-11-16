/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.lang.reflect.Field;
import java.util.function.Function;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import buildcraft.platform.VanillaExtensions;

/^*
 * {@link VanillaExtensions} on MinecraftForge, which patches most of these in under its own signatures: the position
 * aware sound type, dig speed, explosion resistance and infinite-source rule are the same calls NeoForge makes, and the
 * entity's persistent tag is Forge's own {@code getPersistentData}.
 *
 * <ul>
 * <li><b>Experience.</b> Forge moved every vanilla block's experience out of the after-break step into
 * {@code getExpDrop(state, level, random, pos, fortune, silkTouch)}, which it asks the same way, with the two enchantment
 * levels read off the tool. The block entity and the breaker NeoForge's call takes have no Forge counterpart and are not
 * used; so no mixin is needed here, unlike Fabric.</li>
 * <li><b>Custom data.</b> Forge adds no key lookup to {@code CustomData} and its tag is private, so the lookup reads
 * that field through FML's reflection helper to avoid copying the tag (the caller runs on inventory ticks). A direct read
 * needs a line in the shared access transformer ({@code public net.minecraft.world.item.component.CustomData tag}),
 * which NeoForge's nodes would then apply too; left reflective until that line is wanted.</li>
 * <li><b>Level-aware saved data.</b> Forge does not patch the saved-data type; the level reaches the factory and codec
 * through {@link ForgeSavedDataLevels}, as on Fabric, which nothing feeds yet (see the marker at that method).</li>
 * </ul>
 *^/
public final class ForgeVanillaExtensions implements VanillaExtensions {
    @Override
    public SoundType soundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return state.getSoundType(level, pos, entity);
    }

    @Override
    public float destroySpeed(Player player, BlockState state, BlockPos pos) {
        return player.getDestroySpeed(state, pos);
    }

    @Override
    public float explosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getExplosionResistance(level, pos, explosion);
    }

    @Override
    public int expDrop(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity breaker,
            ItemStack tool) {
        // The same two levels Forge's own break path reads before it asks the block (ForgeHooks.dropXpForBlock).
        HolderLookup<Enchantment> enchantments = level.holderLookup(Registries.ENCHANTMENT);
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.FORTUNE), tool);
        int silkTouch = EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH), tool);
        return state.getExpDrop(level, level.getRandom(), pos, fortune, silkTouch);
    }

    @Override
    public boolean customDataContains(CustomData data, String key) {
        try {
            return ((CompoundTag) CustomDataTag.TAG.get(data)).contains(key);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read the tag of " + data, e);
        }
    }

    /^* Holds the field lookup so that only the first {@link #customDataContains} call pays for it (and can fail). ^/
    private static final class CustomDataTag {
        static final Field TAG = ObfuscationReflectionHelper.findField(CustomData.class, "tag");
    }

    @Override
    public boolean canConvertToSource(FluidState fluid, ServerLevel level, BlockPos pos) {
        return fluid.canConvertToSource(level, pos);
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return entity.getPersistentData();
    }

    // TODO(#209 F3): needs a Forge mixin for the storage's level (the saved-data storage and the chunk source tell
    // ForgeSavedDataLevels which level a storage call is for), see platform/fabric/mixin/SavedDataStorageMixin and
    // ServerChunkCacheMixin. Until then every factory and codec below is handed null.
    @Override
    public <T extends SavedData> SavedDataType<T> levelSavedDataType(Identifier id, Function<ServerLevel, T> factory,
            Function<ServerLevel, Codec<T>> codec, DataFixTypes fixType) {
        return new SavedDataType<>(id, () -> factory.apply(ForgeSavedDataLevels.current()), new Codec<T>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                return codec.apply(ForgeSavedDataLevels.current()).decode(ops, input);
            }

            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                return codec.apply(ForgeSavedDataLevels.current()).encode(input, ops, prefix);
            }
        }, fixType);
    }
}
*///?}
