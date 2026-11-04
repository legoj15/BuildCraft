/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * Loader-neutral render data a block entity hands its block model (#98, "Client render" in
 * {@code docs/platform-layer.md}). Common, not client-only: the block entity that exposes it loads on a dedicated
 * server too. The contract rule of {@code buildcraft.platform} applies: JDK, vanilla and {@code buildcraft.platform}
 * types only.
 */
package buildcraft.platform.render;
