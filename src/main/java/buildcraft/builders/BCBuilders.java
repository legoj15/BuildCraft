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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

        // Per-server-tick driver for BCBuildersEventDist.onServerTick, which throttles
        // the destroying_the_world per-owner pairing scan to SCAN_INTERVAL_TICKS and
        // short-circuits when no level has ≥2 tracked quarries.
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> BCBuildersEventDist.INSTANCE.onServerTick());

        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            buildcraft.builders.client.BCBuildersClient.initClient(modEventBus);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Version-neutral capability tokens: Transfer-API on 1.21.10+, classic handler tokens on 1.21.1.
        //? if >=1.21.10 {
        var itemCap = Capabilities.Item.BLOCK;
        var fluidCap = Capabilities.Fluid.BLOCK;
        var energyCap = Capabilities.Energy.BLOCK;
        //?} else {
        /*var itemCap = Capabilities.ItemHandler.BLOCK;
        var fluidCap = Capabilities.FluidHandler.BLOCK;
        var energyCap = Capabilities.EnergyStorage.BLOCK;*/
        //?}
        // Quarry
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> quarry.getMjReceiver());
        event.registerBlockEntity(energyCap, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(quarry.getBattery()));
        //? if >=1.21.10 {
        event.registerBlockEntity(itemCap, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> net.neoforged.neoforge.transfer.EmptyResourceHandler.instance());
        //?} else {
        /*event.registerBlockEntity(itemCap, BCBuildersBlockEntities.QUARRY.get(),
            (quarry, direction) -> net.neoforged.neoforge.items.wrapper.EmptyItemHandler.INSTANCE);*/
        //?}

        // Filler
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> filler.getMjReceiver());
        event.registerBlockEntity(energyCap, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(filler.getBattery()));
        event.registerBlockEntity(itemCap, BCBuildersBlockEntities.FILLER.get(),
            (filler, direction) -> filler.getItemHandler(direction));

        // Builder — wires it into the same pipe/engine capability surfaces as the Filler so MJ
        // engines push power into the battery, pipes push resources into the 27-slot grid, and
        // fluid pipes can top off the 4 tanks for fluid-block placement.
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getMjReceiver());
        event.registerBlockEntity(energyCap, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> MjBatteryEnergyHandler.createIfRfEnabled(builder.getBattery()));
        event.registerBlockEntity(itemCap, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getItemHandler(direction));
        event.registerBlockEntity(fluidCap, BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getTankManager());

        // Electronic Library — exposes its snapshot up/download slots to item pipes.
        event.registerBlockEntity(itemCap, BCBuildersBlockEntities.LIBRARY.get(),
            (library, direction) -> library.getItemHandler(direction));

        // Architect Table — exposes the INSERT input slot (blank Blueprint/Template) and the
        // EXTRACT output slot (finished snapshot) so item pipes can connect and exchange snapshots,
        // matching 1.12.2. Without this the slots are invisible to PipeFlowItems.canConnect.
        event.registerBlockEntity(itemCap, BCBuildersBlockEntities.ARCHITECT.get(),
            (architect, direction) -> architect.getItemHandler(direction));
    }

    private static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
    }


}
