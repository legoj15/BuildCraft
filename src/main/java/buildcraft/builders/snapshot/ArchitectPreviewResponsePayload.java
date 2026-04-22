package buildcraft.builders.snapshot;

import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: transient live preview of the Architect Table's scan-area contents. The
 * blueprint is ephemeral — not stored in {@link GlobalSavedDataSnapshots}, so it only lives in
 * {@link ClientArchitectPreviews} until invalidated. A null blueprint means the server has no
 * preview to offer (invalid box, volume exceeded, etc.) and the client should stop asking.
 */
public record ArchitectPreviewResponsePayload(BlockPos architectPos, @Nullable Blueprint blueprint)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArchitectPreviewResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:architect_preview_response"));

    public static final StreamCodec<FriendlyByteBuf, ArchitectPreviewResponsePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.architectPos());
                boolean hasBlueprint = payload.blueprint() != null;
                buf.writeBoolean(hasBlueprint);
                if (hasBlueprint) {
                    try {
                        CompoundTag nbt = Snapshot.writeToNBT(payload.blueprint());
                        NbtIo.writeCompressed(nbt, new ByteBufOutputStream(buf));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to compress architect preview", e);
                    }
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                boolean hasBlueprint = buf.readBoolean();
                Blueprint bp = null;
                if (hasBlueprint) {
                    try {
                        CompoundTag nbt = NbtIo.readCompressed(new ByteBufInputStream(buf), NbtAccounter.unlimitedHeap());
                        Snapshot snapshot = Snapshot.readFromNBT(nbt);
                        if (snapshot instanceof Blueprint blueprint) {
                            bp = blueprint;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to decompress architect preview", e);
                    }
                }
                return new ArchitectPreviewResponsePayload(pos, bp);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArchitectPreviewResponsePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientArchitectPreviews.INSTANCE.onReceived(
                payload.architectPos(), payload.blueprint()));
    }
}
