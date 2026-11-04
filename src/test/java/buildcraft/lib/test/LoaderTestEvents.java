/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.Collection;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import buildcraft.platform.event.HookOrder;

/**
 * The loader events a game test fires itself, so the wiring from the loader's event to BuildCraft's listener is under
 * test, not just the listener: each posts the event the running loader would post (NeoForge's bus, or the Fabric API
 * event's invoker), so every listener the loader holds runs, BuildCraft's {@code Platform} hooks included. The
 * {@code claim...} and {@code observe...} methods stand up a claim mod or an observer on the running loader's own
 * event for as long as the returned handle is open; a {@link HookOrder} is the claim's place among the listeners
 * (NeoForge's priority, or the Fabric event phase BuildCraft's own hooks use for it). Fabric events cannot be
 * unregistered, so each Fabric claim kind is one callback registered once (per order), handing the click to the open
 * claims.
 */
public final class LoaderTestEvents {

    private LoaderTestEvents() {}

    /** A server level unloading (NeoForge {@code LevelEvent.Unload}, Fabric {@code ServerLevelEvents.UNLOAD}). */
    public static void levelUnload(ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
        //?} else {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.invoker().onLevelUnload(level.getServer(), level);*/
        //?}
    }

    /**
     * A level of either side unloading (NeoForge {@code LevelEvent.Unload}; on Fabric a client level goes through
     * {@code FabricGameHooks.fireClientLevelUnload}, where its {@code MinecraftMixin} posts it).
     */
    public static void levelUnload(net.minecraft.world.level.Level level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
        //?} else {
        /*if (!level.isClientSide() && level instanceof ServerLevel server) {
            levelUnload(server);
        } else {
            buildcraft.platform.fabric.FabricGameHooks.fireClientLevelUnload(level);
        }*/
        //?}
    }

