package buildcraft.energy.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.BCEnergyModels;
import buildcraft.energy.client.gui.ScreenEngineStone;
import buildcraft.energy.client.gui.ScreenEngineIron;
import buildcraft.energy.client.gui.ScreenEngineFE;
import buildcraft.energy.client.gui.ScreenDynamoMJ;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.tile.RenderEngine_BC8;

public class BCEnergyClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCEnergyMenuTypes.ENGINE_STONE.get(), ScreenEngineStone::new);
        event.register(BCEnergyMenuTypes.ENGINE_IRON.get(), ScreenEngineIron::new);
        event.register(BCEnergyMenuTypes.ENGINE_FE.get(), ScreenEngineFE::new);
        event.register(BCEnergyMenuTypes.DYNAMO_MJ.get(), ScreenDynamoMJ::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.ENGINE_STONE.get(),
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineStone_BC8 stone) {
                        return BCEnergyModels.getStoneEngineQuads(stone, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.ENGINE_IRON.get(),
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineIron_BC8 iron) {
                        return BCEnergyModels.getIronEngineQuads(iron, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.ENGINE_FE.get(),
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileEngineFE fe) {
                        return BCEnergyModels.getFeEngineQuads(fe, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
        event.registerBlockEntityRenderer(BCEnergyBlockEntities.DYNAMO_MJ.get(),
                ctx -> new RenderEngine_BC8((engine, pt) -> {
                    if (engine instanceof TileDynamoMJ dynamo) {
                        return BCEnergyModels.getDynamoQuads(dynamo, pt);
                    }
                    return MutableQuad.EMPTY_ARRAY;
                }));
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(buildcraft.energy.client.BCEnergyFluidsClient.class);
        modEventBus.register(BCEnergyClient.class);
    }
}
