/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.button;

import java.util.function.IntSupplier;

import net.minecraft.network.chat.Component;

/**
 * The Auto Workbench's and Advanced Crafting Table's "cycle the crafting output" button (issue #20 follow-up): a
 * 14×14 {@link BCButton} with the {@link ButtonSprite#CYCLE} icon, greyed out (with an explanatory tooltip) unless the
 * grid matches two or more recipes. Both screens build the identical button, so it is configured once here.
 */
public final class CycleOutputButton {
    public static final int SIZE = 14;

    private CycleOutputButton() {}

    /**
     * @param matchCount the synced number of recipes the grid currently matches
     * @param onCycle    sends the cycle message (the server is a no-op past a single match)
     */
    public static BCButton create(int x, int y, IntSupplier matchCount, Runnable onCycle) {
        return BCButton.builder(x, y, SIZE, SIZE)
            .icon(ButtonSprite.CYCLE)
            .activeWhen(() -> matchCount.getAsInt() > 1)
            .tooltip(() -> Component.translatable(matchCount.getAsInt() > 1
                ? "gui.buildcraftunofficial.cycle_output"
                : "gui.buildcraftunofficial.cycle_output.none"))
            .onPress(onCycle)
            .build();
    }
}
