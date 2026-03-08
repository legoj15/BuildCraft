/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import buildcraft.lib.net.IPayloadWriter;

/** Defines some sort of separate element that exists on both the server and client. Doesn't draw directly.
 *  Used for syncing custom data (e.g. tank contents, progress) between server containers and client screens. */
public abstract class Widget_Neptune<C extends ContainerBC_Neptune> {
    public final C container;

    public Widget_Neptune(C container) {
        this.container = container;
    }

    public boolean isRemote() {
        return container.player.level().isClientSide();
    }

    /** Send widget data to the other side. Stub until networking is ported. */
    protected final void sendWidgetData(IPayloadWriter writer) {
        // No-op until networking ported
    }
}
