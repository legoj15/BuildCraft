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
 */
public class BCEnergyFluidsClient {

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            private static final Identifier STILL = Identifier.fromNamespaceAndPath(BCEnergy.MODID, "block/fluid/oil_still");
            private static final Identifier FLOWING = Identifier.fromNamespaceAndPath(BCEnergy.MODID, "block/fluid/oil_flow");

            @Override
            public Identifier getStillTexture() {
                return STILL;
            }

            @Override
            public Identifier getFlowingTexture() {
                return FLOWING;
            }

            @Override
            public int getTintColor() {
                // White tint — textures are already pre-colored with oil's dark palette
                return 0xFFFFFFFF;
            }
        }, BCEnergyFluids.OIL_FLUID_TYPE.get());
    }
}
