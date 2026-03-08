package buildcraft.energy.client;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import buildcraft.energy.BCEnergy;
import buildcraft.energy.BCEnergyFluids;

@EventBusSubscriber(modid = BCEnergy.MODID, value = Dist.CLIENT)
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
                // Dark oil tint (ARGB) — from 1.12 texDark=0x050505
                return 0xFF050505;
            }
        }, BCEnergyFluids.OIL_FLUID_TYPE.get());
    }
}
