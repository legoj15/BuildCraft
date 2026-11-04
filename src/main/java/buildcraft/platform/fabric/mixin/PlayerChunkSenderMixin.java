/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.platform.fabric.FabricGameHooks;

/^* NeoForge's {@code ChunkWatchEvent.Sent} on Fabric ({@link FabricGameHooks#onChunkSent}): after the chunk's packet. *^/
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {

    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void buildcraft$chunkSent(ServerGamePacketListenerImpl connection, ServerLevel level,
                                             LevelChunk chunk, CallbackInfo ci) {
        FabricGameHooks.fireChunkSent(level, connection.player, chunk.getPos());
    }
}
*///?}
