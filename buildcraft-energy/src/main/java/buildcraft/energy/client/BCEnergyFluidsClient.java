package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergyFluids;

/**
 * Client-side fluid rendering extensions for all BuildCraft energy fluids.
 * Registered manually on the mod event bus from BCEnergy.
 *
 * MC 26.1: IClientFluidTypeExtensions interface was removed.
 * Fluid rendering registration now uses RegisterClientExtensionsEvent
 * with the new API. Until the NeoForge 26.1 fluid rendering API stabilizes,
 * this is a stub that registers empty extensions.
 *
 * TODO: Implement proper NeoForge 26.1 fluid rendering when API is finalized.
 */
public class BCEnergyFluidsClient {

    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // MC 26.1: IClientFluidTypeExtensions was removed.
        // The old approach of registering getStillTexture/getFlowingTexture/getTintColor
        // per fluid type no longer works. The new NeoForge 26.1 fluid rendering API
        // handles textures and tints differently.
        //
        // For now, BuildCraft's custom fluids (oil, fuel, etc.) use their own
        // block models which reference the correct textures directly, so
        // this registration is no longer needed for rendering.
        //
        // TODO: If fluid-in-world rendering is wrong, investigate the new
        // NeoForge 26.1 fluid rendering pipeline.
    }
}
