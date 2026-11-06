/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Every {@code BC*BlockEntities} registry builds its types through {@link BlockEntityTypeUtilBC}. From 1.21.10 the
 * constructor it replaced (NeoForge's varargs one) refused a type with no valid block at startup; the helper has to
 * keep refusing it, because the {@code Set} constructor it calls instead accepts one silently.
 */
public class BlockEntityTypeUtilBCTest {

    //? if >=1.21.10 {
    /**
     * The block entity type registry is frozen by the time a test runs, so a type that gets as far as the constructor
     * fails with an {@code IllegalStateException}: the refusal has to come first.
     */
    @Test
    public void aTypeWithNoValidBlockIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BlockEntityTypeUtilBC.create((pos, state) -> null));
    }
    //?}
}
