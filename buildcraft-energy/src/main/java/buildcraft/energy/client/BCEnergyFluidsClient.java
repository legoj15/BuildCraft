package buildcraft.energy.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            final int tint = entry.tintColor();

            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return WATER_STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
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
