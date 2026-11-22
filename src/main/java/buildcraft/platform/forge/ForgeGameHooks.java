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
//? if >=1.21.10 {
import net.minecraft.world.entity.EntitySpawnReason;
//?} else {
/^import net.minecraft.world.entity.MobSpawnType;
^///?}
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

//? if >=1.21.10 {
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.listener.Priority;
//?} else {
/^import net.minecraftforge.eventbus.api.Event.Result;
^///?}
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

import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;

/^*
 * {@link GameHooks} on MinecraftForge (not NeoForge): each hook is one listener on the game bus ({@link ForgeBus}),
 * registered at the call, so the listeners keep the order BuildCraft's init registers them in. A hook without a
 * {@link HookOrder} listens at normal priority. Server-level and server-player hooks drop the client's events
 * here, which every BuildCraft listener used to do itself.
 *
 * <p><b>Cancelling and cancelled events.</b> A listener that may refuse is {@link ForgeBus#refuse} (a {@link Predicate}
 * that answers true to cancel; the first one to do so ends the chain). A plain listener is never told about a cancelled
 * event, whatever its priority, and the only way to see one is {@link ForgeBus#monitor}, which runs last and is given the
 * final answer (EventBus 7's monitor stage; EventBus 6 keeps the same rule through ForgeBus, so both lines behave alike).
 * A hook that takes {@code receiveCancelled} therefore moves to the monitor stage when it is true: it cannot cancel there,
 * and none of these contracts' receive-cancelled listeners does (they only record), but it also runs after every other
 * listener rather than at {@code order}.
 *
 * <p><b>Placement.</b> On EventBus 7 {@code BlockEvent.EntityMultiPlaceEvent} is a subclass of {@code EntityPlaceEvent}
 * with its own bus, and a mutable event does not pass to its subclasses' buses, so {@link #onBlockPlace} listens on both
 * (NeoForge's one listener sees both kinds). EventBus 6 (1.21.1) hands a subclass event to its superclass's listeners,
 * so there the one listener on {@code EntityPlaceEvent} sees both, as on NeoForge; a second would see a multi-placement
 * twice.
 *
 * <p><b>Entity interaction.</b> Forge 26.3 has no plain {@code EntityInteract}: its only entity-click event,
 * {@code EntityInteractSpecific}, is posted from the same vanilla call as NeoForge's ({@code Player.interactOn(entity,
 * hand, location)}, which on 26.3 is the one entry for every entity click and always has a hit location), so it is the
 * same event under another name. Forge 1.21.1 and 1.21.11 (Forge 52, 61) post {@code EntityInteract} from
 * {@code Player.interactOn(entity, hand)},
 * NeoForge 1.21.1's point, so that is the event there.
 *
 * <p><b>Chunk sent.</b> Forge's {@code ChunkWatchEvent.Watch} is posted at the end of {@code PlayerChunkSender.sendChunk},
 * after the chunk packet is queued, where NeoForge posts {@code ChunkWatchEvent.Sent}; Forge has no event for the moment
 * before the send (1.21.1 posts it from the same place).
 *
 * <p><b>1.21.1 (EventBus 6).</b> The events are classes: the tick events carry {@code getServer()} and public
 * {@code level} / {@code player} fields where 26.3's records have accessors, the spawn reason is a {@code MobSpawnType},
 * a dig speed's position is an {@code Optional}, and the use results are EventBus 6's {@code Event.Result}.
 *
 * <p><b>Gaps.</b> {@link #syncRecipeTypes}: Forge posts {@code OnDatapackSyncEvent} before vanilla sends the recipe
 * packet (observe only) and has no switch over what that packet carries.
 *^/
public final class ForgeGameHooks implements GameHooks {

    //? if >=1.21.10 {
    /^* The EventBus 7 priority a {@link HookOrder} names (NeoForge: {@code HIGH}, {@code NORMAL}, {@code LOWEST}). *^/
    public static byte priority(HookOrder order) {
        return switch (order) {
            case EARLY -> Priority.HIGH;
            case DEFAULT -> Priority.NORMAL;
            case LATE -> Priority.LOWEST;
        };
    }
    //?}

    @Override
    public void onServerTickStart(Consumer<MinecraftServer> listener) {
        //? if >=1.21.10 {
        ForgeBus.listen(TickEvent.ServerTickEvent.Pre.class, event -> listener.accept(event.server()));
        //?} else {
        /^ForgeBus.listen(TickEvent.ServerTickEvent.Pre.class, event -> listener.accept(event.getServer()));
        ^///?}
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        //? if >=1.21.10 {
        ForgeBus.listen(TickEvent.ServerTickEvent.Post.class, event -> listener.accept(event.server()));
        //?} else {
        /^ForgeBus.listen(TickEvent.ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
        ^///?}
    }

    @Override
    public void onLevelTickEnd(Consumer<ServerLevel> listener) {
        ForgeBus.listen(TickEvent.LevelTickEvent.Post.class, event -> {
            //? if >=1.21.10 {
            Level eventLevel = event.level();
            //?} else {
            /^Level eventLevel = event.level;
            ^///?}
            if (eventLevel instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }

    @Override
    public void onPlayerTickEnd(Consumer<ServerPlayer> listener) {
        ForgeBus.listen(TickEvent.PlayerTickEvent.Post.class, event -> {
            //? if >=1.21.10 {
            Entity eventPlayer = event.player();
            //?} else {
            /^Entity eventPlayer = event.player;
            ^///?}
            if (eventPlayer instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onLevelUnload(Consumer<LevelAccessor> listener) {
        ForgeBus.listen(LevelEvent.Unload.class, event -> listener.accept(event.getLevel()));
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ForgeBus.listen(ChunkEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkUnload(BiConsumer<ServerLevel, ChunkAccess> listener) {
        ForgeBus.listen(ChunkEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkSent(ChunkSent listener) {
        ForgeBus.listen(ChunkWatchEvent.Watch.class, event ->
                listener.onChunkSent(event.getLevel(), event.getPlayer(), event.getPos()));
    }

    @Override
    public void onEntityJoinLevel(EntityJoin listener) {
        ForgeBus.listen(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onJoin(event.getEntity(), level, event.loadedFromDisk());
            }
        });
    }

    @Override
    public void onPlayerLoggedIn(Consumer<ServerPlayer> listener) {
        ForgeBus.listen(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLoggedOut(Consumer<ServerPlayer> listener) {
        ForgeBus.listen(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerChangedDimension(Consumer<ServerPlayer> listener) {
        ForgeBus.listen(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onFinalizeSpawn(SpawnFinalized listener) {
        ForgeBus.listen(MobSpawnEvent.FinalizeSpawn.class, event -> {
            //? if >=1.21.10 {
            boolean conversion = event.getSpawnReason() == EntitySpawnReason.CONVERSION;
            //?} else {
            /^boolean conversion = event.getSpawnType() == MobSpawnType.CONVERSION;
            ^///?}
            listener.onFinalizeSpawn(event.getEntity(), conversion);
        });
    }

    @Override
    public void onLivingDrops(LivingDrops listener) {
        ForgeBus.listen(LivingDropsEvent.class,
                event -> listener.onDrops(event.getEntity(), event.getSource(), event.isRecentlyHit(), event.getDrops()));
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
        ForgeBus.refuse(PlayerInteractEvent.RightClickBlock.class, refusing);
    }

    @Override
    public void onEntityInteract(HookOrder order, boolean receiveCancelled, EntityInteract listener) {
        //? if >=26.1 {
        Class<PlayerInteractEvent.EntityInteractSpecific> type = PlayerInteractEvent.EntityInteractSpecific.class;
        //?} else {
        /^Class<PlayerInteractEvent.EntityInteract> type = PlayerInteractEvent.EntityInteract.class;
        ^///?}
        if (receiveCancelled) {
            ForgeBus.monitor(type, (event, cancelled) -> listener.onInteract(event.getEntity(), event.getTarget(), cancelled));
        } else {
            ForgeBus.listen(type, order, event -> listener.onInteract(event.getEntity(), event.getTarget(), false));
        }
    }

    @Override
    public void onBlockPlace(HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        addPlaceListener(BlockEvent.EntityPlaceEvent.class, order, receiveCancelled, listener);
        //? if >=1.21.10 {
        addPlaceListener(BlockEvent.EntityMultiPlaceEvent.class, order, receiveCancelled, listener);
        //?}
    }

    private static <E extends BlockEvent.EntityPlaceEvent> void addPlaceListener(Class<E> type,
            HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        if (receiveCancelled) {
            ForgeBus.monitor(type, (event, cancelled) -> listener.accept(new PlaceView(event, cancelled)));
        } else {
            ForgeBus.listen(type, order, event -> listener.accept(new PlaceView(event, false)));
        }
    }

    @Override
    public void onBreakSpeed(BreakSpeed listener) {
        ForgeBus.listen(PlayerEvent.BreakSpeed.class, event -> {
            //? if >=26.3 {
            BlockPos pos = event.getPosition();
            //?} else {
            /^BlockPos pos = event.getPosition().orElse(null);
            ^///?}
            event.setNewSpeed(listener.onBreakSpeed(event.getEntity(), event.getState(), pos, event.getNewSpeed()));
        });
    }

    @Override
    public void onNeighborNotify(NeighborNotify listener) {
        ForgeBus.listen(BlockEvent.NeighborNotifyEvent.class, event -> {
            if (event.getLevel() instanceof Level level) {
                listener.onNeighborNotify(level, event.getPos(), event.getState());
            }
        });
    }

    @Override
    public void onExplosionDetonate(HookOrder order, ExplosionDetonate listener) {
        ForgeBus.listen(ExplosionEvent.Detonate.class, order, event ->
                listener.onDetonate(event.getLevel(), event.getExplosion(), event.getAffectedBlocks(),
                        event.getAffectedEntities()));
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        ForgeBus.listen(RegisterCommandsEvent.class, event -> listener.accept(event.getDispatcher()));
    }

    @Override
    public void onDatapackSync(DatapackSync listener) {
        ForgeBus.listen(OnDatapackSyncEvent.class, event ->
                listener.onSync(event.getPlayerList().getServer(), List.copyOf(event.getPlayers())));
    }

    /^*
     * Forge has no recipe sync to switch on: its {@code OnDatapackSyncEvent} is posted before vanilla sends its recipe
     * packet (on 26.3 that packet carries only the stonecutter recipes and item properties), and the event can add nothing
     * to it (NeoForge's {@code sendRecipes}); Fabric's route is a Fabric API serializer registry Forge has no twin of. So
     * BuildCraft sends the recipes itself over its own payload ({@link ForgeRecipeSync}); the client side of it
     * ({@code ClientHooks#onRecipesReceived}) is the client slice's one call to {@code ForgeRecipeSync#addClientListener}.
     * Forge 1.21.1 still sends a client vanilla's full recipe list, so there is nothing to ask for there (as NeoForge's
     * 1.21.1 arm), and {@code ForgeRecipeSync} is not built on that line.
     *^/
    @Override
    public void syncRecipeTypes(List<RecipeType<?>> types) {
        //? if >=1.21.10 {
        ForgeRecipeSync.request(types);
        //?}
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
