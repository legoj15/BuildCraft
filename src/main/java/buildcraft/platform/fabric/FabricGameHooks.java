/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import eu.pb4.common.protection.api.CommonProtection;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
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
 * {@code DEFAULT} is a BuildCraft phase ordered before or after Fabric's default phase. Where Fabric API has no event,
 * a mixin in {@code platform.fabric.mixin} calls one of this class's {@code fire...} methods where NeoForge posts its
 * event: the chunk sent to a player ({@code PlayerChunkSenderMixin}), the spawn finalize ({@code MobMixin}), the dying
 * entity's drops ({@code LivingEntityMixin}, collected at the drop sites NeoForge captures), the neighbour notify
 * ({@code ServerLevelMixin}, {@code DiodeBlockMixin}), the explosion's lists ({@code ServerExplosionMixin}), each
 * player's own tick ({@code PlayerMixin}), a fake player's entity interaction ({@code PlayerMixin}, see
 * {@link #fireFakePlayerEntityInteract}), the player's dig speed ({@code PlayerMixin}, told the dug position by
 * {@code BlockBehaviourMixin} through {@link #digSpeedAt}, as NeoForge's position-aware {@code getDestroySpeed}) and the
 * client level's unload ({@code client.MinecraftMixin}).
 *
 * <p><b>Gap</b> (the hook registers nothing): {@link #syncRecipeTypes} (Fabric syncs recipes per serializer, not per
 * type). Narrower than NeoForge: {@link #onBlockPlace} sees only the placements BuildCraft's machines make through
 * {@code FabricProtection} (Fabric API has no placement event; a player's own placement still reaches the
 * neighbour-notify listeners), {@link #onFinalizeSpawn} runs from inside {@code Mob}'s own finalize (a mob whose
 * override never calls it is not seen, and a spawner that skips the finalize posts nothing), and a
 * {@code receiveCancelled} {@link #onEntityInteract} listener is told about a refused click by a fake player only
 * (Fabric API asks a real player's click in its packet handler, where a refusing callback ends the event).
 *^/
public final class FabricGameHooks implements GameHooks {

    static final Identifier EARLY_PHASE = Identifier.fromNamespaceAndPath("buildcraftunofficial", "early");
    static final Identifier LATE_PHASE = Identifier.fromNamespaceAndPath("buildcraftunofficial", "late");

    // The mixin-fed hooks. Copy-on-write: a spawn finalize can run off the server thread (world generation).
    private static final List<ChunkSent> CHUNK_SENT = new CopyOnWriteArrayList<>();
    private static final List<SpawnFinalized> FINALIZE_SPAWN = new CopyOnWriteArrayList<>();
    private static final List<LivingDrops> LIVING_DROPS = new CopyOnWriteArrayList<>();
    private static final List<NeighborNotify> NEIGHBOR_NOTIFY = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ServerPlayer>> PLAYER_TICK_END = new CopyOnWriteArrayList<>();
    private static final List<BreakSpeed> BREAK_SPEED = new CopyOnWriteArrayList<>();
    /^* The block whose dig speed this thread is working out ({@link #digSpeedAt}), or null. *^/
    private static final ThreadLocal<BlockPos> DIG_POS = new ThreadLocal<>();
    private static final List<Consumer<LevelAccessor>> CLIENT_LEVEL_UNLOAD = new CopyOnWriteArrayList<>();
    /^* The {@code receiveCancelled} entity-interaction listeners, by order, told how a fake player's click ended. *^/
    private static final EnumMap<HookOrder, List<EntityInteract>> INTERACT_RECEIVE_CANCELLED = new EnumMap<>(
            java.util.Map.of(HookOrder.EARLY, new CopyOnWriteArrayList<>(),
                    HookOrder.DEFAULT, new CopyOnWriteArrayList<>(),
                    HookOrder.LATE, new CopyOnWriteArrayList<>()));
    /^* The fake player's entity interaction {@link #fireFakePlayerEntityInteract} is asking right now, or null.
     *  Server thread only. *^/
    private static InteractDispatch interactInFlight;

    /^* One fake player's click being asked: the {@code receiveCancelled} listeners the callback has reached so far. *^/
    private record InteractDispatch(Player player, Entity target, Set<EntityInteract> reached) {}
    private static final Event<ExplosionDetonate> EXPLOSION_DETONATE = EventFactory.createArrayBacked(
            ExplosionDetonate.class, listeners -> (level, explosion, blocks, entities) -> {
                for (ExplosionDetonate listener : listeners) {
                    listener.onDetonate(level, explosion, blocks, entities);
                }
            });

    /^* Registers {@code listener} on {@code event} in the phase {@code order} names (also the game tests' claims). *^/
    public static <T> void register(Event<T> event, HookOrder order, T listener) {
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
        PLAYER_TICK_END.add(listener);
    }

    @Override
    public void onLevelUnload(Consumer<LevelAccessor> listener) {
        ServerLevelEvents.UNLOAD.register((server, level) -> listener.accept(level));
        CLIENT_LEVEL_UNLOAD.add(listener);
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
        CHUNK_SENT.add(listener);
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
        FINALIZE_SPAWN.add(listener);
    }

    @Override
    public void onLivingDrops(LivingDrops listener) {
        LIVING_DROPS.add(listener);
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
        // A callback before this one that refused the click stopped the event: in it, this one only sees allowed
        // clicks. A receiveCancelled one is also told, by fireFakePlayerEntityInteract, about a fake player's click
        // that was refused before it; a LATE one is told about every fake player's click only there, once the
        // callback has ended, so it sees what every listener decided.
        if (receiveCancelled) {
            INTERACT_RECEIVE_CANCELLED.get(order).add(listener);
        }
        register(UseEntityCallback.EVENT, order, (player, level, hand, entity, hit) -> {
            InteractDispatch dispatch = interactInFlight;
            if (receiveCancelled && dispatch != null && dispatch.player() == player && dispatch.target() == entity) {
                if (order == HookOrder.LATE) {
                    return InteractionResult.PASS;
                }
                dispatch.reached().add(listener);
            }
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
    public void onBreakSpeed(BreakSpeed listener) {
        BREAK_SPEED.add(listener);
    }

    @Override
    public void onNeighborNotify(NeighborNotify listener) {
        NEIGHBOR_NOTIFY.add(listener);
    }

    @Override
    public void onExplosionDetonate(HookOrder order, ExplosionDetonate listener) {
        register(EXPLOSION_DETONATE, order, listener);
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

    // ── Where the mixins post (and the game tests, as NeoForge's tests post its events) ──────────────────────

    /^* A chunk was sent to {@code player}'s client ({@code PlayerChunkSender.sendChunk}). *^/
    public static void fireChunkSent(ServerLevel level, ServerPlayer player, ChunkPos pos) {
        for (ChunkSent listener : CHUNK_SENT) {
            listener.onChunkSent(level, player, pos);
        }
    }

    /^* {@code mob}'s spawn is being finalized ({@code Mob.finalizeSpawn}). *^/
    public static void fireFinalizeSpawn(Mob mob, boolean conversion) {
        for (SpawnFinalized listener : FINALIZE_SPAWN) {
            listener.onFinalizeSpawn(mob, conversion);
        }
    }

    /^* {@code entity} died with {@code drops} collected and not yet in the level; a listener may add to them. *^/
    public static void fireLivingDrops(LivingEntity entity, DamageSource source, boolean recentlyHit,
            List<ItemEntity> drops) {
        for (LivingDrops listener : LIVING_DROPS) {
            listener.onDrops(entity, source, recentlyHit, drops);
        }
    }

    /^* A block change at {@code pos} is about to notify its neighbours. *^/
    /^*
     * Works out a dig speed ({@code speed}, a call of vanilla's {@code Player.getDestroySpeed}) with {@code pos} as
     * the block being dug, which {@link #fireBreakSpeed} hands the listeners: NeoForge's position-aware
     * {@code getDestroySpeed(state, pos)}.
     *^/
    public static float digSpeedAt(@Nullable BlockPos pos, java.util.function.DoubleSupplier speed) {
        BlockPos outer = DIG_POS.get();
        DIG_POS.set(pos);
        try {
            return (float) speed.getAsDouble();
        } finally {
            DIG_POS.set(outer);
        }
    }

    /^* {@code player}'s dig speed for {@code state} is {@code speed}; the listeners may change it ({@code PlayerMixin},
     *  where NeoForge posts its break-speed event: the end of {@code getDestroySpeed}). *^/
    public static float fireBreakSpeed(Player player, BlockState state, float speed) {
        if (BREAK_SPEED.isEmpty()) {
            return speed;
        }
        BlockPos pos = DIG_POS.get();
        for (BreakSpeed listener : BREAK_SPEED) {
            speed = listener.onBreakSpeed(player, state, pos, speed);
        }
        return speed;
    }

    public static void fireNeighborNotify(Level level, BlockPos pos, BlockState state) {
        for (NeighborNotify listener : NEIGHBOR_NOTIFY) {
            listener.onNeighborNotify(level, pos, state);
        }
    }

    /^* An explosion has worked out what it hits; a listener may take blocks and entities out of the lists. *^/
    public static void fireExplosionDetonate(Level level, Explosion explosion, List<BlockPos> blocks,
            List<Entity> entities) {
        EXPLOSION_DETONATE.invoker().onDetonate(level, explosion, blocks, entities);
    }

    /^* {@code player} has finished its own tick ({@code Player.tick}). *^/
    public static void firePlayerTickEnd(ServerPlayer player) {
        for (Consumer<ServerPlayer> listener : PLAYER_TICK_END) {
            listener.accept(player);
        }
    }

    /^*
     * A fake player is interacting with {@code target} ({@code Player.interactOn}, where NeoForge posts its
     * entity-interaction event; Fabric API asks only a real player's click, in its packet handler): the protection
     * providers are asked about the click, then {@code UseEntityCallback}, and the {@code receiveCancelled}
     * listeners are told how it ended (see {@link #onEntityInteract}).
     *
     * @return null when the click may go on, else what {@code interactOn} answers instead: FAIL for a refusal, or the
     *         result the refusing callback gave (a consuming one is a callback that took the click itself)
     *^/
    @Nullable
    public static InteractionResult fireFakePlayerEntityInteract(Player player, Entity target, InteractionHand hand,
            Vec3 location) {
        if (!CommonProtection.canInteractEntity(player.level(), target, player.nameAndId(), player)) {
            tellInteractEnded(player, target, true, Set.of());
            return InteractionResult.FAIL;
        }
        InteractDispatch outer = interactInFlight;
        InteractDispatch dispatch = new InteractDispatch(player, target,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        interactInFlight = dispatch;
        InteractionResult result;
        try {
            result = UseEntityCallback.EVENT.invoker()
                    .interact(player, player.level(), hand, target, new EntityHitResult(target, location));
        } finally {
            interactInFlight = outer;
        }
        boolean refused = result != InteractionResult.PASS;
        tellInteractEnded(player, target, refused, dispatch.reached());
        return refused ? result : null;
    }

    /^* Tells the receiveCancelled listeners not yet {@code reached} about a click: the LATE ones always, the others
     *  only when it was {@code refused} (they saw an allowed one inside the callback). *^/
    private static void tellInteractEnded(Player player, Entity target, boolean refused, Set<EntityInteract> reached) {
        for (HookOrder order : HookOrder.values()) {
            for (EntityInteract listener : INTERACT_RECEIVE_CANCELLED.get(order)) {
                if ((order == HookOrder.LATE || refused) && !reached.contains(listener)) {
                    listener.onInteract(player, target, refused);
                }
            }
        }
    }

    /^* The client's level is closing (replaced, or the connection closed); server levels go through Fabric API. *^/
    public static void fireClientLevelUnload(Level level) {
        for (Consumer<LevelAccessor> listener : CLIENT_LEVEL_UNLOAD) {
            listener.accept(level);
        }
    }

    // ── NeoForge's captureDrops, for fireLivingDrops ─────────────────────────────────────────────────────────

    /^* An entity collecting the drops of its death ({@code EntityMixin}). *^/
    public interface DropCapture {
        /^* The drops collected so far, or null when no death is collecting them. *^/
        @Nullable
        List<ItemEntity> buildcraft$capturedDrops();

        void buildcraft$setCapturedDrops(@Nullable List<ItemEntity> drops);
    }

    /^* Starts collecting {@code entity}'s death drops (the start of {@code LivingEntity.dropAllDeathLoot}). *^/
    public static void startDropCapture(LivingEntity entity) {
        ((DropCapture) entity).buildcraft$setCapturedDrops(new ArrayList<>());
    }

    /^* @return true when {@code drop} was collected for {@code dropper}'s death instead of being put in the level *^/
    public static boolean captureDrop(Entity dropper, ItemEntity drop) {
        List<ItemEntity> drops = ((DropCapture) dropper).buildcraft$capturedDrops();
        if (drops == null) {
            return false;
        }
        drops.add(drop);
        return true;
    }

    /^* Ends the collection (the end of {@code dropAllDeathLoot}): the listeners see the drops, then they go in the
     *  level, as NeoForge's {@code LivingDropsEvent} does. *^/
    public static void finishDropCapture(LivingEntity entity, DamageSource source) {
        DropCapture capture = (DropCapture) entity;
        List<ItemEntity> drops = capture.buildcraft$capturedDrops();
        capture.buildcraft$setCapturedDrops(null);
        if (drops == null) {
            return;
        }
        fireLivingDrops(entity, source, entity.getLastHurtByPlayerMemoryTime() > 0, drops);
        for (ItemEntity drop : drops) {
            entity.level().addFreshEntity(drop);
        }
    }
}
*///?}
