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
 *       {@code WATERLOGGED} and the fluid is a water source, it's placed as waterlogged
 *       (preserving the water "for free"); otherwise the fluid is destroyed first. Positions
 *       whose schematic entry is fluid (water-feature blueprints) keep their fluid intact, and
 *       waterlogged blocks captured-dry-in-schematic stay wet in the world (the lenient
 *       "world wet, schematic dry" comparison in {@link SchematicBlockDefault#isBuilt}).</li>
 *   <li>{@link #CLEAR} — clear all fluids in the build area, full stop. Source AND flowing,
 *       schematic-air positions AND schematic-fluid positions (blueprint-fluid is treated as
 *       schematic-air for classification purposes), waterlogged blocks (the WATERLOGGED
 *       property gets toggled off in-place to match the schematic's dry capture, no item
 *       extracted from inventory). Subsequent placements wait until every fluid in the area is
 *       cleared, except waterlog-clear-only places which run alongside mopping because they
 *       contribute to it. Use REPLACE if you want a water-feature blueprint to preserve its
 *       water; CLEAR is the "I want this area dry no matter what" mode.</li>
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
