/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;

/**
 * {@link GameHooks} on NeoForge: each hook is one listener on {@link NeoForge#EVENT_BUS}, registered at the call, so
 * the listeners keep the order BuildCraft's init registers them in. A hook without a {@link HookOrder} listens at
 * {@code NORMAL} and is not told about cancelled events, exactly like a bare {@code addListener}. Server-level and
 * server-player hooks drop the client's events here, which every BuildCraft listener used to do itself.
 */
public final class NeoForgeGameHooks implements GameHooks {

    /** The NeoForge priority a {@link HookOrder} names. */
    public static EventPriority priority(HookOrder order) {
        return switch (order) {
            case EARLY -> EventPriority.HIGH;
            case DEFAULT -> EventPriority.NORMAL;
            case LATE -> EventPriority.LOWEST;
        };
    }

    @Override
    public void onServerTickStart(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onLevelTickEnd(Consumer<ServerLevel> listener) {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level);
            }
        });
    }

    @Override
    public void onPlayerTickEnd(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onLevelUnload(Consumer<LevelAccessor> listener) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> listener.accept(event.getLevel()));
    }

    @Override
    public void onChunkLoad(BiConsumer<ServerLevel, ChunkAccess> listener) {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkUnload(BiConsumer<ServerLevel, ChunkAccess> listener) {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.accept(level, event.getChunk());
            }
        });
    }

    @Override
    public void onChunkSent(ChunkSent listener) {
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.Sent event) ->
                listener.onChunkSent(event.getLevel(), event.getPlayer(), event.getPos()));
    }

    @Override
    public void onEntityJoinLevel(EntityJoin listener) {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onJoin(event.getEntity(), level, event.loadedFromDisk());
            }
        });
    }

    @Override
    public void onPlayerLoggedIn(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLoggedOut(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onPlayerChangedDimension(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                listener.accept(player);
            }
        });
    }

    @Override
    public void onFinalizeSpawn(SpawnFinalized listener) {
        NeoForge.EVENT_BUS.addListener((FinalizeSpawnEvent event) -> {
            //? if >=1.21.10 {
            boolean conversion = event.getSpawnType() == net.minecraft.world.entity.EntitySpawnReason.CONVERSION;
            //?} else {
            /*boolean conversion = event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.CONVERSION;*/
            //?}
            listener.onFinalizeSpawn(event.getEntity(), conversion);
        });
    }

    @Override
    public void onLivingDrops(LivingDrops listener) {
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent event) ->
                listener.onDrops(event.getEntity(), event.getSource(), event.isRecentlyHit(), event.getDrops()));
    }

    @Override
    public void onRightClickBlock(RightClickBlock listener) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = listener.onRightClick(event.getEntity(), event.getLevel(), event.getHand(),
                    event.getHitVec(), event.getItemStack());
            if (result != null) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onEntityInteract(HookOrder order, boolean receiveCancelled, EntityInteract listener) {
        NeoForge.EVENT_BUS.addListener(priority(order), receiveCancelled, PlayerInteractEvent.EntityInteract.class,
                event -> listener.onInteract(event.getEntity(), event.getTarget(), event.isCanceled()));
    }

    @Override
    public void onBlockPlace(HookOrder order, boolean receiveCancelled, Consumer<BlockPlace> listener) {
        NeoForge.EVENT_BUS.addListener(priority(order), receiveCancelled, BlockEvent.EntityPlaceEvent.class,
                event -> listener.accept(new PlaceView(event)));
    }

    @Override
    public void onBreakSpeed(BreakSpeed listener) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.BreakSpeed event) -> event.setNewSpeed(listener.onBreakSpeed(
                event.getEntity(), event.getState(), event.getPosition().orElse(null), event.getNewSpeed())));
    }

    @Override
    public void onNeighborNotify(NeighborNotify listener) {
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent event) -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
                listener.onNeighborNotify(level, event.getPos(), event.getState());
            }
        });
    }

    @Override
    public void onExplosionDetonate(HookOrder order, ExplosionDetonate listener) {
        NeoForge.EVENT_BUS.addListener(priority(order), false, ExplosionEvent.Detonate.class,
                event -> listener.onDetonate(event.getLevel(), event.getExplosion(), event.getAffectedBlocks(),
                        event.getAffectedEntities()));
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> listener.accept(event.getDispatcher()));
    }

    @Override
    public void onDatapackSync(DatapackSync listener) {
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) ->
                listener.onSync(event.getPlayerList().getServer(), event.getRelevantPlayers().toList()));
    }

    /** NeoForge's opt-in recipe sync ({@code OnDatapackSyncEvent#sendRecipes}). The 1.21.1 line still has
     *  vanilla's own full recipe sync, so there is nothing to ask for there. */
    @Override
    public void syncRecipeTypes(List<RecipeType<?>> types) {
        //? if >=1.21.10 {
        RecipeType<?>[] copy = types.toArray(new RecipeType<?>[0]);
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> event.sendRecipes(copy));
        //?}
    }

    /** {@link BlockPlace} over the placement event; {@link #isProbe} is the placement probe's own mark. */
    private record PlaceView(BlockEvent.EntityPlaceEvent event) implements BlockPlace {
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
            return NeoForgePlaceProbe.isProbe(event);
        }

        @Override
        public boolean isCancelled() {
            return event.isCanceled();
        }
    }
}
//?}
