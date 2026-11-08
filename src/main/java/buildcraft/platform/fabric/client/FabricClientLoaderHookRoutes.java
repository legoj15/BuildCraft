/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.world.level.block.entity.BlockEntity;
//? if <1.21.10 {
/^import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.fabricmc.fabric.api.event.client.player.ClientPickBlockGatherCallback;

import buildcraft.platform.hook.PickStackBlock;
^///?}

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import buildcraft.platform.fabric.FabricLoaderHookRoutes;
import buildcraft.platform.hook.LevelLifecycleBlockEntity;

/^*
 * The client half of {@link FabricLoaderHookRoutes}: a block entity joining the client level, or its chunk unloading
 * there (NeoForge's onLoad and onChunkUnloaded run on both sides).
 *^/
public final class FabricClientLoaderHookRoutes {

    private static final Queue<BlockEntity> CLIENT_JOINED = new ConcurrentLinkedQueue<>();

    private FabricClientLoaderHookRoutes() {}

    /^* Registers the client listeners; the Fabric client entrypoint calls it once, before BuildCraft's client init. *^/
    public static void register() {
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof LevelLifecycleBlockEntity) {
                CLIENT_JOINED.add(blockEntity);
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> FabricLoaderHookRoutes.runJoined(CLIENT_JOINED));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> FabricLoaderHookRoutes.chunkUnloading(chunk));
        //? if <1.21.10 {
        /^// The pick is the client's before 1.21.10 (NeoForge's player-aware getCloneItemStack runs there too); an
        // empty stack lets vanilla's own pick run.
        ClientPickBlockGatherCallback.EVENT.register((player, result) -> {
            if (result.getType() != HitResult.Type.BLOCK || !(result instanceof BlockHitResult hit)) {
                return ItemStack.EMPTY;
            }
            if (!(player.level().getBlockState(hit.getBlockPos()).getBlock() instanceof PickStackBlock block)) {
                return ItemStack.EMPTY;
            }
            ItemStack picked = block.pickedStack(player.level(), hit.getBlockPos(), player);
            return picked == null ? ItemStack.EMPTY : picked;
        });
        ^///?}
    }
}
*///?}
