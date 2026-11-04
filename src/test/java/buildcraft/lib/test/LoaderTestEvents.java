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

/**
 * The loader events a game test fires itself, so the wiring from the loader's event to BuildCraft's listener is under
 * test, not just the listener: each posts the event the running loader would post (NeoForge's bus, or the Fabric API
 * event's invoker), so every listener the loader holds runs, BuildCraft's {@code Platform} hooks included.
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
     * A level of either side unloading. Fabric has no client level-unload event (FabricClientHooks.onClientLevelUnload
     * hooks the level change and the disconnect instead, with no level to name), so a client level throws there.
     */
    public static void levelUnload(net.minecraft.world.level.Level level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
        //?} else {
        /*if (!(level instanceof ServerLevel server)) {
            throw new UnsupportedOperationException("Fabric has no client level-unload event to post for " + level);
        }
        levelUnload(server);*/
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
     * A dying entity's drops being collected into {@code drops} (NeoForge {@code LivingDropsEvent}). Fabric's
     * route is {@code ServerLivingEntityEvents.AFTER_DEATH}, which puts the drops straight in the level, so there is
     * no list to post there; a test that needs one is not in the Fabric set.
     */
    public static void livingDrops(LivingEntity victim, DamageSource source, Collection<ItemEntity> drops,
            boolean recentlyHit) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(victim, source, drops, recentlyHit));
        //?} else {
        /*throw new UnsupportedOperationException("Fabric has no drops-list event to post");*/
        //?}
    }

    /** A mob's spawn being finalized (NeoForge {@code FinalizeSpawnEvent}); Fabric API has no such event. */
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
        /*throw new UnsupportedOperationException("Fabric API has no spawn-finalize event");*/
        //?}
    }

    /**
     * Calls {@code listener} with each vanilla game event (its type, position and cause) until the returned handle is
     * closed (NeoForge {@code VanillaGameEvent}). Fabric API has no game-event hook.
     */
    public static Observer observeGameEvents(GameEventListener listener) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.VanillaGameEvent> neo =
                event -> listener.onGameEvent(event.getVanillaEvent(), event.getEventPosition(), event.getCause());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.VanillaGameEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} else {
        /*throw new UnsupportedOperationException("Fabric API has no game-event hook");*/
        //?}
    }

    /** A chunk sent to a player (NeoForge {@code ChunkWatchEvent.Sent}); Fabric API has no chunk-sent event. */
    public static void chunkSent(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.chunk.LevelChunk chunk,
            ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent(player, chunk, level));
        //?} else {
        /*throw new UnsupportedOperationException("Fabric API has no chunk-sent event");*/
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

    //? if fabric {
    /*private static final class FabricAttackClaims {
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
