/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import buildcraft.platform.transfer.BCItemStorage;

/** BuildCraft's item-handler type: a loader-neutral {@link BCItemStorage}. The platform layer adapts it to the
 * loader's own handler when it is exposed as a capability ({@code ResourceHandler<ItemResource>} on NeoForge
 * 1.21.10+, the classic {@code IItemHandler} on 1.21.1), so BuildCraft code calls one API on every node. */
public interface IBCItemHandler extends BCItemStorage {}
