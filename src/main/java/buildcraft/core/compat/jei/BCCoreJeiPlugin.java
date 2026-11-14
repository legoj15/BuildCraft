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
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

import buildcraft.core.compat.viewer.CoreSubtypes;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.compat.jei.JeiSubtypes;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.ledger.LedgerViewport;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers data component types as subtype differentiators so JEI can
 * distinguish items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftunofficial:core_jei_plugin");

    // JEI's bookmark and lookup-history buttons: two 20x20 buttons at GUI x 6..47, y screenHeight-26..screenHeight-7,
    // drawn whenever a container screen is open. Each number below is that geometry plus 2 px of air.
    /** A ledger whose full-width left edge is below this (the buttons end at x 47) reaches the buttons. */
    private static final int JEI_BUTTONS_CLEAR_X = 50;
    /** How much of the bottom such a ledger stays above (the buttons' top edge is 26 px up). */
    private static final int JEI_BUTTONS_RESERVE = 28;

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

        // Differentiate fragile fluid shards by their stored fluid, ignoring the amount (see CoreSubtypes).
        JeiSubtypes.register(registration, BCCoreItems.FRAGILE_FLUID_CONTAINER.get(), CoreSubtypes::fragileFluidKey);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Tell JEI about BuildCraft ledger exclusion areas so the ingredient
        // list is pushed out of the way when ledgers are open.
        registration.addGenericGuiContainerHandler(GuiBC8.class,
            new BCGuiContainerHandler(registration.getJeiHelpers().getPlatformFluidHelper()));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Keep left-side ledgers off JEI's bottom-left buttons. Installed here rather than in the plugin's
        // constructor so it exists exactly while JEI's buttons do (REI, which has none there, never sets it).
        LedgerViewport.setLeftBottomReserve(
            ledgerLeftX -> ledgerLeftX < JEI_BUTTONS_CLEAR_X ? JEI_BUTTONS_RESERVE : 0);
    }

    @Override
    public void onRuntimeUnavailable() {
        LedgerViewport.setLeftBottomReserve(null);
    }
}
