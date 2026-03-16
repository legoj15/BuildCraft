/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.pluggable.PluggableDefinition.IPluggableNbtReader;
import buildcraft.api.transport.pluggable.PluggableDefinition.IPluggableNetLoader;

import buildcraft.silicon.plug.PluggableFacade;

public class BCSiliconPlugs {

    public static PluggableDefinition facade;

    public static void preInit() {
        facade = register("facade",
            (PluggableDefinition.IPluggableNbtReader) PluggableFacade::new,
            (PluggableDefinition.IPluggableNetLoader) PluggableFacade::new);
    }

    private static PluggableDefinition register(String name, IPluggableNbtReader reader, IPluggableNetLoader loader) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), reader, loader);
        // Only register if the pluggable registry is available (transport module loaded)
        if (PipeApi.pluggableRegistry != null) {
            PipeApi.pluggableRegistry.register(def);
        }
        return def;
    }

    private static Identifier idFor(String name) {
        return Identifier.fromNamespaceAndPath(BCSilicon.MODID, name);
    }
}
