/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The Fabric implementation of the {@code buildcraft.platform} contracts (#98, Phase 2). Every file keeps its
 * {@code package} line live and holds its whole body in a {@code //? if fabric} arm, so on a node for another loader
 * it is an empty compilation unit. Only the binder in {@code buildcraft.platform} names these classes. Today only the
 * {@code 26.3-fabric} node builds them.
 */
package buildcraft.platform.fabric;
