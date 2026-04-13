package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
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

import buildcraft.core.BCCore;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.FacadeStateManager;

/**
 * BuildCraft Silicon initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCSilicon {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCSilicon.class);

    public static void init(IEventBus modEventBus) {
        // Register all deferred registries
        BCSiliconBlocks.init(modEventBus);
        BCSiliconItems.init(modEventBus);
        BCSiliconBlockEntities.init(modEventBus);
        BCSiliconMenuTypes.init(modEventBus);
        BCSiliconCreativeTabs.init(modEventBus);

        // Register pluggable definitions (facade, etc.)
        BCSiliconPlugs.preInit();
        BCSiliconStatements.preInit();

        // Register client-side extensions on the mod event bus
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            buildcraft.silicon.client.BCSiliconClient.initClient(modEventBus);
        }

        // Register capabilities, lifecycle events, and creative tab
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            commonSetup(event);
        });
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });



        // MC 26.1: Register recipes on ServerAboutToStartEvent
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            onServerAboutToStart(event);
        });

        LOGGER.info("BuildCraft Silicon initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCSiliconBlockEntities.LASER.get(),
            (laser, direction) -> laser.getMjReceiver());
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, BCSiliconBlockEntities.LASER.get(),
            (laser, direction) -> laser.getMjReceiver());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BCSiliconPlugs.registerAll();
            FacadeAPI.facadeItem = BCSiliconItems.PLUG_FACADE.get();
            FacadeAPI.registry = FacadeStateManager.INSTANCE;
        });
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        FacadeStateManager.ensureInitialized();
        BCSiliconRecipes.init();
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {

        if (event.getTabKey() == BCSiliconCreativeTabs.TRANSPORT_PLUGS_TAB_KEY) {
            buildcraft.silicon.item.ItemPluggableGate gateItem = BCSiliconItems.PLUG_GATE.get();
            for (buildcraft.silicon.gate.EnumGateMaterial material : buildcraft.silicon.gate.EnumGateMaterial.VALUES) {
                if (!material.canBeModified) {
                    event.accept(gateItem.getStack(new buildcraft.silicon.gate.GateVariant(buildcraft.silicon.gate.EnumGateLogic.AND, material, buildcraft.silicon.gate.EnumGateModifier.NO_MODIFIER)));
                    continue;
                }
                for (buildcraft.silicon.gate.EnumGateLogic logic : buildcraft.silicon.gate.EnumGateLogic.VALUES) {
                    for (buildcraft.silicon.gate.EnumGateModifier modifier : buildcraft.silicon.gate.EnumGateModifier.VALUES) {
                        event.accept(gateItem.getStack(new buildcraft.silicon.gate.GateVariant(logic, material, modifier)));
                    }
                }
            }

            buildcraft.silicon.item.ItemPluggableLens lensItem = BCSiliconItems.PLUG_LENS.get();
            event.accept(lensItem.getStack(null, false));
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                event.accept(lensItem.getStack(color, false));
            }
            event.accept(lensItem.getStack(null, true));
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                event.accept(lensItem.getStack(color, true));
            }

            event.accept(new ItemStack(BCSiliconItems.PLUG_PULSAR.get()));
            event.accept(new ItemStack(BCSiliconItems.PLUG_LIGHT_SENSOR.get()));
            event.accept(new ItemStack(BCSiliconItems.PLUG_TIMER.get()));
        }

        if (event.getTabKey() == BCSiliconCreativeTabs.FACADE_TAB_KEY) {
            FacadeStateManager.ensureInitialized();
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
