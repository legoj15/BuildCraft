package buildcraft.lib.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

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
}
