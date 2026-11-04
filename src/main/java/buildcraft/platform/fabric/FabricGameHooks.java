/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;

/^*
 * {@link GameHooks} on Fabric: Fabric API's events, registered at the call. A {@link HookOrder} other than
 * {@code DEFAULT} is a BuildCraft phase ordered before or after Fabric's default phase.
 *
 * <p><b>Gaps</b> (Fabric API has no such event; the hook registers nothing and its listener never runs):
 * {@link #onChunkSent} (wire systems reach a newly watching player on their next change instead),
 * {@link #onFinalizeSpawn}, {@link #onNeighborNotify}, {@link #onExplosionDetonate} and {@link #syncRecipeTypes}
 * (Fabric syncs recipes per serializer, not per type). Narrower than NeoForge: {@link #onBlockPlace} sees only the
 * placements BuildCraft's machines make through {@code FabricProtection} (Fabric API has no placement event, so a
 * player's own placement is not seen), {@link #onLevelUnload} sees server levels only (a client level's
 * unload is a client hook), {@link #onPlayerTickEnd} runs once per server tick for every player rather than inside each
 * player's own tick, {@link #onLivingDrops} adds its drops to the level after vanilla's own (Fabric's after-death
 * callback has no drop list), and a {@code receiveCancelled} {@link #onEntityInteract} listener is never told about a
 * refused click, because a Fabric callback that refuses stops the event there.
 *^/
public final class FabricGameHooks implements GameHooks {

    static final Identifier EARLY_PHASE = Identifier.fromNamespaceAndPath("buildcraftunofficial", "early");
    static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath("buildcraftunofficial", "late");

    /^* Registers {@code listener} on {@code event} in the phase {@code order} names. *^/
    static <T> void register(Event<T> event, HookOrder order, T listener) {
        switch (order) {
            case EARLY -> {
                event.addPhaseOrdering(EARLY_PHASE, Event.DEFAULT_PHASE);
                event.register(EARLY_PHASE, listener);
            }
            case DEFAULT -> event.register(listener);
            case LATE -> {
                event.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
                event.register(LATE_PHASE, listener);
            }
        }
    }

    @Override
    public void onServerTickStart(Consumer<MinecraftServer> listener) {
        ServerTickEvents.START_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onLevelTickEnd(Consumer<ServerLevel> listener) {
        ServerTickEvents.END_LEVEL_TICK.register(listener::accept);
    }

    @Override
    public void onPlayerTickEnd(Consumer<ServerPlayer> listener) {
        // No player tick event: every player once, after the server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onLevelUnload(Consumer<LevelAccessor> listener) {
        ServerLevelEvents.UNLOAD.register((server, level) -> listener.accept(level));
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> listener.accept(level, chunk));
    }

    @Override
    public void onChunkUnload(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ServerChunkEvents.CHUNK_UNLOAD.register(listener::accept);
    }

    @Override
    public void onChunkSent(ChunkSent listener) {
        // Gap: Fabric API has no chunk-sent event (see the class documentation)
    }

    @Override
    public void onEntityJoinLevel(EntityJoin listener) {
        // ALLOW_LOAD, not ENTITY_LOAD: it runs before the entity is added, as NeoForge's join event does, and it
        // knows whether the entity came from disk. Allowing is the default answer.
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) -> {
            listener.onJoin(entity, level, loadedFromDisk);
            return true;
        });
    }

    @Override
    public void onPlayerLoggedIn(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.getPlayer()));
    }

    @Override
    public void onPlayerLoggedOut(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> listener.accept(handler.getPlayer()));
    }

    @Override
    public void onPlayerChangedDimension(Consumer<ServerPlayer> listener) {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> listener.accept(player));
    }

    @Override
    public void onFinalizeSpawn(SpawnFinalized listener) {
        // Gap: Fabric API has no spawn-finalize event (see the class documentation)
    }

    @Override
    public void onLivingDrops(LivingDrops listener) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            List<ItemEntity> drops = new ArrayList<>();
            listener.onDrops(entity, source, entity.getLastHurtByPlayerMemoryTime() > 0, drops);
            for (ItemEntity drop : drops) {
                level.addFreshEntity(drop);
            }
        });
    }

    @Override
    public void onRightClickBlock(RightClickBlock listener) {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            InteractionResult result = listener.onRightClick(player, level, hand, hit, player.getItemInHand(hand));
            return result == null ? InteractionResult.PASS : result;
        });
    }

    @Override
    public void onEntityInteract(HookOrder order, boolean receiveCancelled, EntityInteract listener) {
        // A callback before this one that refused the click stopped the event: this one only sees allowed clicks
        register(UseEntityCallback.EVENT, order, (player, level, hand, entity, hit) -> {
            listener.onInteract(player, entity, false);
            return InteractionResult.PASS;
        });
    }

    @Override
    public void onBlockPlace(HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        // Narrower: Fabric API has no block-placement event, so only machine placements are seen (class documentation).
        FabricProtection.addPlaceListener(order, receiveCancelled, listener);
    }

    @Override
    public void onNeighborNotify(NeighborNotify listener) {
        // Gap: Fabric API has no neighbour-notify event (see the class documentation)
    }

    @Override
    public void onExplosionDetonate(HookOrder order, ExplosionDetonate listener) {
        // Gap: Fabric API has no explosion event (see the class documentation)
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> listener.accept(dispatcher));
    }

    @Override
    public void onDatapackSync(DatapackSync listener) {
        // Fabric posts this once per player (login, and each player on /reload)
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(
                (player, joined) -> listener.onSync(player.level().getServer(), List.of(player)));
    }

    @Override
    public void syncRecipeTypes(List<RecipeType<?>> types) {
        // Gap: Fabric syncs recipes per serializer (RecipeSynchronization), not per type (see the class documentation)
    }
}
*///?}
