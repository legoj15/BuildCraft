package buildcraft.core;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import java.util.function.Supplier;
// import buildcraft.lib.BCLib;
// import buildcraft.lib.BCLibItems;
import buildcraft.lib.marker.MarkerCache;
import buildcraft.core.marker.PathCache;
import buildcraft.core.marker.VolumeCache;

@Mod(BCCore.MODID)
public class BCCore {
    public static final String MODID = "buildcraftcore";
    public static BCCore INSTANCE = null;

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
            .createDataComponents(Registries.DATA_COMPONENT_TYPE, BCCore.MODID);
    public static final Supplier<DataComponentType<SimpleFluidContent>> FLUID_CONTENT = DATA_COMPONENTS
            .registerComponentType("fluid_content", builder -> builder.persistent(SimpleFluidContent.CODEC)
                    .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    public BCCore(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        // BCLibItems.enableGuide();
        // BCLibItems.enableDebugger();

        BCCoreItems.ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BCCoreBlocks.init(modEventBus);
        BCCoreBlockEntities.init(modEventBus);
        BCCoreFeatures.init(modEventBus);
        BCCoreCreativeTabs.init(modEventBus);

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::buildCreativeTabContents);
    }

    private void preInit(FMLCommonSetupEvent event) {
        // BCCoreConfig.preInit(cfgFolder);
        // CreativeTabBC tab = CreativeTabManager.createTab("buildcraft.main");

        MarkerCache.registerCache(VolumeCache.INSTANCE);
        MarkerCache.registerCache(PathCache.INSTANCE);

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

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.Fluid.ITEM,
                (stack, ctx) -> new net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler(ctx,
                        FLUID_CONTENT.get(), buildcraft.core.item.ItemFragileFluidContainer.MAX_FLUID_HELD) {
                    @Override
                    public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource,
                            int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
                        return 0; // cannot fill!
                    }
                },
                BCCoreItems.FRAGILE_FLUID_CONTAINER);
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(BCCoreItems.WRENCH);
            event.accept(BCCoreItems.PAINTBRUSH);
            event.accept(BCCoreItems.GOGGLES);
            event.accept(BCCoreItems.MARKER_CONNECTOR);
            event.accept(BCCoreItems.VOLUME_BOX);
            event.accept(BCCoreItems.MAP_LOCATION);
            event.accept(BCCoreItems.SPRING_WATER);
            event.accept(BCCoreItems.SPRING_OIL);
            event.accept(BCCoreItems.DECORATED_DESTROY);
            event.accept(BCCoreItems.DECORATED_BLUEPRINT);
            event.accept(BCCoreItems.DECORATED_TEMPLATE);
            event.accept(BCCoreItems.DECORATED_PAPER);
            event.accept(BCCoreItems.DECORATED_LEATHER);
            event.accept(BCCoreItems.DECORATED_LASER);
            event.accept(BCCoreItems.MARKER_VOLUME);
            event.accept(BCCoreItems.MARKER_PATH);
            event.accept(BCCoreItems.ENGINE_REDSTONE);
            event.accept(BCCoreItems.ENGINE_CREATIVE);
        }
    }
}
