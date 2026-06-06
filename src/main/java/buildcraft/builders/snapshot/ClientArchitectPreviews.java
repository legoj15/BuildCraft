package buildcraft.builders.snapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

//? if >=1.21.10 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}

/**
 * Client-side cache of transient Architect Table previews, keyed by tile position. Mirrors the
 * request/reply pattern used by {@link ClientSnapshots} but without the hash-based key scheme —
 * previews are ephemeral and tied to a specific tile's world state at request time.
 * <p>
 * The cache preserves the cached {@link Blueprint} <i>instance</i> across refreshes whose
 * content hash is unchanged. Two reasons:
 * <ul>
 *   <li>{@code BlueprintPipRenderer} logs once per {@code System.identityHashCode(blueprint)};
 *       replacing the instance every 2s would cause per-refresh log spam.</li>
 *   <li>A periodic refresh that drops the cache first leaves the preview blank for the frames
 *       between request and response. Keeping the old instance visible until a <i>different</i>
 *       Blueprint arrives avoids that flicker entirely.</li>
 * </ul>
 */
public enum ClientArchitectPreviews {
    INSTANCE;

    private final Map<BlockPos, Blueprint> previews = new HashMap<>();
    private final Set<BlockPos> pending = new HashSet<>();

    /**
     * Returns the cached preview for the given tile position, or {@code null} if none is
     * available yet. If no cache entry exists and no request is already in flight, fires off a
     * request to the server.
     */
    @Nullable
    public Blueprint get(BlockPos pos) {
        Blueprint cached = previews.get(pos);
        if (cached == null && !pending.contains(pos)) {
            pending.add(pos);
            //? if >=1.21.10 {
            ClientPacketDistributor.sendToServer(new ArchitectPreviewRequestPayload(pos.immutable()));
            //?} else {
            /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ArchitectPreviewRequestPayload(pos.immutable()));*/
            //?}
        }
        return cached;
    }

    /**
     * Asks the server for a fresh preview without dropping the currently-cached one. Safe to
     * call on a periodic timer while the GUI is open — the old preview stays visible until the
     * server's reply arrives (or forever, if the content hasn't changed).
     */
    public void requestRefresh(BlockPos pos) {
        BlockPos key = pos.immutable();
        if (!pending.contains(key)) {
            pending.add(key);
            //? if >=1.21.10 {
            ClientPacketDistributor.sendToServer(new ArchitectPreviewRequestPayload(key));
            //?} else {
            /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ArchitectPreviewRequestPayload(key));*/
            //?}
        }
    }

    /**
     * Called from the response handler. If the incoming blueprint's content hash matches the
     * cached one, we keep the existing instance so downstream identity-hashcode tracking (e.g.
     * BlueprintPipRenderer's once-per-blueprint log) doesn't re-fire on every refresh cycle.
     * A null blueprint clears any stale cache entry.
     */
    public void onReceived(BlockPos pos, @Nullable Blueprint blueprint) {
        BlockPos key = pos.immutable();
        pending.remove(key);
        if (blueprint == null) {
            previews.remove(key);
            return;
        }
        Blueprint existing = previews.get(key);
        if (existing != null && sameContent(existing, blueprint)) {
            // Content unchanged; keep the cached instance to preserve identity.
            return;
        }
        previews.put(key, blueprint);
    }

    /** Forgets everything about a position — used when the GUI closes. */
    public void invalidate(BlockPos pos) {
        BlockPos key = pos.immutable();
        previews.remove(key);
        pending.remove(key);
    }

    /**
     * Cheap equivalence check via the content-addressed key computed on the server. Both sides
     * run {@link Snapshot#computeKey()}, so matching hashes imply matching content.
     */
    private static boolean sameContent(Blueprint a, Blueprint b) {
        if (a.key == null || b.key == null) return false;
        byte[] ah = a.key.hash;
        byte[] bh = b.key.hash;
        if (ah == null || bh == null || ah.length == 0 || bh.length == 0) return false;
        return java.util.Arrays.equals(ah, bh);
    }
}
