package buildcraft.silicon.client;

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
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BCSiliconBlockEntities.LASER.get(), RenderLaser::new);
        // Register pluggable bakers into the transport API registry
        if (PipeApiClient.registry != null) {
            PipeApiClient.registry.registerBaker(KeyPlugFacade.class, PlugBakerFacade.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(BCSiliconMenuTypes.ASSEMBLY_TABLE.get(), GuiAssemblyTable::new);
        event.register(BCSiliconMenuTypes.INTEGRATION_TABLE.get(), GuiIntegrationTable::new);
        event.register(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), GuiAdvancedCraftingTable::new);
    }

    /**
     * Swap the vanilla-baked plug_facade item model with FacadeItemModel
     * for dynamic per-block-state rendering.
     */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var itemModels = event.getBakingResult().itemStackModels();
        Identifier facadeId = BuiltInRegistries.ITEM.getKey(BCSiliconItems.PLUG_FACADE.get());
        ItemModel vanillaModel = itemModels.get(facadeId);
        if (vanillaModel instanceof BlockModelWrapper wrapper) {
            itemModels.put(facadeId, new FacadeItemModel(wrapper));
        }
        FacadeItemModel.onModelBake();
    }
}
