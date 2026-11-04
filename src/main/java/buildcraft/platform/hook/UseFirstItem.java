/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** An item that acts on a clicked block before the block's own interaction (NeoForge's {@code onItemUseFirst}). */
public interface UseFirstItem {
    /** Anything but PASS ends the click there; PASS hands it on to the block. */
    InteractionResult useBeforeBlock(ItemStack stack, UseOnContext context);
}
