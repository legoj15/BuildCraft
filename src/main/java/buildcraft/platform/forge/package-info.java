/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The MinecraftForge (not NeoForge) implementation of the {@code buildcraft.platform} contracts (#209). Every file keeps
 * its {@code package} line live and holds its whole body in a {@code //? if forge} arm, so on a node for another loader it
 * is an empty compilation unit. Only the binder in {@code buildcraft.platform} names these classes (and the {@code @Mod}
 * entrypoint, which FML finds). Today only the {@code 26.3-forge} node builds them; a seam of the binder with no Forge
 * implementation yet throws "not ported (#209)", and a gap inside one that exists is a {@code TODO(#209 F3)}.
 */
package buildcraft.platform.forge;
