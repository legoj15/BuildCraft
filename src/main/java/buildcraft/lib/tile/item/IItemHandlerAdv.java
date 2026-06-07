/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

/** BuildCraft's advanced item handler: an {@link IBCItemHandler} (so a {@code ResourceHandler<ItemResource>}
 * on 1.21.10+ via the NeoForge Transfer API, or a classic {@code IItemHandler} on 1.21.1), plus
 * insertion-checking via {@link StackInsertionChecker}. On 1.21.1 it additionally exposes
 * {@code IItemHandlerModifiable} so callers can {@code setStackInSlot} through the type. */
//? if >=1.21.10 {
public interface IItemHandlerAdv extends IBCItemHandler, StackInsertionChecker {}
//?} else {
/*public interface IItemHandlerAdv extends IBCItemHandler, StackInsertionChecker, net.neoforged.neoforge.items.IItemHandlerModifiable {}*/
//?}
