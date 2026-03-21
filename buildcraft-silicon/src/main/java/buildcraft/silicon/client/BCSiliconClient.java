package buildcraft.silicon.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.client.render.RenderLaser;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiIntegrationTable;

public class BCSiliconClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCSiliconBlockEntities.LASER.get(), RenderLaser::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), GuiAssemblyTable::new);
        event.register(BCSiliconMenuTypes.INTEGRATION_TABLE.get(), GuiIntegrationTable::new);
        event.register(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), GuiAdvancedCraftingTable::new);
    }
}

