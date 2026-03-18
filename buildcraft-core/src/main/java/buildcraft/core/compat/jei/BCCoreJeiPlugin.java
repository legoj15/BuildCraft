/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers a subtype interpreter so JEI can distinguish paintbrush colours
 * for both the ingredient list AND recipe output matching.
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftcore:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] registerItemSubtypes called!");

        registration.registerSubtypeInterpreter(BCCoreItems.PAINTBRUSH.get(),
                (ItemStack stack, UidContext context) -> {
                    DyeColor color = stack.get(BCCore.BRUSH_COLOR.get());
                    if (color != null) {
                        return color.getName(); // each color is a distinct subtype
                    }
                    return ""; // clean brush = no subtype
                }
        );

        org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] Registered paintbrush subtype interpreter");
    }
}


