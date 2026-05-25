/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

/**
 * Cycling container-contents handling state for {@link buildcraft.builders.tile.TileBuilder}.
 * Controls what the Builder does with the inventory contents of container blocks (chests,
 * barrels, hoppers, shulkers, the furnace family, dispensers/droppers, brewing stands, …) that
 * the Architect captured into the blueprint.
 *
 * <p>The Architect always captures contents; this toggle is purely a build-time decision so the
 * same blueprint can be built either way.
 *
 * <ul>
 *   <li>{@link #INCLUDE} (default) — the placed container is filled with the captured items, and
 *       those items appear in the Builder's required-items list (so the player pays for them out
 *       of the resource grid). Net effect: items conserved. This is the only mode under which
 *       captured contents survive into the placed world.</li>
 *   <li>{@link #IGNORE} — the placed container is empty. The captured inventory is stripped from
 *       the block-entity NBT at placement time, and the items_list extractor's contribution to
 *       the required-items list is filtered out so the player doesn't have to source contents
 *       they don't intend to receive. Cosmetic block-entity data (custom name, lock string, loot
 *       table seed, furnace cook progress, …) is preserved — only the {@code Items} tag goes.</li>
 * </ul>
 */
public enum EnumContainerContentsMode {
    INCLUDE("gui.buildcraftunofficial.builder.contentsmode.include"),
    IGNORE("gui.buildcraftunofficial.builder.contentsmode.ignore");

    private final String tooltipKey;

    EnumContainerContentsMode(String tooltipKey) {
        this.tooltipKey = tooltipKey;
    }

    public EnumContainerContentsMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String tooltipKey() {
        return tooltipKey;
    }

    public static EnumContainerContentsMode fromOrdinal(int ord) {
        if (ord < 0 || ord >= values().length) return INCLUDE;
        return values()[ord];
    }
}
