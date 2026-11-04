/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * Loader-neutral routes for the patched-vanilla overrides ({@link buildcraft.platform.LoaderHook}): small interfaces a
 * BuildCraft block, item or block entity implements with the common method its tagged override delegates to. NeoForge
 * reaches that method through the override itself; a loader without the patched hook (Fabric) finds the interface on
 * the block, item or block entity from its own event or mixin and calls the same method. Contract files only: JDK and
 * vanilla types.
 */
package buildcraft.platform.hook;
