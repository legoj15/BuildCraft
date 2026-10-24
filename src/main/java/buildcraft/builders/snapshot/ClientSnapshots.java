/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum ClientSnapshots {
    INSTANCE;

    private static final Logger LOGGER = LogManager.getLogger("BCClientSnapshots");

    private final List<Snapshot> snapshots = new ArrayList<>();
    private final SnapshotRequestTracker requests = new SnapshotRequestTracker(System::nanoTime,
            SnapshotRequestTracker.DEFAULT_TIMEOUT_NANOS, SnapshotRequestTracker.DEFAULT_MAX_TIMEOUT_NANOS);

    public Snapshot getSnapshot(Snapshot.Key key) {
        Snapshot found = snapshots.stream().filter(snapshot -> snapshot.key.equals(key)).findFirst().orElse(null);
        SnapshotRequestTracker.Ask ask = found == null ? requests.ask(key) : SnapshotRequestTracker.Ask.WAIT;
        if (ask != SnapshotRequestTracker.Ask.WAIT) {
            String hashHex = key.hash == null ? "null"
                    : buildcraft.lib.misc.HashUtil.convertHashToString(key.hash);
            // INFO once per hash; the timed resends of an unanswered one (a hash the server refuses is retried for
            // as long as its preview is drawn) are DEBUG, so a hovered tooltip does not fill the log.
            if (ask == SnapshotRequestTracker.Ask.FIRST) {
                LOGGER.info("Sending SnapshotRequest to server: hash={} hasHeader={}",
                        hashHex, key.header != null);
            } else {
                LOGGER.debug("Resending unanswered SnapshotRequest to server: hash={} hasHeader={}",
                        hashHex, key.header != null);
            }
            //? if >=1.21.10 {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new SnapshotRequestPayload(key));
            //?} else {
            /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(new SnapshotRequestPayload(key));*/
            //?}
        }
        return found;
    }

    public void onSnapshotReceived(Snapshot snapshot) {
        String hashHex = snapshot.key.hash == null ? "null"
                : buildcraft.lib.misc.HashUtil.convertHashToString(snapshot.key.hash);
        LOGGER.info("Received snapshot from server: class={} hash={} size={} pendingRemoved={}",
                snapshot.getClass().getSimpleName(), hashHex, snapshot.size,
                requests.received(snapshot.key));
        // Replace existing if updated
        snapshots.removeIf(s -> s.key.equals(snapshot.key));
        snapshots.add(snapshot);
    }
}
