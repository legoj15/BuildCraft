/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for BuildCraft Silicon (lasers, assembly/integration tables).
 */
public class BCSiliconConfig {

    public static ModConfigSpec.EnumValue<LaserTargetingMode> laserTargetingBehavior;

    /** See {@link #buildGeneral} for the full semantics. */
    public static ModConfigSpec.BooleanValue disableFacadeGeneration;

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
    }
}
