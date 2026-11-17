/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The MinecraftForge (not NeoForge) implementation of the {@code buildcraft.platform} contracts (#209). Every file keeps
 * its {@code package} line live and holds its whole body in a {@code //? if forge} arm, so on a node for another loader it
 * is an empty compilation unit. Only the binder in {@code buildcraft.platform} names these classes (and the {@code @Mod}
 * entrypoint, which FML finds). The {@code 26.3-forge} and {@code 1.21.1-forge} nodes build them, beside the rest of
 * BuildCraft's source (each compiles all of {@code src/main/java} bar the recipe-viewer plugins, and ships nothing yet).
 * The common seams are implemented on both; the client ones ({@code platform.forge.client} and
 * {@code platform.forge.mixin.client}, F6) are 26.3 code, whole-file {@code forge && >=1.21.10}, so {@code 1.21.1-forge}
 * compiles them out and its client holders throw "not ported (#209)" until F7b. A known gap inside an implementation that
 * exists is recorded in {@code docs/forge-port.md} (the hooks Forge lacks are served by the mixins in
 * {@code platform.forge.mixin}).
 */
package buildcraft.platform.forge;
