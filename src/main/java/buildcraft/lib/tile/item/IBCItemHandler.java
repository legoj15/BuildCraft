/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
//?}

/** BuildCraft's version-neutral item-handler capability TYPE alias: a {@code ResourceHandler<ItemResource>}
 * (the NeoForge Transfer API) on 1.21.10+, or a classic {@code IItemHandler} on 1.21.1. Type-reference sites
 * name this one type instead of the node-specific capability interface, so they swap a single import and need
 * no per-node directive of their own. Method-calling sites (which differ by node) still gate their bodies. */
//? if >=1.21.10 {
public interface IBCItemHandler extends ResourceHandler<ItemResource> {}
//?} else {
/*public interface IBCItemHandler extends net.neoforged.neoforge.items.IItemHandler {}*/
//?}
