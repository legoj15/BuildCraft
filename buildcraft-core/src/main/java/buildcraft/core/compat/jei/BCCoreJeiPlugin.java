/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers subtype interpreters so JEI can differentiate items that share
 * the same item ID but differ by data component (e.g. coloured paintbrushes).
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
        // Tell JEI to differentiate paintbrush stacks by their brush_color component.
        // Absent = clean brush, present = that specific DyeColor.
        registration.registerSubtypeInterpreter(
                BCCoreItems.PAINTBRUSH.get(),
                (stack, context) -> {
                    DyeColor colour = stack.get(BCCore.BRUSH_COLOR.get());
                    return colour != null ? colour.getName() : "";
                }
        );
    }
}
