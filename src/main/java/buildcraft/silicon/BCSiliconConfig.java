package buildcraft.silicon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for BuildCraft Silicon (lasers, assembly/integration tables).
 */
public class BCSiliconConfig {

    public static ModConfigSpec.EnumValue<LaserTargetingMode> laserTargetingBehavior;

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
    }
}
