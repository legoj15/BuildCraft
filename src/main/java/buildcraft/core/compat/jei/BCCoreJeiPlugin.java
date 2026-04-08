/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.gui.GuiBC8;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers data component types as subtype differentiators so JEI can
 * distinguish items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Tell JEI to differentiate paintbrush stacks by their brush_color component.
        // This handles both ingredient list display AND recipe output matching.
        registration.registerFromDataComponentTypes(
                BCCoreItems.PAINTBRUSH.get(),
                BCCore.BRUSH_COLOR.get()
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Tell JEI about BuildCraft ledger exclusion areas so the ingredient
        // list is pushed out of the way when ledgers are open.
        registration.addGenericGuiContainerHandler(GuiBC8.class, new BCGuiContainerHandler());
    }
}
