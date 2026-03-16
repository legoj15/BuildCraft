/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import buildcraft.api.filler.FillerManager;
import buildcraft.api.mj.MjAPI;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.builders.registry.FillerRegistry;
import buildcraft.core.BCCoreCreativeTabs;
import buildcraft.lib.mj.MjBatteryEnergyHandler;

@Mod(BCBuilders.MODID)
public class BCBuilders {
    public static final String MODID = "buildcraftbuilders";

    public static BCBuilders INSTANCE = null;

    public BCBuilders(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        BCBuildersBlocks.init(modEventBus);
        BCBuildersItems.init(modEventBus);
        BCBuildersBlockEntities.init(modEventBus);
        BCBuildersMenuTypes.init(modEventBus);

        BCBuildersConfig.register(modContainer);

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::buildCreativeTabContents);
        modEventBus.addListener(this::registerMenuScreens);

        // Register quarry rendering via game event bus (not mod bus)
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
            event -> BCBuildersEventDist.INSTANCE.renderAllQuarries(event));
    }


    private void preInit(FMLCommonSetupEvent event) {
        BCBuildersRecipes.init();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // MJ receiver capability: quarry accepts MJ power on any face
        event.registerBlockEntity(
            MjAPI.CAP_RECEIVER,
            BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> quarry.getMjReceiver()
        );

        // FE/RF energy capability: quarry also accepts FE power (auto-converted to MJ)
        event.registerBlockEntity(
            Capabilities.Energy.BLOCK,
            BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> new MjBatteryEnergyHandler(quarry.getBattery())
        );

        // Item handler capability: expose an empty handler so item transport pipes
        // recognize the quarry as a valid connection target.
        // In 1.12.2 this was AutomaticProvidingTransactor via CAP_ITEM_TRANSACTOR.
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> net.neoforged.neoforge.transfer.EmptyResourceHandler.instance()
        );
    }


    private void init(FMLCommonSetupEvent event) {
        FillerManager.registry = FillerRegistry.INSTANCE;
    }

    private void postInit(FMLLoadCompleteEvent event) {
        // Future: RulesLoader.loadAll()
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == BCCoreCreativeTabs.MAIN_TAB_KEY) {
            // ordering="AFTER" in neoforge.mods.toml ensures this fires after BCCore
            event.accept(BCBuildersItems.BLUEPRINT_CLEAN);
            event.accept(BCBuildersItems.TEMPLATE_CLEAN);
            event.accept(BCBuildersItems.SCHEMATIC_SINGLE_CLEAN);
            event.accept(BCBuildersItems.FILLER_PLANNER);
            event.accept(BCBuildersBlocks.FILLER);
            event.accept(BCBuildersBlocks.BUILDER);
            event.accept(BCBuildersBlocks.ARCHITECT);
            event.accept(BCBuildersBlocks.LIBRARY);
            event.accept(BCBuildersBlocks.REPLACER);
            event.accept(BCBuildersBlocks.FRAME);
            event.accept(BCBuildersBlocks.QUARRY);
        }
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BCBuildersMenuTypes.FILLER.get(), GuiFiller::new);
        event.register(BCBuildersMenuTypes.BUILDER.get(), GuiBuilder::new);
        event.register(BCBuildersMenuTypes.ARCHITECT.get(), GuiArchitectTable::new);
        event.register(BCBuildersMenuTypes.LIBRARY.get(), GuiElectronicLibrary::new);
        event.register(BCBuildersMenuTypes.REPLACER.get(), GuiReplacer::new);
    }
}
