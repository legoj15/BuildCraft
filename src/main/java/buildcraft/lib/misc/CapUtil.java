package buildcraft.lib.misc;

import javax.annotation.Nonnull;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Capability tokens for BuildCraft's internal capability routing.
 *
 * In NeoForge 1.21.11 the capability system uses typed BlockCapability tokens
 * rather than the old CapabilityInject/@CapabilityInject approach. These
 * constants let flow classes stay capability-agnostic while TilePipeHolder
 * routes them to the actual NeoForge level.getCapability() calls.
 *
 * For MJ capabilities BuildCraft defines its own tokens in MjAPI.
 * For items and fluids we wrap the NeoForge capability tokens. */
public class CapUtil {
    /** Token for item handler capability. Routes to Capabilities.Item.BLOCK. */
    @Nonnull
    public static final Object CAP_ITEMS = Capabilities.Item.BLOCK;

    /** Token for fluid handler capability. Routes to Capabilities.Fluid.BLOCK. */
    @Nonnull
    public static final Object CAP_FLUIDS = Capabilities.Fluid.BLOCK;

    /** Token for energy handler capability. Routes to Capabilities.Energy.BLOCK. */
    @Nonnull
    public static final Object CAP_ENERGY = Capabilities.Energy.BLOCK;
}
