/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.events;

/** A BuildCraft event a listener may cancel; what cancelling does is documented on each event. See
 *  {@link BCEventListeners}. */
public interface ICancellableBCEvent {
    boolean isCanceled();

    void setCanceled(boolean canceled);
}
