package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.facades.FacadeAPI;
import buildcraft.api.mj.MjAPI;
import buildcraft.lib.mj.MjBatteryEnergyHandler;

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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCSiliconBlockEntities.LASER.get(),
            (laser, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(laser.getBattery()));

        // Item handlers — lets item pipes connect to and exchange items with the laser tables.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCSiliconBlockEntities.ASSEMBLY_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
        // Integration Table is dev-only — absent from public builds.
        if (BCSiliconBlockEntities.INTEGRATION_TABLE != null) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, BCSiliconBlockEntities.INTEGRATION_TABLE.get(),
                (table, direction) -> table.getItemHandler(direction));
        }
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCSiliconBlockEntities.ADVANCED_CRAFTING_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
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

            net.minecraft.world.item.DyeColor[] legacyOrder = new net.minecraft.world.item.DyeColor[] {
                net.minecraft.world.item.DyeColor.BLACK,
                net.minecraft.world.item.DyeColor.RED,
                net.minecraft.world.item.DyeColor.GREEN,
                net.minecraft.world.item.DyeColor.BROWN,
                net.minecraft.world.item.DyeColor.BLUE,
                net.minecraft.world.item.DyeColor.PURPLE,
                net.minecraft.world.item.DyeColor.CYAN,
                net.minecraft.world.item.DyeColor.LIGHT_GRAY,
                net.minecraft.world.item.DyeColor.GRAY,
                net.minecraft.world.item.DyeColor.PINK,
                net.minecraft.world.item.DyeColor.LIME,
                net.minecraft.world.item.DyeColor.YELLOW,
                net.minecraft.world.item.DyeColor.LIGHT_BLUE,
                net.minecraft.world.item.DyeColor.MAGENTA,
                net.minecraft.world.item.DyeColor.ORANGE,
                net.minecraft.world.item.DyeColor.WHITE
            };

            // Colored Lenses (in legacy order)
            for (net.minecraft.world.item.DyeColor color : legacyOrder) {
                event.accept(lensItem.getStack(color, false));
            }

            // Colored Filters (in legacy order)
            for (net.minecraft.world.item.DyeColor color : legacyOrder) {
                event.accept(lensItem.getStack(color, true));
            }

            // Clear Lens and Clear Filter
            event.accept(lensItem.getStack(null, false));
            event.accept(lensItem.getStack(null, true));

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
