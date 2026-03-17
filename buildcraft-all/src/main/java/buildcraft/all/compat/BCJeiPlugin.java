/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.all.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.Identifier;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;

/**
 * JEI integration plugin for BuildCraft.
 *
 * This class lives in the buildcraft-all assembly module (not buildcraft-core) because
 * NeoForge's ModFileScanData annotation scanning in the multi-module dev environment
 * does not consistently discover @JeiPlugin annotations in sub-project sourceSets.
 * By placing the plugin in buildcraft-all's own sourceSet (which is explicitly added
 * to the buildcraftcore mod entry), annotation discovery is guaranteed.
 */
@JeiPlugin
public class BCJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("buildcraftcore:jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Register brush_color DataComponentType as a subtype differentiator.
        // JEI will use the component's value to distinguish paintbrush colour variants
        // for both ingredient display and recipe output matching.
        registration.registerFromDataComponentTypes(
                BCCoreItems.PAINTBRUSH.get(),
                BCCore.BRUSH_COLOR.get()
        );
    }
}
