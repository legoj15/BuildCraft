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
 * test, not just the listener: each posts the event the running loader would post (NeoForge's or Forge's bus, or the
 * Fabric API event's invoker), so every listener the loader holds runs, BuildCraft's {@code Platform} hooks included. The
 * {@code claim...} and {@code observe...} methods stand up a claim mod or an observer on the running loader's own
 * event for as long as the returned handle is open; a {@link HookOrder} is the claim's place among the listeners
 * (NeoForge's or Forge's priority, or the Fabric event phase BuildCraft's own hooks use for it). Fabric events cannot
 * be unregistered, so each Fabric claim kind is one callback registered once (per order), handing the click to the open
 * claims. Forge's events go through BuildCraft's {@code ForgeBus}, which is EventBus 7 on Forge for 26.x and EventBus 6
 * on Forge 1.21.1: a claim refuses by answering true, and is removed again through the handle the bus returned.
 */
public final class LoaderTestEvents {

    private LoaderTestEvents() {}

    /** A server level unloading (NeoForge {@code LevelEvent.Unload}, Fabric {@code ServerLevelEvents.UNLOAD}, before
     *  26.1 {@code ServerWorldEvents.UNLOAD}). */
    public static void levelUnload(ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
        //?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.invoker().onLevelUnload(level.getServer(), level);*/
        //?} elif fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.invoker().onWorldUnload(level.getServer(), level);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(new net.minecraftforge.event.level.LevelEvent.Unload(level));*/
        //?}
    }

    /**
     * A level of either side unloading (NeoForge {@code LevelEvent.Unload}; on Fabric a client level goes through
     * {@code FabricGameHooks.fireClientLevelUnload}, where its {@code MinecraftMixin} posts it).
     */
    public static void levelUnload(net.minecraft.world.level.Level level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Unload(level));
        //?} elif fabric {
        /*if (!level.isClientSide() && level instanceof ServerLevel server) {
            levelUnload(server);
        } else {
            buildcraft.platform.fabric.FabricGameHooks.fireClientLevelUnload(level);
        }*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(new net.minecraftforge.event.level.LevelEvent.Unload(level));*/
        //?}
    }

    /**
     * A survival player's break of the block at {@code pos} reaching the point where the loader asks the block whether
     * it goes (NeoForge's and Forge's {@code onDestroyedByPlayer}, which also removes the block when it answers true; Fabric's
     * {@code PlayerBlockBreakEvents.BEFORE}, every callback, BuildCraft's player-break guard among them): whether the
     * break goes ahead.
     */
    public static boolean playerBreakGoesAhead(ServerLevel level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        //? if neoforge && >=1.21.10 {
        return state.getBlock().onDestroyedByPlayer(state, level, pos, player, player.getMainHandItem(), false,
                level.getFluidState(pos));
        //?} elif neoforge || forge {
        /*return state.getBlock().onDestroyedByPlayer(state, level, pos, player, false, level.getFluidState(pos));*/
        //?} elif fabric {
        /*return net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));*/
        //?}
    }

    /**
     * A server level saved (NeoForge's and Forge's {@code LevelEvent.Save} for that level; Fabric has only the server-wide
     * {@code ServerLifecycleEvents.AFTER_SAVE}, which BuildCraft's save hook turns into one call per level).
     */
    public static void levelSave(ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Save(level));
        //?} elif fabric {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.AFTER_SAVE.invoker()
                .onAfterSave(level.getServer(), false, false);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(new net.minecraftforge.event.level.LevelEvent.Save(level));*/
        //?}
    }

    /**
     * An entity joining a level, newly spawned (NeoForge's and Forge's {@code EntityJoinLevelEvent}, Fabric
     * {@code ServerEntityEvents.ALLOW_LOAD}, before 26.1 BuildCraft's {@code FabricGameHooks.ENTITY_ALLOW_JOIN}; the
     * answer is ignored here).
     */
    public static void entityJoin(Entity entity, ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(entity, level));
        //?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD.invoker()
                .onAllowLoad(entity, level, null, false);*/
        //?} elif fabric {
        /*buildcraft.platform.fabric.FabricGameHooks.fireEntityJoin(entity, level, false);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(new net.minecraftforge.event.entity.EntityJoinLevelEvent(entity, level));*/
        //?}
    }

    /**
     * A dying entity's drops being collected into {@code drops} (NeoForge's and Forge's {@code LivingDropsEvent}; Fabric
     * {@code FabricGameHooks.fireLivingDrops}, where its {@code LivingEntityMixin} posts it).
     */
    public static void livingDrops(LivingEntity victim, DamageSource source, Collection<ItemEntity> drops,
            boolean recentlyHit) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(victim, source, drops, recentlyHit));
        //?} elif fabric {
        /*buildcraft.platform.fabric.FabricGameHooks.fireLivingDrops(victim, source, recentlyHit,
                drops instanceof java.util.List<ItemEntity> list ? list : new java.util.ArrayList<>(drops));*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(
                new net.minecraftforge.event.entity.living.LivingDropsEvent(victim, source, drops, recentlyHit));*/
        //?}
    }

    /**
     * A mob's spawn being finalized, as a natural spawn (NeoForge {@code FinalizeSpawnEvent}, Forge
     * {@code MobSpawnEvent.FinalizeSpawn}; Fabric
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
        //?} elif fabric {
        /*buildcraft.platform.fabric.FabricGameHooks.fireFinalizeSpawn(mob, false);*/
        //?} elif forge && >=1.21.10 {
        /*buildcraft.platform.forge.ForgeBus.post(
                new net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn(
                        mob, level, mob.getX(), mob.getY(), mob.getZ(), level.getCurrentDifficultyAt(mob.blockPosition()),
                        net.minecraft.world.entity.EntitySpawnReason.NATURAL, null, null, null));*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(
                new net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn(
                        mob, level, mob.getX(), mob.getY(), mob.getZ(), level.getCurrentDifficultyAt(mob.blockPosition()),
                        net.minecraft.world.entity.MobSpawnType.NATURAL, null, null, null));*/
        //?}
    }

    /**
     * Calls {@code listener} with each vanilla game event (its type, position and cause) until the returned handle is
     * closed (NeoForge's and Forge's {@code VanillaGameEvent}; on Fabric the game-test mod's {@code ServerLevelGameEventMixin}, at
     * the same point in {@code ServerLevel.gameEvent}).
     */
    public static Observer observeGameEvents(GameEventListener listener) {
        //? if neoforge {
        java.util.function.Consumer<net.neoforged.neoforge.event.VanillaGameEvent> neo =
                event -> listener.onGameEvent(event.getVanillaEvent(), event.getEventPosition(), event.getCause());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.VanillaGameEvent.class, neo);
        return () -> net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(neo);
        //?} elif fabric {
        /*FabricGameEvents.OPEN.add(listener);
        return () -> FabricGameEvents.OPEN.remove(listener);*/
        //?} elif forge {
        /*// Forge's event hands over the vanilla event itself, not its holder.
        buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.listenRemovable(
                net.minecraftforge.event.VanillaGameEvent.class,
                event -> listener.onGameEvent(
                        net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.wrapAsHolder(event.getVanillaEvent()),
                        event.getEventPosition(), event.getCause()));
        return registered::remove;*/
        //?}
    }

    /** A chunk sent to a player (NeoForge {@code ChunkWatchEvent.Sent}, Forge {@code ChunkWatchEvent.Watch}; Fabric {@code FabricGameHooks.fireChunkSent},
     *  where its {@code PlayerChunkSenderMixin} posts it). */
    public static void chunkSent(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.chunk.LevelChunk chunk,
            ServerLevel level) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.ChunkWatchEvent.Sent(player, chunk, level));
        //?} elif fabric {
        /*buildcraft.platform.fabric.FabricGameHooks.fireChunkSent(level, player, chunk.getPos());*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.post(new net.minecraftforge.event.level.ChunkWatchEvent.Watch(player, chunk, level));*/
        //?}
    }

    /**
     * A claim on attacks: {@code claim} is asked about each player attack on an entity until the returned handle is
     * closed, and refuses it by answering true (NeoForge's and Forge's {@code AttackEntityEvent} cancelled; Fabric
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
        //?} elif fabric {
        /*FabricAttackClaims.OPEN.add(claim);
        return () -> FabricAttackClaims.OPEN.remove(claim);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.entity.player.AttackEntityEvent.class,
                event -> claim.test(event.getEntity(), event.getTarget()));
        return registered::remove;*/
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
     * and Forge {@code EntityInteractSpecific} (on 1.21.1 {@code EntityInteract}, as NeoForge) at the order's priority; Fabric {@code UseEntityCallback} in the order's phase, which BuildCraft's
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
        //?} elif fabric {
        /*return FabricInteractClaims.open(order, claim);*/
        //?} elif forge {
        /*// Forge 26.3's one entity-click event is EntityInteractSpecific, posted from the same vanilla call as NeoForge's;
        // Forge 1.21.1 and 1.21.11 post EntityInteract from NeoForge 1.21.1's point (ForgeGameHooks#onEntityInteract has the
        // same split).
        //? if >=26.1 {
        Class<net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific> type =
                net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.class;
        //?} else {
        /^Class<net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract> type =
                net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract.class;
        ^///?}
        buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.refuseRemovable(type, order, event -> {
            InteractAnswer answer = claim.apply(event.getEntity(), event.getTarget());
            if (answer == InteractAnswer.ALLOW) {
                return false;
            }
            if (answer == InteractAnswer.REFUSE_CONSUMING) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            }
            return true;
        });
        return registered::remove;*/
        //?}
    }

    /**
     * A claim on entities joining a level, at {@code order}: {@code claim} answering true keeps the entity out (NeoForge
     * and Forge {@code EntityJoinLevelEvent} cancelled at the order's priority; Fabric {@code ServerEntityEvents.ALLOW_LOAD},
     * before 26.1 {@code FabricGameHooks.ENTITY_ALLOW_JOIN}, answering false in the order's phase).
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
        //?} elif fabric {
        /*return FabricJoinClaims.open(order, claim);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.entity.EntityJoinLevelEvent.class, order, event -> claim.test(event.getEntity()));
        return registered::remove;*/
        //?}
    }

    /**
     * A claim on block placements: {@code claim} is asked about each placement by an entity at a position and refuses
     * it by answering true (NeoForge's and Forge's {@code BlockEvent.EntityPlaceEvent} cancelled; on Fabric a Common Protection API
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
        //?} elif fabric {
        /*net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "buildcraftunofficial_gametest", "placement_claim_" + FabricPlacementClaims.IDS.incrementAndGet());
        eu.pb4.common.protection.api.CommonProtection.register(id, new FabricPlacementClaims(claim));
        return () -> eu.pb4.common.protection.api.CommonProtection.remove(id);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.Registration onSingle = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class,
                event -> claim.test(event.getEntity(), event.getPos()));
        // On EventBus 7 a multi-cell placement posts EntityMultiPlaceEvent, which has its own bus: listen on both. On
        // EventBus 6 (1.21.1) the parent's listener sees the subclass already.
        //? if >=1.21.10 {
        buildcraft.platform.forge.ForgeBus.Registration onMulti = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.level.BlockEvent.EntityMultiPlaceEvent.class,
                event -> claim.test(event.getEntity(), event.getPos()));
        return () -> {
            onSingle.remove();
            onMulti.remove();
        };
        //?} else {
        /^return onSingle::remove;
        ^///?}*/
        //?}
    }

    /**
     * A claim that answers the way most Fabric claim mods answer: through a Common Protection API provider alone, with
     * no Fabric API callback refusing anything, so a refusal it causes proves the provider half of BuildCraft's Fabric
     * asks. On NeoForge and Forge it is the events the same asks post. Each predicate refuses by answering true:
     * {@code breakRefused} a break at a position (NeoForge's break event; provider
     * {@code canBreakBlock}), {@code interactRefused} an interaction at a position (NeoForge
     * {@code PlayerInteractEvent.RightClickBlock} at the clicked block and {@code RightClickItem} at the block the
     * user stands in; provider {@code canInteractBlock}, which BuildCraft asks at those same positions),
     * {@code damageRefused} an attack on an entity (NeoForge {@code AttackEntityEvent}; provider
     * {@code canDamageEntity}).
     */
    public static Observer claimByProvider(java.util.function.Predicate<net.minecraft.core.BlockPos> breakRefused,
            java.util.function.Predicate<net.minecraft.core.BlockPos> interactRefused,
            java.util.function.Predicate<Entity> damageRefused) {
        //? if neoforge {
        net.neoforged.bus.api.IEventBus bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        // The break event's class moves between NeoForge lines; BuildCraft's own seam registers on whichever it is.
        buildcraft.platform.event.PlatformProtection.Registration onBreak =
                buildcraft.platform.Platform.protection().onBreak(event -> {
                    if (breakRefused.test(event.pos())) {
                        event.cancel();
                    }
                });
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock> onClick =
                event -> {
                    if (interactRefused.test(event.getPos())) {
                        event.setCanceled(true);
                    }
                };
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem> onUse =
                event -> {
                    if (interactRefused.test(event.getPos())) {
                        event.setCanceled(true);
                    }
                };
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.AttackEntityEvent> onAttack = event -> {
            if (damageRefused.test(event.getTarget())) {
                event.setCanceled(true);
            }
        };
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, onClick);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem.class, onUse);
        bus.addListener(net.neoforged.neoforge.event.entity.player.AttackEntityEvent.class, onAttack);
        return () -> {
            onBreak.close();
            bus.unregister(onClick);
            bus.unregister(onUse);
            bus.unregister(onAttack);
        };
        //?} elif fabric {
        /*net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                "buildcraftunofficial_gametest", "provider_claim_" + FabricProviderClaims.IDS.incrementAndGet());
        eu.pb4.common.protection.api.CommonProtection.register(id,
                new FabricProviderClaims(breakRefused, interactRefused, damageRefused));
        return () -> eu.pb4.common.protection.api.CommonProtection.remove(id);*/
        //?} elif forge {
        /*// Forge has no provider API: the events the same asks post, as on NeoForge.
        buildcraft.platform.event.PlatformProtection.Registration onBreak =
                buildcraft.platform.Platform.protection().onBreak(event -> {
                    if (breakRefused.test(event.pos())) {
                        event.cancel();
                    }
                });
        buildcraft.platform.forge.ForgeBus.Registration onClick = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                event -> interactRefused.test(event.getPos()));
        buildcraft.platform.forge.ForgeBus.Registration onUse = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem.class,
                event -> interactRefused.test(event.getPos()));
        buildcraft.platform.forge.ForgeBus.Registration onAttack = buildcraft.platform.forge.ForgeBus.refuseRemovable(
                net.minecraftforge.event.entity.player.AttackEntityEvent.class,
                event -> damageRefused.test(event.getTarget()));
        return () -> {
            onBreak.close();
            onClick.remove();
            onUse.remove();
            onAttack.remove();
        };*/
        //?}
    }

    /**
     * Calls {@code listener} with each player's right-click on a block (NeoForge's and Forge's {@code PlayerInteractEvent.RightClickBlock};
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
        //?} elif fabric {
        /*FabricRightClicks.OPEN.add(listener);
        return () -> FabricRightClicks.OPEN.remove(listener);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.listenRemovable(
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                event -> listener.accept(event.getEntity(), event.getPos()));
        return registered::remove;*/
        //?}
    }

    /**
     * Calls {@code listener} with each dying entity and what killed it as its drops are collected (NeoForge's and Forge's
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
        //?} elif fabric {
        /*FabricLivingDrops.OPEN.add(listener);
        return () -> FabricLivingDrops.OPEN.remove(listener);*/
        //?} elif forge {
        /*buildcraft.platform.forge.ForgeBus.Registration registered = buildcraft.platform.forge.ForgeBus.listenRemovable(
                net.minecraftforge.event.entity.living.LivingDropsEvent.class,
                event -> listener.accept(event.getEntity(), event.getSource()));
        return registered::remove;*/
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

    /^* The open entity-join claims on Fabric: one ALLOW_LOAD (before 26.1 ENTITY_ALLOW_JOIN) callback per order, in
     *  its phase. *^/
    private static final class FabricJoinClaims {
        private static final java.util.Map<HookOrder, java.util.List<java.util.function.Predicate<Entity>>> OPEN =
                new java.util.EnumMap<>(HookOrder.class);

        static {
            for (HookOrder order : HookOrder.values()) {
                java.util.List<java.util.function.Predicate<Entity>> open = new java.util.concurrent.CopyOnWriteArrayList<>();
                OPEN.put(order, open);
                //? if >=26.1 {
                buildcraft.platform.fabric.FabricGameHooks.register(
                        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ALLOW_LOAD, order,
                        (entity, level, reason, loadedFromDisk) -> allows(open, entity));
                //?} else {
                /^buildcraft.platform.fabric.FabricGameHooks.register(
                        buildcraft.platform.fabric.FabricGameHooks.ENTITY_ALLOW_JOIN, order,
                        (entity, level, loadedFromDisk) -> allows(open, entity));
                ^///?}
            }
        }

        // Every open claim is asked (each may record what it saw); any one refusing keeps the entity out.
        private static boolean allows(java.util.List<java.util.function.Predicate<Entity>> open, Entity entity) {
            boolean refused = false;
            for (java.util.function.Predicate<Entity> claim : open) {
                refused |= claim.test(entity);
            }
            return !refused;
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
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return !claim.test(player, pos);
        }
    }

    /^* One provider-only claim on Fabric: refuses breaks, block interactions and entity damage, never placements. *^/
    private record FabricProviderClaims(java.util.function.Predicate<net.minecraft.core.BlockPos> breakRefused,
            java.util.function.Predicate<net.minecraft.core.BlockPos> interactRefused,
            java.util.function.Predicate<Entity> damageRefused) implements eu.pb4.common.protection.api.ProtectionProvider {
        static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public boolean isProtected(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
            return false;
        }

        @Override
        public boolean isAreaProtected(net.minecraft.world.level.Level level, net.minecraft.world.phys.AABB area) {
            return false;
        }

        @Override
        public boolean canBreakBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return !breakRefused.test(pos);
        }

        @Override
        public boolean canInteractBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return !interactRefused.test(pos);
        }

        @Override
        public boolean canDamageEntity(net.minecraft.world.level.Level level, Entity entity,
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return !damageRefused.test(entity);
        }

        // The API's defaults for these fall back on the break and damage answers above; this claim refuses none of
        // them, as its NeoForge arm posts nothing for them.
        @Override
        public boolean canPlaceBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return true;
        }

        @Override
        public boolean canExplodeBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                //? if >=26.1 {
                net.minecraft.world.level.Explosion explosion, net.minecraft.server.players.NameAndId profile,
                //?} else {
                /^net.minecraft.world.level.Explosion explosion, com.mojang.authlib.GameProfile profile,
                ^///?}
                net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public boolean canInteractEntity(net.minecraft.world.level.Level level, Entity entity,
                //? if >=26.1 {
                net.minecraft.server.players.NameAndId profile, net.minecraft.world.entity.player.Player player) {
                //?} else {
                /^com.mojang.authlib.GameProfile profile, net.minecraft.world.entity.player.Player player) {
                ^///?}
            return true;
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
