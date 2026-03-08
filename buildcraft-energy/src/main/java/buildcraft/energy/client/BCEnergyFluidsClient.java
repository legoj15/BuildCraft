package buildcraft.energy.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.energy.BCEnergyFluids;

/**
 * Client-side fluid rendering extensions for all BuildCraft energy fluids.
 * Registered manually on the mod event bus from BCEnergy.
 *
 * Uses vanilla water textures tinted with each fluid's color (averaged
 * from the 1.12 tex_light and tex_dark values).
 */
public class BCEnergyFluidsClient {

    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");

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
                public int getTintColor() {
                    return tint;
                }

                @Override
                public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    return tint;
                }

                @Override
                public int getTintColor(FluidStack stack) {
                    return tint;
                }
            }, entry.fluidType().get());
        }
    }
}
