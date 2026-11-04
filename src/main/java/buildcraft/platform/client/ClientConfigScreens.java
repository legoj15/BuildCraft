/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

/** Client-only: hooks BuildCraft's configs into the loader's mod-list config screen. */
public interface ClientConfigScreens {

    /** Registers the in-game config screen for the mod being constructed. */
    void registerConfigScreen();
}
