/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import buildcraft.lib.net.MessageEnergyOverlay;

public class MessageUtil {

    /**
     * Pushes a block entity's update packet ({@link BlockEntity#getUpdatePacket()}) to every
     * player tracking its chunk, without going through {@code Level.sendBlockUpdated()}.
     *
     * <p>{@code sendBlockUpdated} is a block-change broadcast — on the client it runs
     * {@code LevelRenderer.blockChanged}, which marks the chunk section dirty and re-meshes it.
     * A tile that only needs to sync frequently-changing data (a fluid amount, a drill length),
     * and whose dynamic visuals are drawn by a per-frame {@code BlockEntityRenderer} rather than
     * baked chunk geometry, must NOT use it: a tank syncing every tick would re-mesh its whole
     * section 20x/second — unnoticeable in a flat world, a severe FPS cost in terrain-dense
     * chunks. This sends only the {@code ClientboundBlockEntityDataPacket}, which the client
     * applies via {@code onDataPacket} with no re-mesh.
     */
    public static void sendUpdateToTrackingPlayers(BlockEntity be) {
        if (be.getLevel() instanceof ServerLevel level) {
            Packet<?> packet = be.getUpdatePacket();
            if (packet == null) {
                return;
            }
            ChunkPos chunkPos = PositionUtil.chunkContaining(be.getBlockPos());
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunkPos, false)) {
                player.connection.send(packet);
            }
        }
    }

    /**
     * Show a transient action-bar (overlay) message to a player. 26.1 renamed the base
     * {@code displayClientMessage(msg, true)} to {@code sendOverlayMessage(msg)}; this isolates
     * that cliff so call sites stay version-agnostic.
     */
    public static void sendOverlayMessage(Player player, Component message) {
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);*/
        //?}
    }

    /**
     * Show a player an action-bar message that names an energy figure, formatted by THEIR client. The figure's unit
     * label, number format and language all come from client-side display settings the server cannot read, so the
     * server ships the raw amount ({@link MessageEnergyOverlay}) and the client builds the text. Never format such a
     * figure with {@code LocaleUtil} here and pass the string to {@link #sendOverlayMessage}: on a dedicated server
     * it would be baked in the server's defaults and language. The payload does carry THIS (server) JVM's
     * {@code powerMode} and MJ⇄RF ratio: those are COMMON config the server's machines really convert at, and a
     * remote client's own copy is never synced to them.
     *
     * @param key    base translation key of the message, taking the formatted figure as its one {@code %s}
     * @param kind   which unit family {@code amount} is in
     * @param amount the figure ({@link MessageEnergyOverlay.Kind} says in what unit)
     */
    public static void sendEnergyOverlay(Player player, String key, MessageEnergyOverlay.Kind kind, long amount) {
        MessageEnergyOverlay overlay = MessageEnergyOverlay.of(key, kind, amount);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, overlay);
        } else {
            // A client-side player: there is no server to hop through, and this JVM is the one that formats.
            sendOverlayMessage(player, overlay.resolve());
        }
    }

    /**
     * Send a system (chat) message to a player. 26.1 {@code sendSystemMessage(msg)} ⇄ 1.21.11
     * {@code displayClientMessage(msg, false)} (false = chat line, not the action-bar overlay).
     */
    public static void sendSystemMessage(Player player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);*/
        //?}
    }
}
