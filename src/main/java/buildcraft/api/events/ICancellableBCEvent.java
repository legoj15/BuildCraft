/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.events;

/** A BuildCraft event a listener may cancel; what cancelling does is documented on each event. See
 *  {@link BCEventListeners}. Only a {@link BCEvent} implements it. On NeoForge it is also NeoForge's
 *  {@code ICancellableEvent}, whose bus reads the cancel state through these same two methods. */
//? if neoforge {
public interface ICancellableBCEvent extends net.neoforged.bus.api.ICancellableEvent {
//?} else {
/*public interface ICancellableBCEvent {
*///?}
    boolean isCanceled();

    void setCanceled(boolean canceled);
}
