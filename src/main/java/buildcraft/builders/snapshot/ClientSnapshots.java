package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import buildcraft.lib.net.MessageManager;

public enum ClientSnapshots {
    INSTANCE;

    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<Snapshot.Key> pending = new ArrayList<>();

    public Snapshot getSnapshot(Snapshot.Key key) {
        Snapshot found = snapshots.stream().filter(snapshot -> snapshot.key.equals(key)).findFirst().orElse(null);
        if (found == null && !pending.contains(key)) {
            pending.add(key);
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new SnapshotRequestPayload(key));
        }
        return found;
    }

    public void onSnapshotReceived(Snapshot snapshot) {
        pending.remove(snapshot.key);
        // Replace existing if updated
        snapshots.removeIf(s -> s.key.equals(snapshot.key));
        snapshots.add(snapshot);
    }
}
