/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders;

import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import buildcraft.api.filler.FillerManager;
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
import buildcraft.lib.transfer.ItemStorageUtil;
import buildcraft.platform.Platform;
import buildcraft.platform.transfer.CapabilityRegistrar;

/**
 * BuildCraft Builders initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCBuilders {
    public static final String MODID = BCCore.MODID;

    public static void init() {
        BCBuildersBlocks.init();
        BCBuildersItems.init();
        BCBuildersBlockEntities.init();
        BCBuildersEntities.init();
        BCBuildersMenuTypes.init();
        BCBuildersFeatures.init();

        Platform.lifecycle().onCommonSetup(BCBuildersRecipes::init);
        Platform.lifecycle().onCommonSetup(() -> {
            FillerManager.registry = FillerRegistry.INSTANCE;
            TemplateApi.templateRegistry = TemplateRegistry.INSTANCE;
            TemplateApi.templateRegistry.addHandler(TemplateHandlerDefault.INSTANCE);
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
        Platform.transfer().registerCapabilities(BCBuilders::registerCapabilities);
        Platform.transfer().registerCapabilities(BCBuilders::registerTransfer);

        // Per-server-tick driver for BCBuildersEventDist.onServerTick, which throttles
        // the destroying_the_world per-owner pairing scan to SCAN_INTERVAL_TICKS and
        // short-circuits when no level has ≥2 tracked quarries.
        Platform.gameHooks().onServerTickEnd(server -> BCBuildersEventDist.INSTANCE.onServerTick());

        if (Platform.loader().isPhysicalClient()) {
            buildcraft.builders.client.BCBuildersClient.initClient();
        }
    }

    private static void registerCapabilities(buildcraft.platform.transfer.CapabilityRegistrar registrar) {
        // Quarry — advertises as an MJ connector too so kinesis pipes route power to it (a receiver IS a
        // connector), and gates FE insertion on the same wantsPower predicate as the MJ receiver, so a
        // finished quarry refuses FE pushes instead of topping its battery off, unless a Pump on it is
        // drawing on that battery (MJ/FE parity).
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCBuildersBlockEntities.QUARRY.get(),
            quarry -> quarry.getMjReceiver(),
            quarry -> MjBatteryEnergyHandler.createIfRfEnabled(quarry.getBattery(), quarry::wantsPower));

        // Filler
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCBuildersBlockEntities.FILLER.get(),
            filler -> filler.getMjReceiver(),
            filler -> MjBatteryEnergyHandler.createIfRfEnabled(filler.getBattery()));

        // Builder — wires it into the same pipe/engine capability surfaces as the Filler so MJ
        // engines push power into the battery, pipes push resources into the 27-slot grid, and
        // fluid pipes can top off the 4 tanks for fluid-block placement.
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCBuildersBlockEntities.BUILDER.get(),
            builder -> builder.getMjReceiver(),
            builder -> MjBatteryEnergyHandler.createIfRfEnabled(builder.getBattery()));
    }

    /** The item storages, through the platform layer's capability registrar. */
    private static void registerTransfer(CapabilityRegistrar registrar) {
        // Quarry: a slotless storage, so item pipes still connect to it.
        registrar.blockItems(BCBuildersBlockEntities.QUARRY.get(), (quarry, direction) -> ItemStorageUtil.EMPTY);

        registrar.blockItems(BCBuildersBlockEntities.FILLER.get(), (filler, direction) -> filler.getItemHandler(direction));

        // Builder — pipes push resources into the 27-slot grid.
        registrar.blockItems(BCBuildersBlockEntities.BUILDER.get(),
            (builder, direction) -> builder.getItemHandler(direction));
        // ... and fluid pipes top off its 4 tanks for fluid-block placement.
        registrar.blockFluids(BCBuildersBlockEntities.BUILDER.get(), (builder, direction) -> builder.getTankManager());

        // Electronic Library — exposes its snapshot up/download slots to item pipes.
        registrar.blockItems(BCBuildersBlockEntities.LIBRARY.get(),
            (library, direction) -> library.getItemHandler(direction));

        // Architect Table — exposes the INSERT input slot (blank Blueprint/Template) and the
        // EXTRACT output slot (finished snapshot) so item pipes can connect and exchange snapshots,
        // matching 1.12.2. Without this the slots are invisible to PipeFlowItems.canConnect.
        registrar.blockItems(BCBuildersBlockEntities.ARCHITECT.get(),
            (architect, direction) -> architect.getItemHandler(direction));
    }




}
