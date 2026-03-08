package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergy;
import buildcraft.energy.BCEnergyFluids;

/**
 * Client-side fluid rendering extensions for oil.
 * This class is manually registered on the mod event bus from {@link BCEnergy}.
 *
 * Matches the 1.12 AtlasSpriteFluid approach: vanilla water textures recolored via tint.
 * Crude oil 1.12 colors: tex_light=0x505050, tex_dark=0x050505.
 * We use the average as a single tint since NeoForge only supports one tint value.
 */
public class BCEnergyFluidsClient {

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            // Use vanilla water textures — 1.12 AtlasSpriteFluid worked by recoloring
            // the water texture with light/dark oil colors. NeoForge achieves this via tint.
            private static final Identifier STILL = Identifier.withDefaultNamespace("block/water_still");
            private static final Identifier FLOWING = Identifier.withDefaultNamespace("block/water_flow");
            private static final Identifier OVERLAY = Identifier.withDefaultNamespace("block/water_overlay");

            @Override
            public Identifier getStillTexture() {
                return STILL;
            }

            @Override
            public Identifier getFlowingTexture() {
                return FLOWING;
            }

            @Override
            public Identifier getOverlayTexture() {
                return OVERLAY;
            }

            @Override
            public int getTintColor() {
                // Crude oil tint (ARGB): between 1.12 tex_light (0x505050) and tex_dark (0x050505)
                // Using 0x2A2A2A — a dark gray that's visible in both world blocks and bucket items
                return 0xFF2A2A2A;
            }
        }, BCEnergyFluids.OIL_FLUID_TYPE.get());
    }
}
