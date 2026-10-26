/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

//? if >=1.21.10 {
import javax.annotation.Nullable;

import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
//?}
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;

/**
 * One BuildCraft REI display: the viewer-neutral recipe entry (the same object the JEI category shows), the category
 * it belongs to, and the {@link ReiLayout} its category built for it, whose inputs and outputs are the display's
 * entries. Made on the client by each subsystem's client plugin, never synced, so it has no serializer.
 *
 * @param <R> the recipe-viewer entry type
 */
public final class ReiDisplay<R> extends BasicDisplay {

    private final CategoryIdentifier<ReiDisplay<R>> category;
    private final R recipe;
    private final ReiLayout layout;

    ReiDisplay(CategoryIdentifier<ReiDisplay<R>> category, R recipe, ReiLayout layout) {
        super(layout.inputs(), layout.outputs());
        this.category = category;
        this.recipe = recipe;
        this.layout = layout;
    }

    public R recipe() {
        return recipe;
    }

    ReiLayout layout() {
        return layout;
    }

    @Override
    public CategoryIdentifier<ReiDisplay<R>> getCategoryIdentifier() {
        return category;
    }

    //? if >=1.21.10 {
    // REI allows null for a display that is never synced from the server; these are built client-side only.
    @Override
    @Nullable
    public DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
    //?}
}
