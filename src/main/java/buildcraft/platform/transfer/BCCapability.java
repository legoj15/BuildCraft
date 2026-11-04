/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * A typed, loader-neutral token for one kind of block storage, as BuildCraft's pipe API passes it around (where an
 * {@code Object} token used to name the loader's capability). Each kind has exactly one instance, so the {@code ==}
 * checks the pipe flows make on their tokens keep working; {@link TransferAccess#find} resolves one to the loader's
 * lookup and returns the BuildCraft view.
 *
 * @param <T> the BuildCraft storage type the token finds.
 */
public final class BCCapability<T> {
    /** Item storages: {@link BCItemStorage}. */
    public static final BCCapability<BCItemStorage> ITEM = new BCCapability<>("item", BCItemStorage.class);

    private final String name;
    private final Class<T> type;

    private BCCapability(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /** The storage type this token finds. */
    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "BCCapability[" + name + "]";
    }
}
