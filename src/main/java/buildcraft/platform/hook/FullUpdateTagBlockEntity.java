/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

/**
 * A block entity whose client copy applies every update tag it is sent, an empty one included (on 1.21.1 BuildCraft's
 * NeoForge {@code onDataPacket} override). Vanilla before 1.21.10 skips an empty tag, so a tile whose state saves to
 * nothing (a drained tank) would keep showing its last contents; a loader without that override applies it here.
 */
public interface FullUpdateTagBlockEntity {
}
