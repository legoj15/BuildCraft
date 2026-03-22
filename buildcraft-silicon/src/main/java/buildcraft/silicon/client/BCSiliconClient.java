package buildcraft.silicon.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import buildcraft.api.transport.pipe.PipeApiClient;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.client.model.FacadeItemModel;
import buildcraft.silicon.client.model.key.KeyPlugFacade;
import buildcraft.silicon.client.model.plug.PlugBakerFacade;
import buildcraft.silicon.client.render.RenderLaser;
import buildcraft.silicon.gui.GuiAdvancedCraftingTable;
import buildcraft.silicon.gui.GuiAssemblyTable;
import buildcraft.silicon.gui.GuiIntegrationTable;

public class BCSiliconClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");

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

    /**
     * Register facade baker and swap the vanilla facade item model with FacadeItemModel.
     * This event fires after all models are baked and after EntityRenderersEvent has
     * completed, so PipeApiClient.registry is guaranteed to be set by Transport.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Register facade baker via API (fires after Transport's registerRenderers)
        if (PipeApiClient.registry != null) {
            PipeApiClient.registry.registerBaker(KeyPlugFacade.class, PlugBakerFacade.INSTANCE);
        } else {
            LOGGER.warn("[silicon.client] PipeApiClient.registry is null at ModifyBakingResult! "
                + "Facade in-world rendering will not work.");
        }

        // Swap vanilla item model with dynamic facade model
        var itemModels = event.getBakingResult().itemStackModels();
        Identifier facadeId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_FACADE.get());
        ItemModel vanillaModel = itemModels.get(facadeId);
        if (vanillaModel instanceof BlockModelWrapper wrapper) {
            itemModels.put(facadeId, new FacadeItemModel(wrapper));
        }
        FacadeItemModel.onModelBake();
    }
}

