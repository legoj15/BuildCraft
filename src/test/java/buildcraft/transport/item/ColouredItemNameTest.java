/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.item;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.energy.BCEnergyConfig;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.transport.BCTransportItems;

/**
 * A painted pipe and a charged paintbrush are named "colour + noun". The word ORDER used to be hardcoded in Java
 * ({@code literal(colour) + " " + base}), so a language that puts the colour after the noun, or has no separating
 * space, could not fix it in its lang file. The name is now a two-argument format key — colour first, base item name
 * second — the same shape {@code ItemWire} ("%s Pipe Wire") and {@code ItemPluggableLens} ("%s %s") already use.
 *
 * <p>The assertions inspect the {@link Component} structure rather than its rendered text because a plain unit JVM has
 * no BuildCraft lang loaded (a missing key renders as itself, see {@code LocaleUtilUnitKeyTester}); the structure is
 * what carries the word order to the lang file.
 */
public class ColouredItemNameTest extends VanillaSetupBaseTester {

    // Literals on purpose (not the production constants): the key is part of the contract with the lang files.
    static final String PIPE_KEY = "item.buildcraftunofficial.pipe.coloured";
    static final String BRUSH_KEY = "item.buildcraftunofficial.paintbrush.coloured";

    private static TranslatableContents translatable(Component name) {
        return Assertions.assertInstanceOf(TranslatableContents.class, name.getContents(),
                "the name must be a translatable component, not a literal built in Java");
    }

    private static String textOf(Object arg) {
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    private static ItemStack colouredPipe(net.minecraft.world.item.Item pipe, DyeColor colour) {
        ItemStack stack = new ItemStack(pipe);
        stack.set(BCTransportItems.PIPE_COLOUR.get(), colour);
        return stack;
    }

    @Test
    public void paintedPipeNameIsAColourThenBaseFormatKey() {
        ItemStack stack = colouredPipe(BCTransportItems.PIPE_WOOD_ITEM.get(), DyeColor.ORANGE);

        TranslatableContents contents = translatable(stack.getItem().getName(stack));

        Assertions.assertEquals(PIPE_KEY, contents.getKey(), "painted pipes are named through the format key");
        Assertions.assertEquals(2, contents.getArgs().length, "format key takes the colour then the pipe's own name");
        Assertions.assertEquals(ColourUtil.getTextFullTooltip(DyeColor.ORANGE), textOf(contents.getArgs()[0]),
                "argument 0 is the (localised, chat-coloured) colour word");
        TranslatableContents base = translatable(Assertions.assertInstanceOf(Component.class, contents.getArgs()[1],
                "argument 1 is the pipe's own name component, so it stays translatable"));
        Assertions.assertEquals(stack.getItem().getDescriptionId(), base.getKey(),
                "argument 1 is the unpainted pipe's translatable name");
    }

    @Test
    public void paintedFePipeKeepsItsFeOrRfBaseName() {
        ItemStack stack = colouredPipe(BCTransportItems.PIPE_WOOD_RF.get(), DyeColor.BLUE);

        TranslatableContents contents = translatable(stack.getItem().getName(stack));

        Assertions.assertEquals(PIPE_KEY, contents.getKey());
        TranslatableContents base = translatable(Assertions.assertInstanceOf(Component.class, contents.getArgs()[1]));
        Assertions.assertEquals(BCEnergyConfig.rfFeKey(stack.getItem().getDescriptionId()), base.getKey(),
                "the FE/RF wording switch must survive inside the coloured name");
    }

    @Test
    public void unpaintedPipeNameIsUnchanged() {
        ItemStack stack = new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get());

        TranslatableContents contents = translatable(stack.getItem().getName(stack));

        Assertions.assertEquals(stack.getItem().getDescriptionId(), contents.getKey());
        Assertions.assertEquals(0, contents.getArgs().length);
    }

    @Test
    public void chargedPaintbrushNameIsAColourThenBaseFormatKey() {
        ItemStack stack = ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), DyeColor.RED);

        TranslatableContents contents = translatable(stack.getItem().getName(stack));

        Assertions.assertEquals(BRUSH_KEY, contents.getKey(), "coloured brushes are named through the format key");
        Assertions.assertEquals(2, contents.getArgs().length, "format key takes the colour then the brush's own name");
        Assertions.assertEquals(ColourUtil.getTextFullTooltip(DyeColor.RED), textOf(contents.getArgs()[0]),
                "argument 0 is the (localised, chat-coloured) colour word");
        TranslatableContents base = translatable(Assertions.assertInstanceOf(Component.class, contents.getArgs()[1]));
        Assertions.assertEquals(stack.getItem().getDescriptionId(), base.getKey(),
                "argument 1 is the plain brush's translatable name");
    }

    @Test
    public void cleanPaintbrushNameIsUnchanged() {
        ItemStack stack = ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), null);

        TranslatableContents contents = translatable(stack.getItem().getName(stack));

        Assertions.assertEquals(stack.getItem().getDescriptionId(), contents.getKey());
        Assertions.assertEquals(0, contents.getArgs().length);
    }
}
