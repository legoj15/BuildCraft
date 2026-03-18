/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;

/**
 * JEI integration plugin for BuildCraft Core.
 * Registers data component types as subtype differentiators so JEI can
 * distinguish items that share the same item ID but differ by component
 * (e.g. coloured paintbrushes).
 */
@JeiPlugin
public class BCCoreJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftcore:jei_plugin");

    public BCCoreJeiPlugin() {
        org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] BCCoreJeiPlugin INSTANTIATED");
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] registerItemSubtypes called!");
        registration.registerFromDataComponentTypes(
                BCCoreItems.PAINTBRUSH.get(),
                BCCore.BRUSH_COLOR.get()
        );
        org.slf4j.LoggerFactory.getLogger("BuildCraft").info("[JEI] Registered BRUSH_COLOR subtype for paintbrush");
    }
}
