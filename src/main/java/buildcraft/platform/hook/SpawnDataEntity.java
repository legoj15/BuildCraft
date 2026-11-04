/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.hook;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * An entity that sends extra data to each client that starts tracking it, read there before the entity is used
 * (NeoForge: {@code IEntityWithComplexSpawn}, the data riding with the spawn packet; Fabric: a BuildCraft payload sent
 * right after the spawn packet, from the tracking-start event, and applied to the client entity by id).
 */
public interface SpawnDataEntity {
    /** Server: writes the extra spawn data. */
    void writeExtraSpawnData(RegistryFriendlyByteBuf buffer);

    /** Client: reads what {@link #writeExtraSpawnData} wrote. */
    void readExtraSpawnData(RegistryFriendlyByteBuf buffer);
}
