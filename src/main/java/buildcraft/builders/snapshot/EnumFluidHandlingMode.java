/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cycling fluid-handling state for {@link buildcraft.builders.tile.TileBuilder}. Controls
 * what the Builder does when a blueprint position intersects an in-world fluid.
 *
 * <ul>
 *   <li>{@link #NO_REPLACE} — original behavior: any position holding a fluid (flowing or
 *       source) is silently skipped.</li>
 *   <li>{@link #REPLACE} — the schematic block is placed. If the block supports vanilla
 *       {@code WATERLOGGED} and the fluid is a water source, it's placed as waterlogged;
 *       otherwise the fluid is destroyed first. Positions whose schematic entry is air are
 *       still left alone (so REPLACE ≠ CLEAR per user intent).</li>
 *   <li>{@link #CLEAR} — same as REPLACE, plus fluid SOURCES at schematic-air positions are
 *       also destroyed. Flowing fluid drains naturally once sources are gone. Positions where
 *       the blueprint itself specifies a fluid are preserved — this is what keeps CLEAR from
 *       oscillating on water-feature blueprints.</li>
 * </ul>
 */
public enum EnumFluidHandlingMode {
    NO_REPLACE(() -> new ItemStack(Items.BARRIER),
               "gui.buildcraftunofficial.builder.fluidmode.no_replace"),
    REPLACE   (() -> new ItemStack(Items.BRICKS),
               "gui.buildcraftunofficial.builder.fluidmode.replace"),
    CLEAR     (() -> new ItemStack(Items.BUCKET),
               "gui.buildcraftunofficial.builder.fluidmode.clear");

    private final Supplier<ItemStack> iconSupplier;
    private final String tooltipKey;

    EnumFluidHandlingMode(Supplier<ItemStack> iconSupplier, String tooltipKey) {
        this.iconSupplier = iconSupplier;
        this.tooltipKey = tooltipKey;
    }

    public EnumFluidHandlingMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ItemStack icon() {
        return iconSupplier.get();
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public static EnumFluidHandlingMode fromOrdinal(int ord) {
        if (ord < 0 || ord >= values().length) return NO_REPLACE;
        return values()[ord];
    }
}
