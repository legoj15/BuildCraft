package buildcraft.factory.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.client.gui.GuiAutoCraftItems;
import buildcraft.factory.client.gui.GuiChute;
import buildcraft.factory.client.gui.GuiDistiller;
import buildcraft.factory.client.gui.GuiTank;
import buildcraft.factory.client.render.RenderDistiller;
import buildcraft.factory.client.render.RenderHeatExchange;
import buildcraft.factory.client.render.RenderMiningWell;
import buildcraft.factory.client.render.RenderPump;
import buildcraft.factory.client.render.RenderTank;

public class BCFactoryClient {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS.get(), GuiAutoCraftItems::new);
        event.register(BCFactoryMenuTypes.TANK.get(), GuiTank::new);
        event.register(BCFactoryMenuTypes.CHUTE.get(), GuiChute::new);
        event.register(BCFactoryMenuTypes.DISTILLER.get(), GuiDistiller::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCFactoryBlockEntities.TANK.get(), RenderTank::new);
        event.registerBlockEntityRenderer(BCFactoryBlockEntities.DISTILLER.get(), RenderDistiller::new);
        event.registerBlockEntityRenderer(BCFactoryBlockEntities.HEAT_EXCHANGE.get(), RenderHeatExchange::new);
        event.registerBlockEntityRenderer(BCFactoryBlockEntities.PUMP.get(), RenderPump::new);
        event.registerBlockEntityRenderer(BCFactoryBlockEntities.MINING_WELL.get(), RenderMiningWell::new);
    }

    public static void initClient(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BCFactoryClient.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> buildcraft.factory.client.render.TubeRenderer.onRenderLevel(event)
        );
    }
}
