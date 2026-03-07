/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCBuildersItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BCBuilders.MODID);

    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
