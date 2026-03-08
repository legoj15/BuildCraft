package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergy;
import buildcraft.energy.BCEnergyFluids;

/**
 * Client-side fluid rendering extensions for oil at all temperature levels.
 * Registered manually on the mod event bus from {@link BCEnergy}.
 *
 * Uses vanilla water textures + tint color (matching 1.12 AtlasSpriteFluid approach).
 * Higher heat → slightly lighter tint (more visible oil).
 */
public class BCEnergyFluidsClient {

    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");
    private static final Identifier WATER_OVERLAY = Identifier.withDefaultNamespace("block/water_overlay");

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // Oil (Cool) — heat_0: darkest tint
        registerWaterTintedFluid(event, BCEnergyFluids.OIL_FLUID_TYPE.get(), 0xFF2A2A2A);

        // Oil (Hot) — heat_1: slightly lighter
        registerWaterTintedFluid(event, BCEnergyFluids.OIL_HEAT_1_FLUID_TYPE.get(), 0xFF3A3A3A);

        // Oil (Searing) — heat_2: lighter still
        registerWaterTintedFluid(event, BCEnergyFluids.OIL_HEAT_2_FLUID_TYPE.get(), 0xFF4A4A4A);
    }

    /**
     * Registers a fluid type that renders as tinted vanilla water textures.
     *
     * @param event the registration event
     * @param fluidType the fluid type to register
     * @param tintColor ARGB tint color (applied multiplicatively to water textures)
     */
    private static void registerWaterTintedFluid(RegisterClientExtensionsEvent event,
                                                  net.neoforged.neoforge.fluids.FluidType fluidType,
                                                  int tintColor) {
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
                return tintColor;
            }
        }, fluidType);
    }
}
