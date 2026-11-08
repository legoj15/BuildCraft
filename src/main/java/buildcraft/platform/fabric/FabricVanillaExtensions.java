/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Function;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import buildcraft.platform.VanillaExtensions;
import buildcraft.platform.hook.PositionalSoundBlock;
import buildcraft.platform.fabric.mixin.FlowingFluidAccessor;

/^*
 * {@link VanillaExtensions} on Fabric, which patches none of these: each answers what vanilla does, plus BuildCraft's
 * own hook interfaces. The sound asks a {@link PositionalSoundBlock}, the resistance an {@code ExplosionArmouredBlock}
 * (other mods' blocks answer without the position); the dig speed tells BuildCraft's break-speed hook
 * the position ({@code FabricGameHooks.digSpeedAt}); the experience is what the block's own {@code spawnAfterBreak} would pop, caught before it spawns
 * ({@code BlockExperienceMixin}); the persistent tag is a BuildCraft data attachment; the saved-data level is the one
 * the storage was made for ({@code SavedDataStorageMixin}).
 *^/
public final class FabricVanillaExtensions implements VanillaExtensions {
    /^*
     * The positions of a player's two-block step sound, which vanilla's {@code playCombinationStepSounds} and
     * {@code playMuffledStepSound} are not given: {@code PlayerMixin} sets them around those calls in
     * {@code Player.playStepSound} (where NeoForge passes them) and {@code EntityMixin} reads them there.
     *^/
    public interface StepSoundPositions {
        void buildcraft$setStepSoundPositions(@Nullable BlockPos primary, @Nullable BlockPos muffled);
    }

    /^* The entity's persistent compound: BuildCraft's own attachment, as Fabric has no shared one. ^/
    private static final AttachmentType<CompoundTag> PERSISTENT_DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "persistent_data"),
            builder -> builder.initializer(CompoundTag::new).persistent(CompoundTag.CODEC));

    /^* The experience a {@link #expDrop} in progress on this thread has caught, or null outside one. ^/
    private static final ThreadLocal<int[]> CAUGHT_EXPERIENCE = new ThreadLocal<>();

    @Override
    public SoundType soundType(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return positionalSoundType(state, level, pos, entity);
    }

    /^*
     * NeoForge's 4-arg {@code getSoundType}: a {@link PositionalSoundBlock}'s answer for the position, else the
     * state's own sound. The sound-type mixins ({@code EntityMixin}, {@code LivingEntityMixin}, {@code BlockItemMixin},
     * {@code LevelEventHandlerMixin}) ask it where NeoForge passes the position.
     ^/
    public static SoundType positionalSoundType(BlockState state, LevelReader level, BlockPos pos,
            @Nullable Entity entity) {
        if (state.getBlock() instanceof PositionalSoundBlock block) {
            SoundType sound = block.soundType(level, pos, entity);
            if (sound != null) {
                return sound;
            }
        }
        return state.getSoundType();
    }

    @Override
    public float destroySpeed(Player player, BlockState state, BlockPos pos) {
        return FabricGameHooks.digSpeedAt(pos, () -> player.getDestroySpeed(state));
    }

    @Override
    public float explosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        float own = state.getBlock().getExplosionResistance();
        // The same adjustment ExplosionDamageCalculatorMixin makes for a real blast.
        if (state.getBlock() instanceof buildcraft.platform.hook.ExplosionArmouredBlock armoured) {
            return armoured.explosionResistance(level, pos, explosion, own);
        }
        return own;
    }

    @Override
    public int expDrop(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity breaker,
            ItemStack tool) {
        // An infested block's after-break step spawns its silverfish and drops no experience.
        if (state.getBlock() instanceof InfestedBlock) {
            return 0;
        }
        int[] caught = new int[1];
        int[] outer = CAUGHT_EXPERIENCE.get();
        CAUGHT_EXPERIENCE.set(caught);
        try {
            state.spawnAfterBreak(level, pos, tool, true);
        } finally {
            CAUGHT_EXPERIENCE.set(outer);
        }
        return caught[0];
    }

    /^*
     * Called by {@code BlockExperienceMixin} as {@code Block.popExperience} starts: true (and the orbs are not
     * spawned) when an {@link #expDrop} is catching the amount instead.
     ^/
    public static boolean catchExperience(int amount) {
        int[] caught = CAUGHT_EXPERIENCE.get();
        if (caught == null) {
            return false;
        }
        caught[0] += amount;
        return true;
    }

    @Override
    public boolean customDataContains(CustomData data, String key) {
        return data.tag.contains(key);
    }

    @Override
    public boolean canConvertToSource(FluidState fluid, ServerLevel level, BlockPos pos) {
        return fluid.getType() instanceof FlowingFluid flowing
                && ((FlowingFluidAccessor) flowing).buildcraft$canConvertToSource(level);
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return ((AttachmentTarget) entity).getAttachedOrCreate(PERSISTENT_DATA);
    }

    //? if >=1.21.10 && <26.3 {
    /^// Vanilla's fuel table, which Fabric API's FuelRegistryEvents feed.
    @Override
    public int burnTime(ItemStack stack, net.minecraft.world.level.Level level) {
        return level.fuelValues().burnDuration(stack);
    }
    ^///?}
    //? if <1.21.10 {
    /^// 1.21.1's static fuel map, which Fabric API's FuelRegistry feeds.
    @Override
    public int burnTime(ItemStack stack, net.minecraft.world.level.Level level) {
        return stack.isEmpty() ? 0 : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel()
                .getOrDefault(stack.getItem(), 0);
    }
    ^///?}

    @Override
    public <T extends SavedData> SavedDataType<T> levelSavedDataType(Identifier id, Function<ServerLevel, T> factory,
            Function<ServerLevel, Codec<T>> codec, DataFixTypes fixType) {
        return new SavedDataType<>(id, () -> factory.apply(FabricSavedDataLevels.current()), new Codec<T>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                return codec.apply(FabricSavedDataLevels.current()).decode(ops, input);
            }

            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                return codec.apply(FabricSavedDataLevels.current()).encode(input, ops, prefix);
            }
        }, fixType);
    }
}
*///?}
