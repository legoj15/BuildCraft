/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.compat.viewer.FluidContainerAliases;

/**
 * What one BuildCraft REI display shows, in the category's own coordinates (the same ones the JEI category uses), and
 * which entries it indexes. A {@link ReiCategory} fills one per recipe when the display is made; the display keeps it,
 * reads its {@link #inputs()} and {@link #outputs()} for REI's lookups, and turns it into widgets each time REI lays
 * the display out.
 *
 * <p>Slot coordinates are the item's top-left pixel, as for a vanilla menu slot and a JEI slot. Once a
 * {@link #background} is set the slot art comes from that texture, so slots stop drawing REI's own frame.
 */
public final class ReiLayout {

    /** How a slot takes part in lookups: an input ("uses"), an output ("recipes"), or shown only. */
    public enum Role {
        INPUT, OUTPUT, RENDER_ONLY
    }

    /** Text colour on REI's light and dark recipe backgrounds. The light one is JEI's (the vanilla label grey). */
    private static final int TEXT_LIGHT = 0xFF404040, TEXT_DARK = 0xFFBBBBBB;

    private interface Element {
        Widget widget(int originX, int originY, boolean textured);
    }

    private final List<Element> elements = new ArrayList<>();
    private final List<EntryIngredient> inputs = new ArrayList<>();
    private final List<EntryIngredient> outputs = new ArrayList<>();
    private final List<IntSupplier> textRightEdges = new ArrayList<>();
    private boolean textured;

    /** Draws a 256x256 texture's region at the origin as the display's panel. Slots then draw no frame of their own. */
    public ReiLayout background(Identifier texture, int u, int v, int width, int height) {
        textured = true;
        return texture(texture, 0, 0, u, v, width, height);
    }

    /** Draws a 256x256 texture's region at {@code (x, y)}, above everything added before it. */
    public ReiLayout texture(Identifier texture, int x, int y, int u, int v, int width, int height) {
        elements.add((ox, oy, t) -> Widgets.createTexturedWidget(texture, ox + x, oy + y, u, v, width, height));
        return this;
    }

    /** An item slot cycling through {@code stacks}. Empty lists add nothing. */
    public ReiLayout item(int x, int y, Role role, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return this;
        }
        EntryIngredient entries = EntryIngredients.ofItemStacks(stacks);
        index(role, entries);
        elements.add((ox, oy, t) -> mark(Widgets.createSlot(new Point(ox + x, oy + y)), role, t).entries(entries));
        return this;
    }

    /** An output item slot on REI's large result frame (JEI's output-slot background). */
    public ReiLayout result(int x, int y, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return this;
        }
        elements.add((ox, oy, t) -> Widgets.createResultSlotBackground(new Point(ox + x, oy + y)));
        EntryIngredient entries = EntryIngredients.ofItemStacks(stacks);
        index(Role.OUTPUT, entries);
        elements.add((ox, oy, t) -> Widgets.createSlot(new Point(ox + x, oy + y)).disableBackground().markOutput()
                .entries(entries));
        return this;
    }

    /**
     * A fluid tank of {@code width} x {@code height} showing {@code amount} mB of {@code fluid}. As an input or an
     * output it also indexes the fluid's item forms (bucket, fragile shard; see {@link FluidContainerAliases}) without
     * drawing them, so a lookup on the item a player holds finds the recipe. Empty fluids add nothing.
     */
    public ReiLayout fluid(int x, int y, int width, int height, Role role, FluidStack fluid, long amount) {
        if (fluid == null || fluid.isEmpty()) {
            return this;
        }
        EntryIngredient entries = EntryIngredients.of(fluid.getFluid(), amount);
        index(role, entries);
        if (role != Role.RENDER_ONLY) {
            List<ItemStack> forms = FluidContainerAliases.itemForms(fluid);
            if (!forms.isEmpty()) {
                index(role, EntryIngredients.ofItemStacks(forms));
            }
        }
        // BC draws the tank itself (REI's fluid renderer is a no-op from 21.9 on), and with no frame: REI's slot frame
        // is a fixed 18x18 blit that breaks up on a tank-sized slot. JEI draws tanks unframed too.
        var plain = EntryStacks.of(fluid.getFluid(), amount);
        var tank = EntryStacks.of(fluid.getFluid(), amount).withRenderer(new ReiFluidTankRenderer<>(fluid, plain));
        elements.add((ox, oy, t) -> mark(Widgets.createSlot(new Rectangle(ox + x - 1, oy + y - 1, width + 2,
                height + 2)), role, t).disableBackground().entry(tank));
        return this;
    }

    /** A fluid tank showing the whole of {@code fluid}. */
    public ReiLayout fluid(int x, int y, int width, int height, Role role, FluidStack fluid) {
        return fluid == null ? this : fluid(x, y, width, height, role, fluid, fluid.getAmount());
    }

    /** A line of text, left-aligned at {@code (x, y)} in the label grey, without a shadow. */
    public ReiLayout text(int x, int y, Component text) {
        return text(x, y, () -> text);
    }

    /**
     * As {@link #text(int, int, Component)}, but {@code text} is asked again every frame, the way JEI's
     * {@code MjLabel.draw} resolves per frame: a unit-name setting change, or the RF mode arriving from the server
     * after the display was built, shows without a REI reload.
     */
    public ReiLayout text(int x, int y, Supplier<Component> text) {
        textRightEdges.add(() -> x + Minecraft.getInstance().font.width(text.get()));
        elements.add((ox, oy, t) -> Widgets.createLabel(new Point(ox + x, oy + y), text.get()).leftAligned().noShadow()
                .color(TEXT_LIGHT, TEXT_DARK).onRender((graphics, label) -> {
                    Component now = text.get();
                    // setMessage drops REI's cached text layout, so only call it when the text changed.
                    if (!now.equals(label.getMessage())) {
                        label.setMessage(now);
                    }
                }));
        return this;
    }

    public List<EntryIngredient> inputs() {
        return inputs;
    }

    public List<EntryIngredient> outputs() {
        return outputs;
    }

    /** How far right the display's text reaches now, in its own coordinates, so a category can widen the display
     *  to fit a long translation or unit name instead of letting the text run off the card. 0 off the render thread:
     *  REI also asks for display widths while validating displays on its reload thread, and measuring text there
     *  bakes font glyphs, which uploads to the GPU and throws (or worse) off the render thread. */
    public int textWidth() {
        if (!Minecraft.getInstance().isSameThread()) {
            return 0;
        }
        int widest = 0;
        for (IntSupplier edge : textRightEdges) {
            widest = Math.max(widest, edge.getAsInt());
        }
        return widest;
    }

    /** The widgets for a display whose content starts at {@code (originX, originY)}, in drawing order. */
    public List<Widget> widgets(int originX, int originY) {
        List<Widget> widgets = new ArrayList<>(elements.size());
        for (Element element : elements) {
            widgets.add(element.widget(originX, originY, textured));
        }
        return widgets;
    }

    private void index(Role role, EntryIngredient entries) {
        if (role == Role.INPUT) {
            inputs.add(entries);
        } else if (role == Role.OUTPUT) {
            outputs.add(entries);
        }
    }

    private static Slot mark(Slot slot, Role role, boolean textured) {
        if (role == Role.INPUT) {
            slot.markInput();
        } else if (role == Role.OUTPUT) {
            slot.markOutput();
        }
        return textured ? slot.disableBackground() : slot;
    }
}
