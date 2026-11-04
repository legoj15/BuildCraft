/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package buildcraft.factory;

import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.core.BCCore;
import buildcraft.lib.mj.MjBatteryEnergyHandler;
import buildcraft.platform.Platform;
import buildcraft.lib.transfer.FluidStorageUtil;
import buildcraft.platform.transfer.BCFluidStorage;

/**
 * BuildCraft Factory initializer. No longer a separate @Mod — called from BCCore.
 */
public class BCFactory {
    public static final String MODID = BCCore.MODID;
    private static final Logger LOGGER = LoggerFactory.getLogger(BCFactory.class);

    public static void init() {
        // Register all deferred registries
        BCFactoryBlocks.init();
        BCFactoryItems.init();
        BCFactoryBlockEntities.init();
        BCFactoryMenuTypes.init();
        BCFactoryAttachments.init();

        // Register client-side extensions on the mod event bus
        if (Platform.loader().isPhysicalClient()) {
            buildcraft.factory.client.BCFactoryClient.initClient();
        }

        // Register capabilities
        Platform.transfer().registerCapabilities(BCFactory::registerCapabilities);
        Platform.transfer().registerCapabilities(BCFactory::registerTransfer);

        // Notify TilePump when COMMON config reloads so running pumps re-evaluate
        // pumpsConsumeWater on the next tick instead of waiting for natural queue
        // exhaustion (which never happens if the pump's tank stays full).
        buildcraft.platform.Platform.configHooks().onReload(buildcraft.core.BCUnifiedConfig.SPEC,
                buildcraft.factory.tile.TilePump::onConfigReloaded);

        LOGGER.info("BuildCraft Factory initialized");
    }

    /** The item and fluid storages, through the platform layer's capability registrar. */
    private static void registerTransfer(buildcraft.platform.transfer.CapabilityRegistrar registrar) {
        registrar.blockItems(BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            (workbench, direction) -> workbench.getItemHandler(direction));
        // Mining Well has no internal item buffer — it pushes mined drops straight into adjacent
        // pipes via InventoryUtil.addToBestAcceptor. Expose an empty item storage (like the Quarry)
        // purely so item pipes render a connection to it; in 1.12.2 the AutomaticProvidingTransactor
        // capability served that role.
        registrar.blockItems(BCFactoryBlockEntities.MINING_WELL.get(),
            (miner, direction) -> buildcraft.lib.transfer.ItemStorageUtil.EMPTY);
        registrar.blockItems(BCFactoryBlockEntities.CHUTE.get(), (chute, direction) -> chute.getItemHandler(direction));

        // Fluid storages.
        registrar.blockFluids(BCFactoryBlockEntities.TANK.get(),
            (tank, direction) -> new buildcraft.factory.tile.TankColumnResourceHandler(tank));
        // Pump: drain-only.
        registrar.blockFluids(BCFactoryBlockEntities.PUMP.get(),
            (pump, direction) -> FluidStorageUtil.extractOnly(pump.getTank()));
        registrar.blockFluids(BCFactoryBlockEntities.FLOOD_GATE.get(),
            (floodGate, direction) -> floodGate.getTank());
        // Distiller: the input tank is fill-only, the output tanks are drain-only.
        registrar.blockFluids(BCFactoryBlockEntities.DISTILLER.get(),
            (distiller, direction) -> {
                BCFluidStorage tank = distiller.getTankForSide(direction);
                if (tank == null) return null;
                return tank == distiller.getTankIn()
                    ? FluidStorageUtil.insertOnly(tank)
                    : FluidStorageUtil.extractOnly(tank);
            });
        registrar.blockFluids(BCFactoryBlockEntities.HEAT_EXCHANGE.get(),
            (heatExchange, direction) -> heatExchange.getFluidTankForDirection(direction));
    }

    private static void registerCapabilities(buildcraft.platform.transfer.CapabilityRegistrar registrar) {
        // The Auto Workbench now backs its craft progress with a real MjBattery, so it exposes the
        // Forge-Energy cap (rf_enabled-gated) like every other machine — previously it was dead on FE cables.
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCFactoryBlockEntities.AUTO_WORKBENCH_ITEMS.get(),
            workbench -> workbench.getMjReceiver(),
            workbench -> MjBatteryEnergyHandler.createIfRfEnabled(workbench.getBattery()));
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCFactoryBlockEntities.MINING_WELL.get(),
            miner -> miner.getMjReceiver(),
            miner -> MjBatteryEnergyHandler.createIfRfEnabled(miner.getBattery()));
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCFactoryBlockEntities.PUMP.get(),
            pump -> pump.getMjReceiver(),
            pump -> MjBatteryEnergyHandler.createIfRfEnabled(pump.getBattery()));
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCFactoryBlockEntities.CHUTE.get(),
            chute -> chute.getMjReceiver(),
            chute -> MjBatteryEnergyHandler.createIfRfEnabled(chute.getBattery()));
        buildcraft.lib.mj.MjCapabilities.registerMjConsumer(registrar, BCFactoryBlockEntities.DISTILLER.get(),
            distiller -> distiller.getMjReceiver(),
            distiller -> MjBatteryEnergyHandler.createIfRfEnabled(distiller.getBattery()));
    }
}
