/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile.item;

import buildcraft.platform.transfer.BCModifiableItemStorage;

/** BuildCraft's advanced item handler: an {@link IBCItemHandler} plus insertion-checking via
 * {@link StackInsertionChecker}, and settable slots ({@link BCModifiableItemStorage}). On 1.21.1 the platform adapter
 * therefore hands it out as an {@code IItemHandlerModifiable}, as this type itself was before the platform layer. */
public interface IItemHandlerAdv extends IBCItemHandler, StackInsertionChecker, BCModifiableItemStorage {}
