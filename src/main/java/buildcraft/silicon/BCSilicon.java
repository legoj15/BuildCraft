/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.api.facades.FacadeAPI;
import buildcraft.lib.mj.MjBatteryEnergyHandler;

import buildcraft.core.BCCore;
import buildcraft.platform.Platform;
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
        BCSiliconRecipeSerializers.init(modEventBus);

        // Register pluggable definitions (facade, etc.)
        BCSiliconPlugs.preInit();
        BCSiliconStatements.preInit();

        // Register client-side extensions on the mod event bus
        if (Platform.loader().isPhysicalClient()) {
            buildcraft.silicon.client.BCSiliconClient.initClient(modEventBus);
        }

        // Register capabilities, lifecycle events, and creative tab
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });
        Platform.lifecycle().onCommonSetupOnMain(BCSilicon::commonSetup);
        modEventBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (BuildCreativeModeTabContentsEvent event) -> {
            addCreativeTabItems(event);
        });



        // MC 26.1: Register recipes on ServerAboutToStartEvent
        Platform.lifecycle().onServerAboutToStart(server -> onServerAboutToStart());

        LOGGER.info("BuildCraft Silicon initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Version-neutral capability tokens: Transfer-API on 1.21.10+, classic handler tokens on 1.21.1.
        //? if >=1.21.10 {
        var itemCap = Capabilities.Item.BLOCK;
        var energyCap = Capabilities.Energy.BLOCK;
        //?} else {
        /*var itemCap = Capabilities.ItemHandler.BLOCK;
        var energyCap = Capabilities.EnergyStorage.BLOCK;*/
        //?}
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(event, BCSiliconBlockEntities.LASER.get(),
            laser -> laser.getMjReceiver(), energyCap,
            laser -> MjBatteryEnergyHandler.createIfRfEnabled(laser.getBattery()));

        // Item handlers — lets item pipes connect to and exchange items with the laser tables.
        event.registerBlockEntity(itemCap, BCSiliconBlockEntities.ASSEMBLY_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
        event.registerBlockEntity(itemCap, BCSiliconBlockEntities.INTEGRATION_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
        event.registerBlockEntity(itemCap, BCSiliconBlockEntities.PROGRAMMING_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
        event.registerBlockEntity(itemCap, BCSiliconBlockEntities.ADVANCED_CRAFTING_TABLE.get(),
            (table, direction) -> table.getItemHandler(direction));
    }

    /** Common setup, on the main thread. */
    private static void commonSetup() {
        BCSiliconPlugs.registerAll();
        FacadeAPI.facadeItem = BCSiliconItems.PLUG_FACADE.get();
        FacadeAPI.registry = FacadeStateManager.INSTANCE;
    }

    private static void onServerAboutToStart() {
        FacadeStateManager.ensureInitialized();
        BCSiliconRecipes.ensureInitialized();
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
            // ensureInitialized is needed for the multiplayer-client case (ServerAboutToStartEvent
            // only fires on the integrated server; a connected client gets no other init trigger).
            FacadeStateManager.ensureInitialized();
            if (FacadeStateManager.isFacadeGenerationDisabled()) {
                return; // no generated variants and nothing to dedup; the tab itself stays registered (empty)
            }
            if (Platform.loader().isPhysicalClient()) {
                // Primary dedup trigger is BCSiliconClient.GameBus.onClientLoggingIn (deterministic,
                // off the JEI first-screen-open critical path). This call is the F3+T safety net:
                // after a resource reload the model cache refills and the next tab rebuild re-dedups
                // against the new textures. In steady state this is a no-op.
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
