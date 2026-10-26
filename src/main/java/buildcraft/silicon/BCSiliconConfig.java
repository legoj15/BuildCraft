/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for BuildCraft Silicon (lasers, assembly/integration tables).
 */
public class BCSiliconConfig {

    public static ModConfigSpec.EnumValue<LaserTargetingMode> laserTargetingBehavior;

    /** See {@link #buildGeneral} for the full semantics. */
    public static ModConfigSpec.BooleanValue disableFacadeGeneration;

    /** The item tags whose members the Advanced Crafting Table's cycle-output button offers as variants. */
    public static final List<String> DEFAULT_CRAFTING_VARIANT_TAGS = List.of(
        "minecraft:planks",
        "minecraft:logs",
        "minecraft:wool",
        "minecraft:stone_crafting_materials",
        "c:cobblestones",
        "minecraft:wooden_slabs");

    /** See {@link #buildGeneral}; read through {@link #getCraftingVariantTags()}. */
    public static ModConfigSpec.ConfigValue<List<? extends String>> craftingVariantTags;

    /**
     * How a Laser searches for nearby tables (Assembly / Integration / Advanced Crafting) to power.
     * <ul>
     *   <li>{@link #LOS_CONE} — the modern BuildCraft 8.0.x behaviour: a 6-block line-of-sight cone
     *       projecting from the laser face.</li>
     *   <li>{@link #BOX} — the legacy BuildCraft 7.x behaviour: a 5-block box reaching the front and
     *       sides of the laser, ignoring line-of-sight.</li>
     * </ul>
     */
    public enum LaserTargetingMode {
        LOS_CONE,
        BOX
    }

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        laserTargetingBehavior = builder
                .comment(
                        "How a Laser searches for nearby tables (e.g. the Assembly Table) to send power to.",
                        "LOS Cone requires laser receivers to be within a 6 block cone shaped volume in front of the laser with nothing blocking them. (Default 8.0.x Behavior)",
                        "Box allows lasers to reach anywhere within 5 blocks from the front or sides of the laser. (Legacy 7.1.x Behavior)"
                )
                .defineEnum("laserTargetingBehavior", LaserTargetingMode.LOS_CONE);
        disableFacadeGeneration = builder
                .comment(
                        "Completely disables BuildCraft's automatic facade generation: no block-registry enumeration",
                        "scan at startup, no generated facade assembly recipes, no facade variants in the creative",
                        "tab, no facade entries in JEI, and no client-side facade texture deduplication scan. Intended",
                        "for players who use another (universal) facades mod.",
                        "The facade item and plug itself stay registered so worlds and inventories containing them",
                        "remain safe, and facades that were already placed keep their appearance. In multiplayer,",
                        "COMMON configs are not synced — set this on the server and on clients separately. Like other",
                        "load-time toggles this takes effect on the next launch or world load."
                )
                .define("disableFacadeGeneration", false);
        craftingVariantTags = builder
                .comment(
                        "Item tags whose members the Advanced Crafting Table offers as variants of the recipe in its",
                        "blueprint grid. When the grid holds an item from one of these tags, the table's cycle-output",
                        "button also steps through the recipes the grid would match with that item swapped for another",
                        "member of the tag (oak planks in a stair pattern also offer the other woods' stairs). Picking one",
                        "rewrites the blueprint to that material. Entries are item tag ids such as minecraft:planks.",
                        "Empty the list to turn variants off; the cycle then offers only same-grid recipe conflicts."
                )
                .defineListAllowEmpty(
                        "craftingVariantTags",
                        DEFAULT_CRAFTING_VARIANT_TAGS,
                        () -> "",
                        s -> s instanceof String
                );
    }

    /** The configured variant tags, read live. Falls back to the defaults while the config is not loaded (headless
     *  unit tests, very early startup). */
    public static List<? extends String> getCraftingVariantTags() {
        if (craftingVariantTags != null) {
            try {
                return craftingVariantTags.get();
            } catch (IllegalStateException notLoaded) {
                // The spec has not been loaded yet: use the defaults below.
            }
        }
        return DEFAULT_CRAFTING_VARIANT_TAGS;
    }
}
