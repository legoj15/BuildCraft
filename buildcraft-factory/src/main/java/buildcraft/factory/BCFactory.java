package buildcraft.factory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCFactory.MODID)
public class BCFactory {
    public static final String MODID = "buildcraftfactory";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCFactory.class);

    public static BCFactory INSTANCE;

    public BCFactory(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCFactoryBlocks.init(modEventBus);
        BCFactoryItems.init(modEventBus);
        BCFactoryBlockEntities.init(modEventBus);
        BCFactoryMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.factory.client.BCFactoryClient.class);
        }

        // Creative tab items
        modEventBus.addListener(this::addCreativeTabItems);

        LOGGER.info("BuildCraft Factory initialized");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(new ItemStack(BCFactoryItems.AUTOWORKBENCH_ITEM.get()));
        }
    }
}
