/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import buildcraft.lib.misc.RegistrationUtilBC;

import buildcraft.lib.item.ItemDebugger;
import buildcraft.lib.item.ItemGuide;
import buildcraft.lib.item.ItemGuideNote;

import buildcraft.core.BCCore;

public class BCLibItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCCore.MODID);

    public static final DeferredItem<ItemGuide> GUIDE = RegistrationUtilBC.registerItem(ITEMS,"guide",
            props -> new ItemGuide(props, "buildcraftunofficial:main"),
            props -> props.stacksTo(1));

    public static final DeferredItem<ItemGuide> GUIDE_CONFIG = RegistrationUtilBC.registerItem(ITEMS,"guide_config",
            props -> new ItemGuide(props, "buildcraftunofficial:config"),
            props -> props.stacksTo(1));

    public static final DeferredItem<ItemGuideNote> GUIDE_NOTE = RegistrationUtilBC.registerItem(ITEMS,"guide_note",
            ItemGuideNote::new, props -> props.stacksTo(1));

    public static final DeferredItem<ItemDebugger> DEBUGGER = RegistrationUtilBC.registerItem(ITEMS,"debugger",
            ItemDebugger::new, props -> props.stacksTo(1));
}
