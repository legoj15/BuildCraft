/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.listener.ObjBooleanBiConsumer;
import net.minecraftforge.eventbus.api.listener.Priority;

import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;

/^*
 * {@link GameHooks} on MinecraftForge (not NeoForge): each hook is one listener on the event's own static {@code BUS},
 * registered at the call, so the listeners keep the order BuildCraft's init registers them in. A hook without a
 * {@link HookOrder} listens at {@code Priority.NORMAL}. Server-level and server-player hooks drop the client's events
 * here, which every BuildCraft listener used to do itself.
 *
 * <p><b>Cancelling and cancelled events (EventBus 7).</b> A cancellable event has no {@code setCanceled}: a listener
 * cancels by being a {@link Predicate} that answers true, and the first one to do so ends the chain. So a plain
 * {@link Consumer} listener is never told about a cancelled event, whatever its priority, and the only way to see one is
 * a monitor listener (priority {@code MONITOR}), which runs last and is given the final answer. A hook that takes
 * {@code receiveCancelled} therefore moves to the monitor stage when it is true: it cannot cancel there, and none of
 * these contracts' receive-cancelled listeners does (they only record), but it also runs after every other listener
 * rather than at {@code order}.
 *
 * <p><b>Placement.</b> {@code BlockEvent.EntityMultiPlaceEvent} is a subclass of {@code EntityPlaceEvent} with its own
 * bus, and a mutable event does not pass to its subclasses' buses, so {@link #onBlockPlace} listens on both (NeoForge's
 * one listener sees both kinds).
 *
 * <p><b>Entity interaction.</b> Forge 26.3 has no plain {@code EntityInteract}: its only entity-click event,
 * {@code EntityInteractSpecific}, is posted from the same vanilla call as NeoForge's ({@code Player.interactOn(entity,
 * hand, location)}, which on 26.3 is the one entry for every entity click and always has a hit location), so it is the
 * same event under another name.
 *
 * <p><b>Chunk sent.</b> Forge's {@code ChunkWatchEvent.Watch} is posted at the end of {@code PlayerChunkSender.sendChunk},
 * after the chunk packet is queued, where NeoForge posts {@code ChunkWatchEvent.Sent}; Forge has no event for the moment
 * before the send.
 *
 * <p><b>Gaps.</b> {@link #syncRecipeTypes}: Forge posts {@code OnDatapackSyncEvent} before vanilla sends the recipe
 * packet (observe only) and has no switch over what that packet carries.
 *^/
public final class ForgeGameHooks implements GameHooks {

    /^* The EventBus 7 priority a {@link HookOrder} names (NeoForge: {@code HIGH}, {@code NORMAL}, {@code LOWEST}). *^/
    public static byte priority(HookOrder order) {
        return switch (order) {
            case EARLY -> Priority.HIGH;
            case DEFAULT -> Priority.NORMAL;
            case LATE -> Priority.LOWEST;
        };
    }

    @Override
    public void onServerTickStart(Consumer<MinecraftServer> listener) {
        TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> listener.accept(event.server()));
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.accept(event.server()));
    }

    @Override
    public void onLevelTickEnd(Consumer<ServerLevel> listener) {
        TickEvent.LevelTickEvent.Post.BUS.addListener(event -> {
            if (event.level() instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }

    @Override
    public void onPlayerTickEnd(Consumer<ServerPlayer> listener) {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(event -> {
            if (event.player() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onLevelUnload(Consumer<LevelAccessor> listener) {
        LevelEvent.Unload.BUS.addListener(event -> listener.accept(event.getLevel()));
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ChunkEvent.Load.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkUnload(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ChunkEvent.Unload.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkSent(ChunkSent listener) {
        ChunkWatchEvent.Watch.BUS.addListener(event ->
                listener.onChunkSent(event.getLevel(), event.getPlayer(), event.getPos()));
    }

    @Override
    public void onEntityJoinLevel(EntityJoin listener) {
        EntityJoinLevelEvent.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onJoin(event.getEntity(), level, event.loadedFromDisk());
            }
        });
    }

    @Override
    public void onPlayerLoggedIn(Consumer<ServerPlayer> listener) {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLoggedOut(Consumer<ServerPlayer> listener) {
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerChangedDimension(Consumer<ServerPlayer> listener) {
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onFinalizeSpawn(SpawnFinalized listener) {
        MobSpawnEvent.FinalizeSpawn.BUS.addListener(event -> {
            boolean conversion = event.getSpawnReason() == EntitySpawnReason.CONVERSION;
            listener.onFinalizeSpawn(event.getEntity(), conversion);
        });
    }

    @Override
    public void onLivingDrops(LivingDrops listener) {
        // A block, not an expression lambda: on a cancellable bus the expression form fits both the Consumer and the
        // Predicate overload, and javac calls the call ambiguous.
        LivingDropsEvent.BUS.addListener(event -> {
            listener.onDrops(event.getEntity(), event.getSource(), event.isRecentlyHit(), event.getDrops());
        });
    }

    /^*
     * A refusal cancels the event with the result, and sets both use results to DENY as NeoForge's {@code setCanceled}
     * does: Forge's right-click patch returns the cancellation result at once for a cancelled event, so the two results
     * only matter to the listeners that read them afterwards (a monitor, or {@code PlatformProtection#rightClickBlock}).
     *^/
    @Override
    public void onRightClickBlock(RightClickBlock listener) {
        Predicate<PlayerInteractEvent.RightClickBlock> refusing = event -> {
            InteractionResult result = listener.onRightClick(event.getEntity(), event.getLevel(), event.getHand(),
                    event.getHitVec(), event.getItemStack());
            if (result == null) {
                return false;
            }
            event.setCancellationResult(result);
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            return true;
        };
        PlayerInteractEvent.RightClickBlock.BUS.addListener(refusing);
    }

    @Override
    public void onEntityInteract(HookOrder order, boolean receiveCancelled, EntityInteract listener) {
        if (receiveCancelled) {
            ObjBooleanBiConsumer<PlayerInteractEvent.EntityInteractSpecific> monitor = (event, cancelled) ->
                    listener.onInteract(event.getEntity(), event.getTarget(), cancelled);
            PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(monitor);
        } else {
            Consumer<PlayerInteractEvent.EntityInteractSpecific> plain = event ->
                    listener.onInteract(event.getEntity(), event.getTarget(), false);
            PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(priority(order), plain);
        }
    }

    @Override
    public void onBlockPlace(HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        addPlaceListener(BlockEvent.EntityPlaceEvent.BUS, order, receiveCancelled, listener);
        addPlaceListener(BlockEvent.EntityMultiPlaceEvent.BUS, order, receiveCancelled, listener);
    }

    private static <E extends BlockEvent.EntityPlaceEvent> void addPlaceListener(CancellableEventBus<E> bus,
            HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        if (receiveCancelled) {
            ObjBooleanBiConsumer<E> monitor = (event, cancelled) -> listener.accept(new PlaceView(event, cancelled));
            bus.addListener(monitor);
        } else {
            Consumer<E> plain = event -> listener.accept(new PlaceView(event, false));
            bus.addListener(priority(order), plain);
        }
    }

    @Override
    public void onBreakSpeed(BreakSpeed listener) {
        PlayerEvent.BreakSpeed.BUS.addListener(event -> {
            event.setNewSpeed(listener.onBreakSpeed(event.getEntity(), event.getState(), event.getPosition(),
                    event.getNewSpeed()));
        });
    }

    @Override
    public void onNeighborNotify(NeighborNotify listener) {
        BlockEvent.NeighborNotifyEvent.BUS.addListener(event -> {
            if (event.getLevel() instanceof Level level) {
                listener.onNeighborNotify(level, event.getPos(), event.getState());
            }
        });
    }

    @Override
    public void onExplosionDetonate(HookOrder order, ExplosionDetonate listener) {
        ExplosionEvent.Detonate.BUS.addListener(priority(order), event ->
                listener.onDetonate(event.getLevel(), event.getExplosion(), event.getAffectedBlocks(),
                        event.getAffectedEntities()));
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        RegisterCommandsEvent.BUS.addListener(event -> listener.accept(event.getDispatcher()));
    }

    @Override
    public void onDatapackSync(DatapackSync listener) {
        OnDatapackSyncEvent.BUS.addListener(event ->
                listener.onSync(event.getPlayerList().getServer(), List.copyOf(event.getPlayers())));
    }

    /^*
     * Not ported: Forge 26.3 posts {@code OnDatapackSyncEvent} before vanilla sends its recipe packet, which carries only
     * the stonecutter recipes and item properties, and the event can add nothing to that packet (NeoForge's
     * {@code sendRecipes}) or switch it off. Fabric's route is a Fabric API serializer registry that Forge has no twin
     * of. Until BuildCraft sends the recipes itself over its own payload, the guide's recipe panels have nothing to show
     * on a client of a remote server.
     *^/
    @Override
    public void syncRecipeTypes(List<RecipeType<?>> types) {
        // TODO(#209 F3): no Forge hook for the recipe sync: needs BuildCraft's own payload sent from onDatapackSync, plus
        // the client receiver that fills ClientGuideRecipeCache (NeoForge: OnDatapackSyncEvent#sendRecipes).
    }

    /^* {@link BlockPlace} over the placement event; {@link #isProbe} is the placement probe's own mark. *^/
    private record PlaceView(BlockEvent.EntityPlaceEvent event, boolean isCancelled) implements BlockPlace {
        @Override
        public LevelAccessor level() {
            return event.getLevel();
        }

        @Override
        public BlockPos pos() {
            return event.getPos();
        }

        @Override
        public BlockState placedBlock() {
            return event.getPlacedBlock();
        }

        @Override
        public BlockState placedAgainst() {
            return event.getPlacedAgainst();
        }

        @Override
        public Entity entity() {
            return event.getEntity();
        }

        @Override
        public boolean isProbe() {
            return ForgePlaceProbe.isProbe(event);
        }
    }
}
*///?}