    /**
     * A server level saved (NeoForge {@code LevelEvent.Save} for that level; Fabric has only the server-wide
     * {@code ServerLifecycleEvents.AFTER_SAVE}, which BuildCraft's save hook turns into one call per level).
     */
    public static void levelSave(ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Save(level));
        //?} else {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.AFTER_SAVE.invoker()
                .onAfterSave(level.getServer(), false, false);*/
        //?}
    }

    /**
     * An entity joining a level, newly spawned (NeoForge {@code EntityJoinLevelEvent}, Fabric
     * {@code ServerEntityEvents.ALLOW_LOAD}, whose answer is ignored here).
     */
    public static void entityJoin(Entity entity, ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(entity, level));
        //?} else {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD.invoker()
                .onAllowLoad(entity, level, null, false);*/
        //?}
    }

    /**
     * A dying entity's drops being collected into {@code drops} (NeoForge {@code LivingDropsEvent}; Fabric
     * {@code FabricGameHooks.fireLivingDrops}, where its {@code LivingEntityMixin} posts it).
     */
    public static void livingDrops(LivingEntity victim, DamageSource source, Collection<ItemEntity> drops,
            boolean recentlyHit) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(victim, source, drops, recentlyHit));
        //?} else {
        /*buildcraft.platform.fabric.FabricGameHooks.fireLivingDrops(victim, source, recentlyHit,
                drops instanceof java.util.List<ItemEntity> list ? list : new java.util.ArrayList<>(drops));*/
        //?}
    }

    /**
     * A mob's spawn being finalized, as a natural spawn (NeoForge {@code FinalizeSpawnEvent}; Fabric
     * {@code FabricGameHooks.fireFinalizeSpawn}, where its {@code MobMixin} posts it).
     */
    public static void finalizeSpawn(ServerLevel level, Mob mob) {
        //? if neoforge && >=1.21.10 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                mob, level, mob.getX(), mob.getY(), mob.getZ(), level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.EntitySpawnReason.NATURAL, null, null));
        //?} elif neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent(
                mob, level, mob.getX(), mob.getY(), mob.getZ(), level.getCurrentDifficultyAt(mob.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.NATURAL, null, null));*/
        //?} else {
        /*buildcraft.platform.fabric.FabricGameHooks.fireFinalizeSpawn(mob, false);*/
        //?}
    }

    /**
     * Calls {@code listener} with each vanilla game event (its type, position and cause) until the returned handle is
     * closed (NeoForge {@code VanillaGameEvent}; on Fabric the game-test mod's {@code ServerLevelGameEventMixin}, at
     * the same point in {@code ServerLevel.gameEvent}).
     */
    public static Observer observeGameEvents(GameEventListener listener) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.VanillaGameEvent> neo =
                event -> listener.onGameEvent(event.getVanillaEvent(), event.getEventPosition(), event.getCause());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.VanillaGameEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*FabricGameEvents.OPEN.add(listener);
        return () -> FabricGameEvents.OPEN.remove(listener);*/
        //?}
    }

    /** A chunk sent to a player (NeoForge {@code ChunkWatchEvent.Sent}; Fabric {@code FabricGameHooks.fireChunkSent},
     *  where its {@code PlayerChunkSenderMixin} posts it). */
    public static void chunkSent(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.chunk.LevelChunk chunk,
            ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent(player, chunk, level));
        //?} else {
        /*buildcraft.platform.fabric.FabricGameHooks.fireChunkSent(level, player, chunk.getPos());*/
        //?}
    }

    /**
     * A claim on attacks: {@code claim} is asked about each player attack on an entity until the returned handle is
     * closed, and refuses it by answering true (NeoForge {@code AttackEntityEvent} cancelled; Fabric
     * {@code AttackEntityCallback} answering FAIL, through one callback registered once, as Fabric events cannot be
     * unregistered).
     */
    public static Observer claimAttacks(java.util.function.BiPredicate<net.minecraft.world.entity.player.Player, Entity> claim) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.AttackEntityEvent> neo = event -> {
            if (claim.test(event.getEntity(), event.getTarget())) {
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.AttackEntityEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*FabricAttackClaims.OPEN.add(claim);
        return () -> FabricAttackClaims.OPEN.remove(claim);*/
        //?}
    }

    /** How an entity-interaction claim ({@link #claimEntityInteractions}) answers a click. */
    public enum InteractAnswer {
        /** Lets the click through. */
        ALLOW,
        /** Refuses it: NeoForge's cancelled event with its default PASS result, Fabric's FAIL. */
        REFUSE,
        /** Refuses it leaving a consuming result: NeoForge's cancelled event answering CONSUME, a Fabric callback
         *  answering CONSUME (one that took the click itself). */
        REFUSE_CONSUMING
    }

    /**
     * A claim on players' right-clicks on entities, at {@code order} (NeoForge {@code PlayerInteractEvent.EntityInteract}
     * at the order's priority; Fabric {@code UseEntityCallback} in the order's phase, which BuildCraft's
     * {@code PlayerMixin} also asks for a fake player's click, where NeoForge posts its event).
     */
    public static Observer claimEntityInteractions(HookOrder order,
            java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Entity, InteractAnswer> claim) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract> neo = event -> {
            InteractAnswer answer = claim.apply(event.getEntity(), event.getTarget());
            if (answer != InteractAnswer.ALLOW) {
                event.setCanceled(true);
                if (answer == InteractAnswer.REFUSE_CONSUMING) {
                    event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
                }
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                buildcraft.platform.neoforge.NeoForgeGameHooks.priority(order),
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*return FabricInteractClaims.open(order, claim);*/
        //?}
    }

    /**
     * A claim on entities joining a level, at {@code order}: {@code claim} answering true keeps the entity out (NeoForge
     * {@code EntityJoinLevelEvent} cancelled at the order's priority; Fabric {@code ServerEntityEvents.ALLOW_LOAD}
     * answering false in the order's phase).
     */
    public static Observer claimEntityJoins(HookOrder order, java.util.function.Predicate<Entity> claim) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> neo = event -> {
            if (claim.test(event.getEntity())) {
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                buildcraft.platform.neoforge.NeoForgeGameHooks.priority(order),
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*return FabricJoinClaims.open(order, claim);*/
        //?}
    }

    /**
     * A claim on block placements: {@code claim} is asked about each placement by an entity at a position and refuses
     * it by answering true (NeoForge {@code BlockEvent.EntityPlaceEvent} cancelled; on Fabric a Common Protection API
     * provider, which is how a Fabric claim mod answers placements: asked by BuildCraft's placement probe and by the
     * blocks a machine's item use placed, once per cell).
     */
    public static Observer claimPlacements(java.util.function.BiPredicate<Entity, net.minecraft.core.BlockPos> claim) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> neo = event -> {
            if (claim.test(event.getEntity(), event.getPos())) {
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "buildcraftunofficial_gametest", "placement_claim_" + FabricPlacementClaims.IDS.incrementAndGet());
        eu.pb4.common.protection.api.CommonProtection.register(id, new FabricPlacementClaims(claim));
        return () -> eu.pb4.common.protection.api.CommonProtection.remove(id);*/
        //?}
    }

    /**
     * Calls {@code listener} with each player's right-click on a block (NeoForge {@code PlayerInteractEvent.RightClickBlock};
     * Fabric {@code UseBlockCallback}, answering PASS).
     */
    public static Observer observeRightClickBlocks(
            java.util.function.BiConsumer<net.minecraft.world.entity.player.Player, net.minecraft.core.BlockPos> listener) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> neo =
                event -> listener.accept(event.getEntity(), event.getPos());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*FabricRightClicks.OPEN.add(listener);
        return () -> FabricRightClicks.OPEN.remove(listener);*/
        //?}
    }

    /**
     * Calls {@code listener} with each dying entity and what killed it as its drops are collected (NeoForge
     * {@code LivingDropsEvent}; Fabric BuildCraft's {@code GameHooks.onLivingDrops}, fed by its
     * {@code LivingEntityMixin}).
     */
    public static Observer observeLivingDrops(java.util.function.BiConsumer<LivingEntity, DamageSource> listener) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDropsEvent> neo =
                event -> listener.accept(event.getEntity(), event.getSource());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*FabricLivingDrops.OPEN.add(listener);
        return () -> FabricLivingDrops.OPEN.remove(listener);*/
        //?}
    }

    /**
     * Calls {@code listener} with each player whose dig speed is worked out and the block dug (null when asked
     * without one): BuildCraft's {@code GameHooks.onBreakSpeed} on both loaders, leaving the speed as it is.
     */
    public static Observer observeBreakSpeeds(
            java.util.function.BiConsumer<net.minecraft.world.entity.player.Player, net.minecraft.core.BlockPos> listener) {
        BreakSpeeds.OPEN.add(listener);
        return () -> BreakSpeeds.OPEN.remove(listener);
    }

    /** The open break-speed observers: one {@code GameHooks.onBreakSpeed} listener, registered on first use. */
    private static final class BreakSpeeds {
        static final java.util.List<java.util.function.BiConsumer<net.minecraft.world.entity.player.Player,
                net.minecraft.core.BlockPos>> OPEN = new java.util.concurrent.CopyOnWriteArrayList<>();

        static {
            buildcraft.platform.Platform.gameHooks().onBreakSpeed((player, state, pos, speed) -> {
                for (java.util.function.BiConsumer<net.minecraft.world.entity.player.Player, net.minecraft.core.BlockPos> each : OPEN) {
                    each.accept(player, pos);
                }
                return speed;
            });
        }
    }

    //? if fabric {
    /*/^* The open entity-interaction claims on Fabric: one UseEntityCallback per order, in its phase. *^/
    private static final class FabricInteractClaims {
        private static final java.util.Map<HookOrder, java.util.List<java.util.function.BiFunction<
                net.minecraft.world.entity.player.Player, Entity, InteractAnswer>>> OPEN = new java.util.EnumMap<>(HookOrder.class);

        static {
            for (HookOrder order : HookOrder.values()) {
                java.util.List<java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Entity, InteractAnswer>> open =
                        new java.util.concurrent.CopyOnWriteArrayList<>();
                OPEN.put(order, open);
                buildcraft.platform.fabric.FabricGameHooks.register(net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT,
                        order, (player, level, hand, entity, hit) -> {
                            InteractAnswer answer = InteractAnswer.ALLOW;
                            for (java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Entity, InteractAnswer> claim : open) {
                                InteractAnswer each = claim.apply(player, entity);
                                if (each.ordinal() > answer.ordinal()) {
                                    answer = each;
                                }
                            }
                            return switch (answer) {
                                case ALLOW -> net.minecraft.world.InteractionResult.PASS;
                                case REFUSE -> net.minecraft.world.InteractionResult.FAIL;
                                case REFUSE_CONSUMING -> net.minecraft.world.InteractionResult.CONSUME;
                            };
                        });
            }
        }

        static Observer open(HookOrder order,
                java.util.function.BiFunction<net.minecraft.world.entity.player.Player, Entity, InteractAnswer> claim) {
            OPEN.get(order).add(claim);
            return () -> OPEN.get(order).remove(claim);
        }
    }

    /^* The open entity-join claims on Fabric: one ALLOW_LOAD callback per order, in its phase. *^/
    private static final class FabricJoinClaims {
        private static final java.util.Map<HookOrder, java.util.List<java.util.function.Predicate<Entity>>> OPEN =
                new java.util.EnumMap<>(HookOrder.class);

        static {
            for (HookOrder order : HookOrder.values()) {
                java.util.List<java.util.function.Predicate<Entity>> open = new java.util.concurrent.CopyOnWriteArrayList<>();
                OPEN.put(order, open);
                buildcraft.platform.fabric.FabricGameHooks.register(
                        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD, order,
                        (entity, level, reason, loadedFromDisk) -> {
                            boolean refused = false;
                            for (java.util.function.Predicate<Entity> claim : open) {
                                refused |= claim.test(entity);
                            }
                            return !refused;
                        });
            }
        }

        static Observer open(HookOrder order, java.util.function.Predicate<Entity> claim) {
            OPEN.get(order).add(claim);
            return () -> OPEN.get(order).remove(claim);
        }
    }

    /^* One placement claim on Fabric: a Common Protection API provider that refuses only placements. *^/
    private static final class FabricPlacementClaims implements eu.pb4.common.protection.api.ProtectionProvider {
        static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.function.BiPredicate<Entity, net.minecraft.core.BlockPos> claim;

        FabricPlacementClaims(java.util.function.BiPredicate<Entity, net.minecraft.core.BlockPos> claim) {
            this.claim = claim;
        }

        @Override
        public boolean isProtected(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            return false;
        }

        @Override
        public boolean isAreaProtected(net.minecraft.world.level.Level level, net.minecraft.world.phys.AABB area) {
            return false;
        }

        @Override
        public boolean canPlaceBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
            return !claim.test(player, pos);
        }
    }

    /^* The open right-click-block observers on Fabric: one UseBlockCallback, answering PASS. *^/
    private static final class FabricRightClicks {
        static final java.util.List<java.util.function.BiConsumer<net.minecraft.world.entity.player.Player,
                net.minecraft.core.BlockPos>> OPEN = new java.util.concurrent.CopyOnWriteArrayList<>();

        static {
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
                for (java.util.function.BiConsumer<net.minecraft.world.entity.player.Player, net.minecraft.core.BlockPos> listener : OPEN) {
                    listener.accept(player, hit.getBlockPos());
                }
                return net.minecraft.world.InteractionResult.PASS;
            });
        }
    }

    /^* The open drop observers on Fabric: one GameHooks.onLivingDrops listener. *^/
    private static final class FabricLivingDrops {
        static final java.util.List<java.util.function.BiConsumer<LivingEntity, DamageSource>> OPEN =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        static {
            buildcraft.platform.Platform.gameHooks().onLivingDrops((entity, source, recentlyHit, drops) -> {
                for (java.util.function.BiConsumer<LivingEntity, DamageSource> listener : OPEN) {
                    listener.accept(entity, source);
                }
            });
        }
    }

    /^* The game-event observers open on Fabric; the game-test mod's {@code ServerLevelGameEventMixin} tells them. *^/
    public static final class FabricGameEvents {
        static final java.util.List<GameEventListener> OPEN = new java.util.concurrent.CopyOnWriteArrayList<>();

        private FabricGameEvents() {}

        public static void post(Holder<GameEvent> event, Vec3 position, @Nullable Entity cause) {
            for (GameEventListener listener : OPEN) {
                listener.onGameEvent(event, position, cause);
            }
        }
    }

    private static final class FabricAttackClaims {
        static final java.util.List<java.util.function.BiPredicate<net.minecraft.world.entity.player.Player, Entity>> OPEN =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        static {
            net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
                boolean refused = false;
                for (java.util.function.BiPredicate<net.minecraft.world.entity.player.Player, Entity> claim : OPEN) {
                    refused |= claim.test(player, entity);
                }
                return refused ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS;
            });
        }
    }*/
    //?}

    /** A registered observer; closing it unregisters it. */
    public interface Observer extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface GameEventListener {
        void onGameEvent(Holder<GameEvent> event, Vec3 position, @Nullable Entity cause);
    }
}
