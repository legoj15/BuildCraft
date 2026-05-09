/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.pipe;

import java.util.HashMap;
import java.util.Map;

import buildcraft.api.transport.pluggable.IPluggableRegistry;
import buildcraft.api.transport.pluggable.PluggableDefinition;

public class PluggableRegistry implements IPluggableRegistry {
    public static final PluggableRegistry INSTANCE = new PluggableRegistry();
    private final Map<Object, PluggableDefinition> definitions = new HashMap<>();

    @Override
    public void register(Object ResourceLocation, PluggableDefinition definition) {
        definitions.put(ResourceLocation, definition);
    }

    @Override
    public PluggableDefinition getDefinition(Object ResourceLocation) {
        return definitions.get(ResourceLocation);
    }
}
