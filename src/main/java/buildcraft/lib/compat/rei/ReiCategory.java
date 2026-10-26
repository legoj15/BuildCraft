/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import java.util.ArrayList;
import java.util.List;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

/**
 * Base of every BuildCraft REI category. A subclass only states what one recipe shows ({@link #layout}), in the same
 * coordinates as its JEI twin; this class turns that into the display's indexed entries ({@link #display}) and, when
 * REI lays the display out, into widgets on REI's recipe panel with a {@value #PADDING}-pixel margin.
 *
 * @param <R> the recipe-viewer entry type
 */
public abstract class ReiCategory<R> implements DisplayCategory<ReiDisplay<R>> {

    private static final int PADDING = 4;

    private final CategoryIdentifier<ReiDisplay<R>> id;
    private final Component title;
    private final ItemLike icon;
    private final int width;
    private final int height;

    /** @param width  the content width, as the JEI category's
     *  @param height the content height, as the JEI category's */
    protected ReiCategory(CategoryIdentifier<ReiDisplay<R>> id, String titleKey, ItemLike icon, int width,
            int height) {
        this.id = id;
        this.title = Component.translatable(titleKey);
        this.icon = icon;
        this.width = width;
        this.height = height;
    }

    /** Adds what {@code recipe} shows to {@code layout}. Called once per recipe, when its display is made. */
    protected abstract void layout(R recipe, ReiLayout layout);

    /** The display for {@code recipe}, ready for {@code DisplayRegistry.add}. */
    public final ReiDisplay<R> display(R recipe) {
        ReiLayout layout = new ReiLayout();
        layout(recipe, layout);
        return new ReiDisplay<>(id, recipe, layout);
    }

    @Override
    public final CategoryIdentifier<ReiDisplay<R>> getCategoryIdentifier() {
        return id;
    }

    @Override
    public final Component getTitle() {
        return title;
    }

    @Override
    public final Renderer getIcon() {
        return EntryStacks.of(icon);
    }

    @Override
    public final List<Widget> setupDisplay(ReiDisplay<R> display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.addAll(display.layout().widgets(bounds.x + PADDING, bounds.y + PADDING));
        return widgets;
    }

    @Override
    public final int getDisplayHeight() {
        return height + 2 * PADDING;
    }

    @Override
    public final int getDisplayWidth(ReiDisplay<R> display) {
        return width + 2 * PADDING;
    }
}
