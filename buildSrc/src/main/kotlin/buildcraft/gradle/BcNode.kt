/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.gradle

/**
 * What the central build.gradle.kts works out about its node from Stonecutter, handed to the `bc.<loader>`
 * convention plugin it then applies (as the `bcNode` extension). The convention plugins cannot ask Stonecutter
 * themselves: its classes live on the settings classpath, not on buildSrc's.
 *
 * Read the flags into locals at configuration time. A task action must never capture this object or the script
 * that holds it: the configuration cache cannot serialize either.
 */
data class BcNode(
    /** Below MC 1.21.11 (the 1.21.10 and 1.21.1 nodes). */
    val isPre1_21_11: Boolean,
    /** Below MC 1.21.10 (the 1.21.1 node). */
    val isPre1_21_10: Boolean,
    /** Below MC 26.3. */
    val isPre26_3: Boolean,
    /** Below MC 26.1: Minecraft ships obfuscated, so a Fabric node remaps (Loom's remap mode, Mojang mappings). */
    val isPre26_1: Boolean = false,
)
