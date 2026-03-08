package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergy;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.client.sprite.FluidRecolorSpriteSource;

/**
 * Client-side fluid rendering extensions for all BuildCraft energy fluids.
 * Registered manually on the mod event bus from BCEnergy.
 *
 * Uses runtime-generated textures via {@link FluidRecolorSpriteSource} which
 * replicates the 1.12 AtlasSpriteFluid recoloring at atlas stitching time.
 */
public class BCEnergyFluidsClient {

    @SubscribeEvent
    public static void onRegisterSpriteSourceTypes(RegisterSpriteSourcesEvent event) {
        event.register(FluidRecolorSpriteSource.ID, FluidRecolorSpriteSource.CODEC);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (BCEnergyFluids.FluidEntry entry : BCEnergyFluids.ALL) {
            final String name = entry.name();
            final Identifier still = Identifier.fromNamespaceAndPath(
                    BCEnergy.MODID, "block/fluid/" + name + "_still");
            final Identifier flow = Identifier.fromNamespaceAndPath(
                    BCEnergy.MODID, "block/fluid/" + name + "_flow");

            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return still;
                }

                @Override
                public Identifier getFlowingTexture() {
                    return flow;
                }

                @Override
                public int getTintColor() {
                    // White — textures are already recolored at runtime
                    return 0xFFFFFFFF;
                }
            }, entry.fluidType().get());
        }
    }
}
