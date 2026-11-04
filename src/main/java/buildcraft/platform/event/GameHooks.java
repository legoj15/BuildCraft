/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.event;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The game events BuildCraft listens to, one typed method each. Register during BuildCraft's init (the listener
 * registers at once and stays for the life of the game); a listener runs at the loader's default order unless the
 * method takes a {@link HookOrder}. A listener that can refuse what happened says so in its return value; none of
 * these listeners is told about an event something before it already refused, unless the method takes
 * {@code receiveCancelled}.
 *
 * <p>Client-only game events (client ticks, rendering, tooltips, the client's own login) are not here.
 */
public interface GameHooks {

    // ── Ticks ─────────────────────────────────────────────────────────────────────────────────────────────────

    /** Runs {@code listener} at the start of every server tick, before any level ticks. */
    void onServerTickStart(Consumer<MinecraftServer> listener);

    /** Runs {@code listener} at the end of every server tick, after every level has ticked. */
    void onServerTickEnd(Consumer<MinecraftServer> listener);

    /** Runs {@code listener} at the end of each server level's tick. */
    void onLevelTickEnd(Consumer<ServerLevel> listener);

    /** Runs {@code listener} at the end of each server player's tick. */
    void onPlayerTickEnd(Consumer<ServerPlayer> listener);

    // ── Levels and chunks ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * Runs {@code listener} when a level unloads: each server level when the server stops (after the final save),
     * and, on a client, the client's level when it is replaced or the connection closes. Check
     * {@code isClientSide()} where only one side matters.
     */
    void onLevelUnload(Consumer<LevelAccessor> listener);

    /** Runs {@code listener} when a chunk of a server level loads. The chunk may not be complete yet: do not touch
     *  the level from the listener. */
    void onChunkLoad(BiConsumer<ServerLevel, ChunkAccess> listener);

    /** Runs {@code listener} when a chunk of a server level unloads, after it was saved; its blocks are all still
     *  there. */
    void onChunkUnload(BiConsumer<ServerLevel, ChunkAccess> listener);

    /** Runs {@code listener} each time a player's client has been sent a chunk. */
    void onChunkSent(ChunkSent listener);

    // ── Entities and players ──────────────────────────────────────────────────────────────────────────────────

    /** Runs {@code listener} when an entity is about to join a server level (spawned, loaded, or arriving through a
     *  dimension change or respawn). */
    void onEntityJoinLevel(EntityJoin listener);

    /** Runs {@code listener} when a player has logged in to the server. */
    void onPlayerLoggedIn(Consumer<ServerPlayer> listener);

    /** Runs {@code listener} when a player logs out of the server. */
    void onPlayerLoggedOut(Consumer<ServerPlayer> listener);

    /** Runs {@code listener} when a player has moved to another dimension. */
    void onPlayerChangedDimension(Consumer<ServerPlayer> listener);

    /** Runs {@code listener} when a mob's spawn is finalized (its equipment and data are being filled in). */
    void onFinalizeSpawn(SpawnFinalized listener);

    /** Runs {@code listener} with a dying entity's drops before they are put in the level; it may add to them. */
    void onLivingDrops(LivingDrops listener);

    // ── Blocks and interaction ────────────────────────────────────────────────────────────────────────────────

    /** Runs {@code listener} when a player right-clicks a block, on both sides, before anything is used. */
    void onRightClickBlock(RightClickBlock listener);

    /**
     * Runs {@code listener} when a player right-clicks an entity, at {@code order}; with {@code receiveCancelled}
     * it is also told about a click something before it refused.
     */
    void onEntityInteract(HookOrder order, boolean receiveCancelled, EntityInteract listener);

    /**
     * Runs {@code listener} when an entity has placed a block (and when a machine's placement probe asks whether it
     * may, see {@link BlockPlace#isProbe}), at {@code order}; with {@code receiveCancelled} it is also told about a
     * placement something before it refused.
     */
    void onBlockPlace(HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener);

    /** Runs {@code listener} when a block change notifies its neighbours. */
    void onNeighborNotify(NeighborNotify listener);

    /** Runs {@code listener} when an explosion has worked out what it hits, at {@code order}; it may take blocks and
     *  entities out of the lists. */
    void onExplosionDetonate(HookOrder order, ExplosionDetonate listener);

    // ── Server data ───────────────────────────────────────────────────────────────────────────────────────────

    /** Runs {@code listener} each time the server's commands are (re)built. */
    void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener);

    /** Runs {@code listener} each time the server sends players its datapack contents (login and {@code /reload}). */
    void onDatapackSync(DatapackSync listener);

    /** Has the recipes of {@code types} sent to every client with the datapack contents, where vanilla no longer
     *  sends them on its own. */
    void syncRecipeTypes(List<RecipeType<?>> types);

    // ── Listener shapes ───────────────────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface ChunkSent {
        void onChunkSent(ServerLevel level, ServerPlayer player, ChunkPos pos);
    }

    @FunctionalInterface
    interface EntityJoin {
        /** {@code loadedFromDisk}: the entity was saved in a chunk, not newly spawned or moved here. */
        void onJoin(Entity entity, ServerLevel level, boolean loadedFromDisk);
    }

    @FunctionalInterface
    interface SpawnFinalized {
        /** {@code conversion}: the mob is being made from another one (a zombie villager cured, a zombie drowned). */
        void onFinalizeSpawn(Mob mob, boolean conversion);
    }

    @FunctionalInterface
    interface LivingDrops {
        /** {@code recentlyHit}: a player hit the entity shortly before it died. */
        void onDrops(LivingEntity entity, DamageSource source, boolean recentlyHit, Collection<ItemEntity> drops);
    }

    @FunctionalInterface
    interface RightClickBlock {
        /** @return null to let the click go on; a result to refuse it, answered to the click with that result */
        InteractionResult onRightClick(Player player, Level level, InteractionHand hand, BlockHitResult hit,
                ItemStack stack);
    }

    @FunctionalInterface
    interface EntityInteract {
        /** {@code cancelled}: something before this listener refused the click. */
        void onInteract(Player player, Entity target, boolean cancelled);
    }

    @FunctionalInterface
    interface NeighborNotify {
        void onNeighborNotify(Level level, BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    interface ExplosionDetonate {
        void onDetonate(Level level, Explosion explosion, List<BlockPos> affectedBlocks, List<Entity> affectedEntities);
    }

    @FunctionalInterface
    interface DatapackSync {
        void onSync(MinecraftServer server, List<ServerPlayer> players);
    }

    /** A block placement as a listener sees it ({@link #onBlockPlace}). */
    interface BlockPlace {
        LevelAccessor level();

        BlockPos pos();

        /** The state the placement put down. */
        BlockState placedBlock();

        /** The state of the block the new one was placed against. */
        BlockState placedAgainst();

        /** The entity that placed it, or null for none. */
        Entity entity();

        /** True when this is a machine's permission probe ({@link PlatformProtection#canPlace}), which places
         *  nothing. */
        boolean isProbe();

        /** True when something before this listener refused the placement. */
        boolean isCancelled();
    }
}
