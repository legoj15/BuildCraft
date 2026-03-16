package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.mj.MjAPI;

@Mod(BCSilicon.MODID)
public class BCSilicon {
    public static final String MODID = "buildcraftsilicon";
    private static final Logger LOGGER = LoggerFactory.getLogger(BCSilicon.class);

    public static BCSilicon INSTANCE;

    public BCSilicon(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        // Register all deferred registries
        BCSiliconBlocks.init(modEventBus);
        BCSiliconItems.init(modEventBus);
        BCSiliconBlockEntities.init(modEventBus);

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.silicon.client.BCSiliconClient.class);
        }

        // Register capabilities and creative tab
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::addCreativeTabItems);

        LOGGER.info("BuildCraft Silicon initialized");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // MJ receiver capability — allows engines to send power to the laser
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCSiliconBlockEntities.LASER.get(),
            (laser, direction) -> laser.getMjReceiver()
        );

        // MJ connector capability — allows visual connection checks
        event.registerBlockEntity(
            MjAPI.CAP_CONNECTOR,
            BCSiliconBlockEntities.LASER.get(),
            (laser, direction) -> laser.getMjReceiver()
        );
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == buildcraft.core.BCCoreCreativeTabs.MAIN_TAB_KEY) {
            // Tables (after laser)
            event.accept(new ItemStack(BCSiliconItems.LASER.get()));
            event.accept(new ItemStack(BCSiliconItems.ASSEMBLY_TABLE.get()));
            event.accept(new ItemStack(BCSiliconItems.ADVANCED_CRAFTING_TABLE.get()));
            event.accept(new ItemStack(BCSiliconItems.INTEGRATION_TABLE.get()));

            // Chipsets
            event.accept(new ItemStack(BCSiliconItems.REDSTONE_RED_CHIPSET.get()));
            event.accept(new ItemStack(BCSiliconItems.REDSTONE_IRON_CHIPSET.get()));
            event.accept(new ItemStack(BCSiliconItems.REDSTONE_GOLD_CHIPSET.get()));
            event.accept(new ItemStack(BCSiliconItems.REDSTONE_QUARTZ_CHIPSET.get()));
            event.accept(new ItemStack(BCSiliconItems.REDSTONE_DIAMOND_CHIPSET.get()));

            // Gate Copier
            event.accept(new ItemStack(BCSiliconItems.GATE_COPIER.get()));
        }
    }
}
