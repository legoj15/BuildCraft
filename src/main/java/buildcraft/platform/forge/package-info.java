/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The MinecraftForge (not NeoForge) implementation of the {@code buildcraft.platform} contracts (#209). Every file keeps
 * its {@code package} line live and holds its whole body in a {@code //? if forge} arm, so on a node for another loader it
 * is an empty compilation unit. Only the binder in {@code buildcraft.platform} names these classes (and the {@code @Mod}
 * entrypoint, which FML finds). The {@code 26.3-forge}, {@code 26.2-forge}, {@code 26.1.2-forge}, {@code 1.21.11-forge}
 * and {@code 1.21.1-forge} nodes build them, beside the rest of BuildCraft's source (each compiles all of
 * {@code src/main/java} bar the recipe-viewer plugins it has no viewer for, and ships nothing yet). The common seams and
 * the client ones ({@code platform.forge.client} and {@code platform.forge.mixin.client}: F6 on 26.3, F8 on 26.2, 26.1.2
 * and 1.21.11, F7b on 1.21.1) are implemented on every one, with per-version arms where the lines differ; a few classes
 * serve some lines only and are whole-file {@code forge && >=1.21.10}, {@code forge && <1.21.10} or 1.21.11 alone, and a
 * client mixin with nothing to do on a line keeps an empty arm there so the one mixin config names the same classes on
 * every line. A known gap inside an implementation that exists is recorded in {@code docs/forge-port.md} (the hooks Forge lacks are
 * served by the mixins in {@code platform.forge.mixin}).
 */
package buildcraft.platform.forge;
