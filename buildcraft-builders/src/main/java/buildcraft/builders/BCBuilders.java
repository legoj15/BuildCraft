/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.neoforged.bus.api.IEventBus;
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
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreCreativeTabs;
import buildcraft.lib.mj.MjBatteryEnergyHandler;

/**
 * BuildCraft Builders initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCBuilders {
    public static final String MODID = BCCore.MODID;

    public static void init(IEventBus modEventBus) {
        BCBuildersBlocks.init(modEventBus);
        BCBuildersItems.init(modEventBus);
        BCBuildersBlockEntities.init(modEventBus);
        BCBuildersEntities.init(modEventBus);
        BCBuildersMenuTypes.init(modEventBus);

        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            BCBuildersRecipes.init();
        });
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            FillerManager.registry = FillerRegistry.INSTANCE;
        });
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            buildCreativeTabContents(event);
        });
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            registerMenuScreens(event);
        });

        // Register quarry rendering via game event bus (not mod bus)
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> BCBuildersEventDist.INSTANCE.renderAllQuarries(event));

            modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
                event.registerEntityRenderer(BCBuildersEntities.QUARRY_RIG.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
            });
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> quarry.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> new MjBatteryEnergyHandler(quarry.getBattery()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> net.neoforged.neoforge.transfer.EmptyResourceHandler.instance());
    }

    private static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BCBuildersMenuTypes.FILLER.get(), GuiFiller::new);
        event.register(BCBuildersMenuTypes.BUILDER.get(), GuiBuilder::new);
        event.register(BCBuildersMenuTypes.ARCHITECT.get(), GuiArchitectTable::new);
        event.register(BCBuildersMenuTypes.LIBRARY.get(), GuiElectronicLibrary::new);
        event.register(BCBuildersMenuTypes.REPLACER.get(), GuiReplacer::new);
    }
}
