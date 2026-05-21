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
import buildcraft.api.template.TemplateApi;
import buildcraft.builders.gui.GuiArchitectTable;
import buildcraft.builders.gui.GuiElectronicLibrary;
import buildcraft.builders.gui.GuiFiller;
import buildcraft.builders.gui.GuiReplacer;
import buildcraft.builders.gui.GuiBuilder;
import buildcraft.builders.registry.FillerRegistry;
import buildcraft.builders.snapshot.RulesLoader;
import buildcraft.builders.snapshot.TemplateHandlerDefault;
import buildcraft.builders.snapshot.TemplateRegistry;
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
            TemplateApi.templateRegistry = TemplateRegistry.INSTANCE;
            TemplateApi.templateRegistry.addHandler(TemplateHandlerDefault.INSTANCE);
            // Register Volume Box addon classes for NBT round-trip identification.
            buildcraft.core.marker.volume.AddonsRegistry.INSTANCE.register(
                net.minecraft.resources.Identifier.parse("buildcraftunofficial:filler_planner"),
                buildcraft.builders.addon.AddonFillerPlanner.class
            );
            BCBuildersSchematics.preInit();
            BCBuildersStatements.preInit();
            // Populate RulesLoader.READ_DOMAINS. Without this call, every block fails
            // SchematicBlockDefault.predicate (which requires the block's namespace be present in
            // READ_DOMAINS), so every Architect Table scan falls through to SchematicBlockAir and
            // produces a completely empty blueprint. loadAll() reads the per-mod rule JSON files
            // shipped under assets/<modid>/compat/buildcraft/builders/index.json and seeds
            // READ_DOMAINS with "minecraft".
            RulesLoader.loadAll();
        });
        modEventBus.addListener((RegisterCapabilitiesEvent event) -> {
            registerCapabilities(event);
        });
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            buildCreativeTabContents(event);
        });


        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            buildcraft.builders.client.BCBuildersClient.initClient(modEventBus);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Quarry
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> quarry.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> new MjBatteryEnergyHandler(quarry.getBattery()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> net.neoforged.neoforge.transfer.EmptyResourceHandler.instance());

        // Filler
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> filler.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> new MjBatteryEnergyHandler(filler.getBattery()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> filler.getItemHandler(direction));

        // Builder — wires it into the same pipe/engine capability surfaces as the Filler so MJ
        // engines push power into the battery, pipes push resources into the 27-slot grid, and
        // fluid pipes can top off the 4 tanks for fluid-block placement.
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getMjReceiver());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> new MjBatteryEnergyHandler(builder.getBattery()));
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getItemHandler(direction));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getTankManager());

        // Electronic Library — exposes its snapshot up/download slots to item pipes.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BCBuildersBlockEntities.LIBRARY.get(),
            (library, direction) -> library.getItemHandler(direction));
    }

    private static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
    }


}
