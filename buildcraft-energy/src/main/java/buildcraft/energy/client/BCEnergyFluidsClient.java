package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergyFluids;

/**
 * Client-side fluid rendering extensions for all BuildCraft energy fluids.
 * Registered manually on the mod event bus from BCEnergy.
 *
 * Uses vanilla water textures + computed tint color for each fluid variant.
 */
public class BCEnergyFluidsClient {

    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");
    private static final Identifier WATER_OVERLAY = Identifier.withDefaultNamespace("block/water_overlay");

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            final int tint = entry.tintColor();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return WATER_STILL;
                }

                @Override
                public Identifier getFlowingTexture() {
                    return WATER_FLOW;
                }

                @Override
                public Identifier getOverlayTexture() {
                    return WATER_OVERLAY;
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            }, entry.fluidType().get());
        }
    }
}
