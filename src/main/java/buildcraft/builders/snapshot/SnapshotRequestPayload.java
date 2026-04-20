package buildcraft.builders.snapshot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import buildcraft.lib.net.MessageManager;

public record SnapshotRequestPayload(Snapshot.Key key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SnapshotRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:snapshot_request"));

    public static final StreamCodec<FriendlyByteBuf, SnapshotRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByteArray(payload.key().hash);
                buf.writeBoolean(payload.key().header != null);
                if (payload.key().header != null) {
                    buf.writeUUID(payload.key().header.owner);
                    buf.writeLong(payload.key().header.created.getTime());
                    buf.writeUtf(payload.key().header.name);
                }
            },
            buf -> {
                byte[] hash = buf.readByteArray();
                boolean hasHeader = buf.readBoolean();
                Snapshot.Key key = new Snapshot.Key(new Snapshot.Key(), hash);
                if (hasHeader) {
                    Snapshot.Header header = new Snapshot.Header(key, buf.readUUID(), new java.util.Date(buf.readLong()), buf.readUtf());
                    key = new Snapshot.Key(key, header);
                }
                return new SnapshotRequestPayload(key);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SnapshotRequestPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            Snapshot snapshot = GlobalSavedDataSnapshots.get(context.player().level()).getSnapshot(payload.key());
            if (snapshot != null) {
                // Return response to client that requested
                context.reply(new SnapshotResponsePayload(snapshot));
            }
        });
    }
}
