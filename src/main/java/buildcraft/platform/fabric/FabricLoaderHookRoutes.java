/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.PlayerPickItemEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.util.TriState;

import buildcraft.platform.event.HookOrder;
import buildcraft.platform.hook.EnchantmentFilterItem;
import buildcraft.platform.hook.LevelLifecycleBlockEntity;
import buildcraft.platform.hook.LevelRemovalEntity;
import buildcraft.platform.hook.PickStackBlock;
import buildcraft.platform.hook.PlayerBreakGuard;
import buildcraft.platform.hook.SneakBypassItem;
import buildcraft.platform.hook.UseFirstItem;

/^*
 * The Fabric routes to BuildCraft's patched-vanilla overrides ({@code buildcraft.platform.LoaderHook}) that Fabric API
 * has an event for: each listener finds the {@code buildcraft.platform.hook} interface on the block, item or block
 * entity and calls the same common method the NeoForge override delegates to. The overrides with no event go through
 * the mixins in {@code buildcraft.platform.fabric.mixin}; the client half is {@code FabricClientLoaderHookRoutes}.
 *
 * <p>Gaps against NeoForge: the player-break guard runs on the server only and before {@code playerWillDestroy} (the
 * client predicts the break until the server restores the block); a chunk-unload hook runs before the chunk is saved
 * rather than after.
 *^/
public final class FabricLoaderHookRoutes {

    /^* Block entities that joined a server level and have not been told yet: NeoForge runs onLoad at the head of the
     *  level's next block-entity tick, never from inside the chunk load that placed it, so Fabric's load event only
     *  queues and {@code LevelMixin} drains a level's entries where NeoForge would ({@link #levelTicksBlockEntities}). *^/
    private static final Queue<BlockEntity> SERVER_JOINED = new ConcurrentLinkedQueue<>();

    private FabricLoaderHookRoutes() {}

    /^* Registers every listener; the Fabric entrypoint calls it once, before BuildCraft's init. *^/
    public static void register() {
        // NeoForge's onItemUseFirst: after the right-click event (every default-phase callback, a claim mod's
        // included, answers first, as NeoForge posts RightClickBlock before it), and never for a machine's ask
        // (NeoForge's ask posts the event alone).
        FabricGameHooks.register(UseBlockCallback.EVENT, HookOrder.LATE, (player, level, hand, hit) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isSpectator() && !FabricProtection.isClickAsk(player)
                    && stack.getItem() instanceof UseFirstItem item) {
                return item.useBeforeBlock(stack, new UseOnContext(player, hand, hit));
            }
            return InteractionResult.PASS;
        });
        // NeoForge's onDestroyedByPlayer, which a machine's break probe (NeoForge's break event alone) never reaches.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
            FabricProtection.isBreakProbe(level, pos, player)
                || !(state.getBlock() instanceof PlayerBreakGuard guard
                    && guard.keepsBlockOnPlayerBreak(state, level, pos, player)));
        PlayerPickItemEvents.BLOCK.register((player, pos, state, includeData) ->
            state.getBlock() instanceof PickStackBlock block ? block.pickedStack(player.level(), pos, player) : null);
        EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, stack, context) -> {
            if (!(stack.getItem() instanceof EnchantmentFilterItem item)) {
                return TriState.DEFAULT;
            }
            if (!item.acceptsEnchantment(stack, enchantment)) {
                return TriState.FALSE;
            }
            // NeoForge's primary-item check (the enchanting table) still wants the enchantment's primary items.
            return context == EnchantingContext.ACCEPTABLE ? TriState.TRUE : TriState.DEFAULT;
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof LevelLifecycleBlockEntity) {
                SERVER_JOINED.add(blockEntity);
            }
        });
        // A level that unloads never ticks again: its queued entries would pin it (and its server) until a later one.
        ServerLevelEvents.UNLOAD.register((server, level) -> SERVER_JOINED.removeIf(be -> be.getLevel() == level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SERVER_JOINED.clear());
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> chunkUnloading(chunk));
        // NeoForge's onRemovedFromLevel, server side (BuildCraft's only user, the robot, deregisters there alone).
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof LevelRemovalEntity removal) {
                removal.onLeftLevel();
            }
        });
        // NeoForge's IEntityWithComplexSpawn: the extra spawn data follows the spawn packet as a payload.
        FabricSpawnData.register();
    }

    /^*
     * Called by {@code LevelMixin} at the head of a server level's {@code tickBlockEntities}, where NeoForge runs its
     * fresh block entities' onLoad: tells each block entity queued for {@code level} that it joined it, before any
     * block entity of that tick ticks.
     *^/
    public static void levelTicksBlockEntities(Level level) {
        if (SERVER_JOINED.isEmpty()) {
            return;
        }
        Queue<BlockEntity> due = new java.util.ArrayDeque<>();
        for (java.util.Iterator<BlockEntity> it = SERVER_JOINED.iterator(); it.hasNext();) {
            BlockEntity blockEntity = it.next();
            if (blockEntity.getLevel() == level) {
                it.remove();
                due.add(blockEntity);
            }
        }
        runJoined(due);
    }

    /^* Tells each queued block entity still in its level that it joined it (NeoForge's onLoad). *^/
    public static void runJoined(Queue<BlockEntity> joined) {
        BlockEntity blockEntity;
        while ((blockEntity = joined.poll()) != null) {
            if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                ((LevelLifecycleBlockEntity) blockEntity).onJoinedLevel();
            }
        }
    }

    /^* Tells every lifecycle block entity in {@code chunk} that the chunk unloads (NeoForge's onChunkUnloaded). *^/
    public static void chunkUnloading(LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof LevelLifecycleBlockEntity lifecycle) {
                lifecycle.onChunkUnload();
            }
        }
    }

    /^*
     * Whether a sneaking player's click still reaches the block (NeoForge's doesSneakBypassUse on both hands): every
     * hand is empty or holds a {@link SneakBypassItem} that says so. Called by the game-mode mixins.
     *^/
    public static boolean sneakBypassesUse(Player player, LevelReader level, BlockPos pos) {
        return bypasses(player.getMainHandItem(), level, pos, player) && bypasses(player.getOffhandItem(), level, pos, player);
    }

    private static boolean bypasses(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return stack.isEmpty()
            || stack.getItem() instanceof SneakBypassItem item && item.sneakBypassesUse(stack, level, pos, player);
    }
}
*///?}
