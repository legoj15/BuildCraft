/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;


import buildcraft.lib.item.ItemDebugger;
import buildcraft.lib.item.ItemGuide;

import buildcraft.core.BCCore;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCItemHolder;

public class BCLibItems {
    public static final BCRegistrar.Items ITEMS = BCRegistrar.createItems(BCCore.MODID);

    public static final BCItemHolder<ItemGuide> GUIDE = ITEMS.registerItem("guide",
            props -> new ItemGuide(props, "buildcraftunofficial:main"),
            props -> props.stacksTo(1));

    public static final BCItemHolder<ItemGuide> GUIDE_CONFIG = ITEMS.registerItem("guide_config",
            props -> new ItemGuide(props, "buildcraftunofficial:config"),
            props -> props.stacksTo(1));

    public static final BCItemHolder<ItemDebugger> DEBUGGER = ITEMS.registerItem("debugger",
            ItemDebugger::new, props -> props.stacksTo(1));
}
