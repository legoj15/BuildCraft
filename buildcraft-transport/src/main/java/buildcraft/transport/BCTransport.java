package buildcraft.transport;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BCTransport.MODID)
public class BCTransport {
    public static final String MODID = "buildcrafttransport";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCTransport.class);

    public static BCTransport INSTANCE;

    public BCTransport(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCTransportBlocks.init(modEventBus);
        BCTransportItems.init(modEventBus);
        BCTransportBlockEntities.init(modEventBus);
        BCTransportMenuTypes.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.transport.client.BCTransportClient.class);
        }

        // Register creative tab — LOW priority so transport items appear after robotics (Zone Planner)
        modEventBus.addListener(EventPriority.LOW, this::addCreativeTabItems);

        LOGGER.info("BuildCraft Transport initialized");
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            event.accept(new ItemStack(BCTransportItems.FILTERED_BUFFER.get()));
        }
    }
}
