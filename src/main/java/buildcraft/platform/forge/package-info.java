/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The MinecraftForge (not NeoForge) implementation of the {@code buildcraft.platform} contracts (#209). Every file keeps
 * its {@code package} line live and holds its whole body in a {@code //? if forge} arm, so on a node for another loader it
 * is an empty compilation unit. Only the binder in {@code buildcraft.platform} names these classes. Today only the
 * {@code 26.3-forge} node builds them, and only the loader services exist: every other seam of the binder throws.
 */
package buildcraft.platform.forge;
