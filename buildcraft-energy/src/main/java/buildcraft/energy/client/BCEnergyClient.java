package buildcraft.energy.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.lib.client.render.tile.RenderEngine_BC8;

import net.minecraft.resources.Identifier;

public class BCEnergyClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyMenuTypes.ENGINE_STONE.get(), ScreenEngineStone::new);
        event.register(BCEnergyMenuTypes.ENGINE_IRON.get(), ScreenEngineIron::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        var trunkMap = RenderEngine_BC8.defaultTrunkTextures();
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.ENGINE_STONE.get(),
                ctx -> new RenderEngine_BC8(
                        Identifier.parse("buildcraftenergy:block/engine/stone_back"),
                        Identifier.parse("buildcraftenergy:block/engine/stone_side"),
                        Identifier.parse("buildcraftcore:block/engine/chamber_base"),
                        trunkMap));
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.ENGINE_IRON.get(),
                ctx -> new RenderEngine_BC8(
                        Identifier.parse("buildcraftenergy:block/engine/iron_back"),
                        Identifier.parse("buildcraftenergy:block/engine/iron_side"),
                        Identifier.parse("buildcraftcore:block/engine/chamber_base"),
                        trunkMap));
    }
}
