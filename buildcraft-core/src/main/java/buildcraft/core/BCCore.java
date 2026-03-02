package buildcraft.core;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;

// import buildcraft.lib.BCLib;
// import buildcraft.lib.BCLibItems;

@Mod(BCCore.MODID)
public class BCCore {
    public static final String MODID = "buildcraftcore";
    public static BCCore INSTANCE = null;

    public BCCore(IEventBus modEventBus) {
        INSTANCE = this;
        // BCLibItems.enableGuide();
        // BCLibItems.enableDebugger();

        BCCoreItems.init(modEventBus);
        BCCoreBlocks.init(modEventBus);

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
    }

    private void preInit(FMLCommonSetupEvent event) {
        // BCCoreConfig.preInit(cfgFolder);
        // CreativeTabBC tab = CreativeTabManager.createTab("buildcraft.main");

        BCCoreItems.preInit();
        BCCoreStatements.preInit();
        BCCoreRecipes.fmlPreInit();

        // BCCoreProxy.getProxy().fmlPreInit();
        // NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE,
        // BCCoreProxy.getProxy());
        // BCCoreConfig.saveConfigs();
    }

    private void init(FMLCommonSetupEvent event) {
        // BCCoreConfig.saveConfigs();
        // BCCoreProxy.getProxy().fmlInit();
    }

    private void postInit(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        // BCCoreConfig.saveConfigs();
        // BCCoreProxy.getProxy().fmlPostInit();
        // BCCoreConfig.postInit();
    }
}
