/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.world.item.Item;

/**
 * The third wrench tier: everything the {@link ItemMarkerWrench marker wrench} does, and it can also pick up the
 * corner of a volume box and drag it to a new size. It is the diamond-tier wrench ({@link WrenchTier#DIAMOND}); the
 * other two are iron.
 */
public class ItemVolumeWrench extends ItemMarkerWrench {

    public ItemVolumeWrench(Item.Properties properties) {
        super(properties, WrenchTier.DIAMOND);
    }

    @Override
    protected boolean canEditCorners() {
        return true;
    }
}
