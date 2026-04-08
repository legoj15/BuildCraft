/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class ItemAddon extends Item {
    public ItemAddon(Item.Properties properties) {
        super(properties);
    }

    public abstract Addon createAddon();

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        // TODO: Volume box addon system is not fully ported yet.
        // When WorldSavedDataVolumeBoxes is ported, this will:
        // 1. Look up the selected VolumeBox and EnumAddonSlot
        // 2. Create and attach the addon if the slot is available
        // 3. Mark the data as dirty
        return InteractionResult.PASS;
    }
}
