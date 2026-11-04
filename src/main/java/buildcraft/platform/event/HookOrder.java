/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.event;

/**
 * Where a {@link GameHooks} listener runs among every mod's listeners for the same event. Only the hooks BuildCraft
 * depends on the order of take one; the rest run at the loader's default order.
 *
 * <p>On NeoForge these are the event priorities {@code HIGH}, {@code NORMAL} and {@code LOWEST}, so a listener that
 * used to name one of those keeps its exact place. On Fabric they are event phases ordered before and after the
 * default phase.
 */
public enum HookOrder {
    /** Before the default listeners (NeoForge {@code HIGH}). */
    EARLY,
    /** With the default listeners (NeoForge {@code NORMAL}). */
    DEFAULT,
    /** After every other listener (NeoForge {@code LOWEST}): sees what the others decided. */
    LATE
}
