package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.builders.tile.TileArchitectTable;

/**
 * Client -> server: request a transient live preview of the Architect Table's current scan area
 * (for showing in the GUI when no built blueprint is available yet). Keyed by the tile position;
 * the server replies with {@link ArchitectPreviewResponsePayload}.
 */
public record ArchitectPreviewRequestPayload(BlockPos architectPos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArchitectPreviewRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:architect_preview_request"));

    public static final StreamCodec<FriendlyByteBuf, ArchitectPreviewRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.architectPos()),
            buf -> new ArchitectPreviewRequestPayload(buf.readBlockPos())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArchitectPreviewRequestPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = context.player();
            Level level = player.level();

            // Security: Verify player is close to the requested position to prevent C2S trust exploits
            BlockPos pos = payload.architectPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                context.reply(new ArchitectPreviewResponsePayload(pos, null));
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            Blueprint preview = null;
            if (be instanceof TileArchitectTable architect) {
                preview = architect.getOrRefreshLivePreview();
            }
            // Always reply — a null preview tells the client "nothing to show" so it stops pending.
            context.reply(new ArchitectPreviewResponsePayload(pos, preview));
        });
    }
}
