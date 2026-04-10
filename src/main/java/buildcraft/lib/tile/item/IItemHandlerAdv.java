/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A form of {@link ResourceHandler} that provides insertion-checking functionality via {@link StackInsertionChecker} */
public interface IItemHandlerAdv extends ResourceHandler<ItemResource>, StackInsertionChecker {}
