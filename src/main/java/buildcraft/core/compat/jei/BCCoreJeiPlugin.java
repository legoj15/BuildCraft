/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.compat.jei.JeiSubtypes;
import buildcraft.lib.gui.GuiBC8;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers data component types as subtype differentiators so JEI can
 * distinguish items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:core_jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Tell JEI to differentiate paintbrush stacks by their brush_color component.
        // This handles both ingredient list display AND recipe output matching.
        //? if >=1.21.10 {
        registration.registerFromDataComponentTypes(
                BCCoreItems.PAINTBRUSH.get(),
                BCCore.BRUSH_COLOR.get()
        );
        //?} else {
        /*// 1.21.1 JEI has no registerFromDataComponentTypes — key the paintbrush stack on its
        // brush_color (DyeColor) component by hand; an uncoloured brush has no subtype.
        JeiSubtypes.register(registration, BCCoreItems.PAINTBRUSH.get(),
                stack -> stack.get(BCCore.BRUSH_COLOR.get()));*/
        //?}

        // Differentiate fragile fluid shards by their stored fluid, ignoring the amount (see CoreJeiSubtypes).
        JeiSubtypes.register(registration, BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), CoreJeiSubtypes::fragileFluidKey);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Tell JEI about BuildCraft ledger exclusion areas so the ingredient
        // list is pushed out of the way when ledgers are open.
        registration.addGenericGuiContainerHandler(GuiBC8.class, new BCGuiContainerHandler());
    }
}
