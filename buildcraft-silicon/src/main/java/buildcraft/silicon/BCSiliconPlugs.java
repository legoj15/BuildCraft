/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.minecraft.resources.Identifier;

import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pluggable.PluggableDefinition;
import buildcraft.api.transport.pluggable.PluggableDefinition.IPluggableCreator;
import buildcraft.api.transport.pluggable.PluggableDefinition.IPluggableNbtReader;
import buildcraft.api.transport.pluggable.PluggableDefinition.IPluggableNetLoader;

import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.plug.PluggableLens;
import buildcraft.silicon.plug.PluggableLightSensor;
import buildcraft.silicon.plug.PluggablePulsar;
import buildcraft.silicon.plug.PluggableTimer;

public class BCSiliconPlugs {

    public static PluggableDefinition facade;
    public static PluggableDefinition gate;
    public static PluggableDefinition pulsar;
    public static PluggableDefinition lens;
    public static PluggableDefinition lightSensor;
    public static PluggableDefinition timer;

    public static void preInit() {
        facade = register("facade",
            (IPluggableNbtReader) PluggableFacade::new,
            (IPluggableNetLoader) PluggableFacade::new);
        gate = register("gate",
            (IPluggableNbtReader) PluggableGate::new,
            (IPluggableNetLoader) PluggableGate::new);
        pulsar = register("pulsar",
            (IPluggableNbtReader) PluggablePulsar::new,
            (IPluggableNetLoader) PluggablePulsar::new);
        lens = register("lens",
            (IPluggableNbtReader) PluggableLens::new,
            (IPluggableNetLoader) PluggableLens::new);
        lightSensor = registerSimple("light_sensor",
            PluggableLightSensor::new);
        timer = registerSimple("timer",
            PluggableTimer::new);
    }

    private static PluggableDefinition register(String name, IPluggableNbtReader reader, IPluggableNetLoader loader) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), reader, loader);
        if (PipeApi.pluggableRegistry != null) {
            PipeApi.pluggableRegistry.register(def);
        }
        return def;
    }

    private static PluggableDefinition registerSimple(String name, IPluggableCreator creator) {
        PluggableDefinition def = new PluggableDefinition(idFor(name), creator);
        if (PipeApi.pluggableRegistry != null) {
            PipeApi.pluggableRegistry.register(def);
        }
        return def;
    }

    private static Identifier idFor(String name) {
        return Identifier.fromNamespaceAndPath(BCSilicon.MODID, name);
    }
}
