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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import buildcraft.core.BCCoreCreativeTabs;

@Mod(BCBuilders.MODID)
public class BCBuilders {
    public static final String MODID = "buildcraftbuilders";

    public static BCBuilders INSTANCE = null;

    public BCBuilders(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;

        BCBuildersBlocks.init(modEventBus);
        BCBuildersItems.init(modEventBus);

        BCBuildersConfig.register(modContainer);

        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::init);
        modEventBus.addListener(this::postInit);
        modEventBus.addListener(this::buildCreativeTabContents);
    }

    private void preInit(FMLCommonSetupEvent event) {
        BCBuildersRecipes.init();
    }

    private void init(FMLCommonSetupEvent event) {
        // Future: BCBuildersRegistries.init()
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
        }
    }
}
