package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.facades.FacadeAPI;
import buildcraft.api.mj.MjAPI;

import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;

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
        BCSiliconMenuTypes.init(modEventBus);
        BCSiliconCreativeTabs.init(modEventBus);

        // Register pluggable definitions (facade, etc.)
        BCSiliconPlugs.preInit();

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.register(buildcraft.silicon.client.BCSiliconClient.class);
        }

        // Register capabilities, lifecycle events, and creative tab
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, this::addCreativeTabItems);

        // Register laser beam renderer on the game event bus (client only)
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(buildcraft.silicon.client.RenderLaser.class);
        }

        // MC 26.1: Register recipes on ServerAboutToStartEvent instead of commonSetup,
        // because ItemStack/FluidStack constructors now require bound registry components.
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);

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

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Register pluggable definitions with the transport registry.
            // This MUST happen after Transport's constructor has set PipeApi.pluggableRegistry.
            BCSiliconPlugs.registerAll();

            // Set up the facade API references
            FacadeAPI.facadeItem = BCSiliconItems.PLUG_FACADE.get();
            FacadeAPI.registry = FacadeStateManager.INSTANCE;

            // NOTE: FacadeStateManager.init() is NOT called here.
            // MC 26.1: ItemStack constructors require Holder.components() to be bound,
            // which only happens after registries freeze. Facade init is deferred to
            // ServerAboutToStartEvent / BuildCreativeModeTabContentsEvent.
        });
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        // MC 26.1: Facade + recipe initialization moved here from commonSetup because
        // ItemStack/FluidStack constructors now access Holder.components(),
        // which is only bound after registries finish freezing.
        FacadeStateManager.ensureInitialized();
        BCSiliconRecipes.init();
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

        // Gates are pluggables — they belong in the Pluggables tab
        if (event.getTabKey() == BCSiliconCreativeTabs.TRANSPORT_PLUGS_TAB_KEY) {
            buildcraft.silicon.item.ItemPluggableGate gateItem = BCSiliconItems.PLUG_GATE.get();
            for (buildcraft.silicon.gate.EnumGateMaterial material : buildcraft.silicon.gate.EnumGateMaterial.VALUES) {
                if (!material.canBeModified) continue;
                for (buildcraft.silicon.gate.EnumGateLogic logic : buildcraft.silicon.gate.EnumGateLogic.VALUES) {
                    for (buildcraft.silicon.gate.EnumGateModifier modifier : buildcraft.silicon.gate.EnumGateModifier.VALUES) {
                        event.accept(gateItem.getStack(new buildcraft.silicon.gate.GateVariant(logic, material, modifier)));
                    }
                }
            }
        }

        // Facades in their own tab
        if (event.getTabKey() == BCSiliconCreativeTabs.FACADE_TAB_KEY) {
            // Ensure facade states are scanned — this may be the first point where
            // ItemStack construction is safe (registry components are bound).
            FacadeStateManager.ensureInitialized();
            // Run visual deduplication using cached models from the last bake
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                buildcraft.silicon.client.BCSiliconClient.runDeferredDedup();
            }
            for (FacadeBlockStateInfo info : FacadeStateManager.validFacadeStates.values()) {
                if (info.isVisible) {
                    event.accept(BCSiliconItems.PLUG_FACADE.get()
                        .createItemStack(FacadeInstance.createSingle(info, false)));
                    event.accept(BCSiliconItems.PLUG_FACADE.get()
                        .createItemStack(FacadeInstance.createSingle(info, true)));
                }
            }
        }
    }
}
