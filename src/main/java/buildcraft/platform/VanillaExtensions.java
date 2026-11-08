/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

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

/**
 * The vanilla calls NeoForge patches a context-aware twin into (and deprecates the plain one in favour of): the
 * position-aware block-state queries, the player's dig speed, the XP a block would drop, an entity's free-form
 * persistent tag and the level-aware saved-data type. NeoForge answers through its patch, so its events and
 * per-block overrides are seen; Fabric answers what vanilla does. Arguments documented as optional take null (the
 * contract package carries no nullness annotations).
 */
public interface VanillaExtensions {
    /** The sound type of the block at {@code pos} for {@code entity} (optional): NeoForge's 4-arg getSoundType. */
    SoundType soundType(BlockState state, LevelReader level, BlockPos pos, Entity entity);

    /** How fast {@code player} digs {@code state} at {@code pos} (NeoForge: with the BreakSpeed event). */
    float destroySpeed(Player player, BlockState state, BlockPos pos);

    /** The block's explosion resistance against {@code explosion} where it stands. */
    float explosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion);

    /**
     * The experience breaking {@code state} at {@code pos} with {@code tool} drops, enchantments applied, without
     * spawning it ({@code blockEntity} and {@code breaker} optional). Random, so ask once per break.
     */
    int expDrop(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity breaker,
            ItemStack tool);

    /** Whether the custom data holds {@code key}, without copying it. */
    boolean customDataContains(CustomData data, String key);

    /** Whether the fluid at {@code pos} takes part in the two-sources infinite-source rule there. */
    boolean canConvertToSource(FluidState fluid, ServerLevel level, BlockPos pos);

    /**
     * The entity's free-form compound saved with it (NeoForge's {@code getPersistentData}; Fabric: a BuildCraft data
     * attachment). Created empty on first ask, and changed in place.
     */
    CompoundTag persistentData(Entity entity);

    //? if <26.3 {
    /*/^*
     * The furnace burn time of {@code stack} in {@code level}'s fuel table, 0 when it is no fuel. NeoForge's patched
     * {@code getBurnTime} also asks the item itself; Fabric reads vanilla's table, which Fabric API's fuel registry
     * events feed. (26.3 moved fuel onto a datapack component that only the server can resolve.)
     ^/
    int burnTime(ItemStack stack, net.minecraft.world.level.Level level);
    *///?}

    //? if >=1.21.10 && <26.1 {
    /*/^*
     * What crafting with {@code stack} leaves behind (an empty bucket for a lava bucket), EMPTY for nothing: NeoForge's
     * stack-sensitive {@code getCraftingRemainder}; Fabric API's {@code getRecipeRemainder}, which asks the item too.
     ^/
    ItemStack craftingRemainder(ItemStack stack);
    *///?}

    //? if >=26.1 {
    /**
     * A saved-data type whose factory and codec are handed the level the storage belongs to (null for a storage with
     * none): NeoForge's level-aware {@code SavedDataType}; Fabric passes the level its storage was made for.
     */
    <T extends net.minecraft.world.level.saveddata.SavedData> net.minecraft.world.level.saveddata.SavedDataType<T>
            levelSavedDataType(net.minecraft.resources.Identifier id,
                    java.util.function.Function<ServerLevel, T> factory,
                    java.util.function.Function<ServerLevel, com.mojang.serialization.Codec<T>> codec,
                    net.minecraft.util.datafix.DataFixTypes fixType);
    //?}
}
