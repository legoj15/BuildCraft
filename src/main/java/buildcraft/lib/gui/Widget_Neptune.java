/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import buildcraft.lib.net.IPayloadWriter;
import buildcraft.lib.net.PacketBufferBC;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Defines some sort of separate element that exists on both the server and client. Doesn't draw directly.
 *  Used for syncing custom data (e.g. tank clicks, progress) between server containers and client screens. */
public abstract class Widget_Neptune<C extends ContainerBC_Neptune> {
    public final C container;

    public Widget_Neptune(C container) {
        this.container = container;
    }

    public boolean isRemote() {
        return container.player.level().isClientSide();
    }

    /** Send widget data to the other side via the container's networking pipeline. */
    protected final void sendWidgetData(IPayloadWriter writer) {
        container.sendWidgetData(this, writer);
    }

    /** Handle widget data received on the SERVER. Override in subclasses. */
    public void handleWidgetDataServer(IPayloadContext ctx, PacketBufferBC buffer) {
        // Default: no-op
    }

    /** Handle widget data received on the CLIENT. Override in subclasses. */
    public void handleWidgetDataClient(IPayloadContext ctx, PacketBufferBC buffer) {
        // Default: no-op
    }
}
