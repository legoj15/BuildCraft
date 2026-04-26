/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.list;

import buildcraft.api.lists.ListRegistry;

/** Single entry point for registering BuildCraft's built-in list-match handlers.
 * Called from {@code BCCore#init} during {@code FMLCommonSetupEvent}. Order matters:
 * the broader handlers (Tags) come first so they get a chance to claim sources before
 * narrower or opt-in handlers (Class) are consulted. */
public final class VanillaListHandlers {

    private VanillaListHandlers() {
    }

    public static void register() {
        ListRegistry.registerHandler(new ListMatchHandlerTags());
        ListRegistry.registerHandler(new ListMatchHandlerTools());
        ListRegistry.registerHandler(new ListMatchHandlerArmor());
        ListRegistry.registerHandler(new ListMatchHandlerFluid());
        ListRegistry.registerHandler(new ListMatchHandlerClass());
    }
}
