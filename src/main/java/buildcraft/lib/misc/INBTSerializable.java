/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import net.minecraft.nbt.Tag;

/**
 * Compatibility stub for the removed net.neoforged.neoforge.common.util.INBTSerializable.
 * In NeoForge 1.21.x this interface was dropped; classes now use custom serialization patterns.
 */
public interface INBTSerializable<T extends Tag> {
    T serializeNBT();
    void deserializeNBT(T nbt);
}
