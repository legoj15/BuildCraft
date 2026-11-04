/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import net.minecraft.world.entity.Entity;

/**
 * A registered, saved data attachment on entities (NeoForge data attachments, Fabric's data attachment API),
 * created by a {@link BCAttachmentRegistrar}. The value is a mutable {@link BCAttachmentData}.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 *
 * @param <T> the payload type
 */
public interface BCAttachment<T extends BCAttachmentData> {
    /** {@code holder}'s value; a fresh default value is attached first when it has none. */
    T get(Entity holder);

    /** Replaces {@code holder}'s value. */
    void set(Entity holder, T value);
}
