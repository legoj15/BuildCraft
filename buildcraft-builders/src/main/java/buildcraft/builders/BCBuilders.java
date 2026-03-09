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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import buildcraft.api.filler.FillerManager;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.builders.registry.FillerRegistry;
import buildcraft.core.BCCoreCreativeTabs;

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
        modEventBus.addListener(this::buildCreativeTabContents);
        modEventBus.addListener(this::registerMenuScreens);
    }

    private void preInit(FMLCommonSetupEvent event) {
        BCBuildersRecipes.init();
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
