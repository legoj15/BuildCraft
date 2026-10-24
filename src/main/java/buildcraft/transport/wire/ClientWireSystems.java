/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.wire;

import java.util.HashMap;
import java.util.Map;

/**
 * The wire systems this client has been sent, by wires hash. One map for the whole client, replaced wholesale by each
 * {@link PayloadWireSystems} and read by {@link PayloadWireSystemsPowered}. The server sends nothing for a level that
 * has no wire in view, so a map left alone would keep the previous dimension's (or server's) systems after a dimension
 * change or a reconnect; {@code BCTransportClient} clears it when the client level goes away.
 */
public enum ClientWireSystems {
    INSTANCE;

    public final Map<Integer, WireSystem> wireSystems = new HashMap<>();

    /** Forget every system: they belong to a level the client no longer has. */
    public void clear() {
        wireSystems.clear();
    }
}
