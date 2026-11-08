/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.container;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.energy.BCEnergyMenuTypes;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.lib.engine.EngineMenuData;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.WideContainerData;
import buildcraft.lib.gui.widget.WidgetFluidTank;

/**
 * Container (menu) for the combustion engine GUI.
 * Syncs engine state + fluid tank levels to the client via WideContainerData.
 */
@SuppressWarnings("this-escape")
public class ContainerEngineIron extends ContainerBCTile<TileEngineIron_BC8> {
    // The engine tile is the inherited `tile` field (ContainerBCTile). Screens read it via menu.tile.
    private final EngineMenuData engineData;
    private final WideContainerData.IntValue fuelAmount;
    private final WideContainerData.IntValue coolantAmount;
    private final WideContainerData.IntValue residueAmount;
    private final WideContainerData.IntValue fuelFluidId;
    private final WideContainerData.IntValue coolantFluidId;
    private final WideContainerData.IntValue residueFluidId;

    // Widget indices (in registration order) for screen to reference
    public final WidgetFluidTank widgetFuel;
    public final WidgetFluidTank widgetCoolant;
    public final WidgetFluidTank widgetResidue;

    /** Server constructor */
    public ContainerEngineIron(int containerId, Inventory playerInv, TileEngineIron_BC8 engine) {
        super(BCEnergyMenuTypes.ENGINE_IRON.get(), containerId, playerInv.player, engine);

        // Engine and tank state synced to the client; WideContainerData keeps every value intact through vanilla's
        // 16-bit menu-data packet (the only one Fabric has).
        WideContainerData.Builder sync = WideContainerData.builder();
        engineData = new EngineMenuData(sync, engine, () -> engine.getPower());
        fuelAmount = sync.addInt(() -> engine.tankFuel.getAmountMb(0));
        coolantAmount = sync.addInt(() -> engine.tankCoolant.getAmountMb(0));
        residueAmount = sync.addInt(() -> engine.tankResidue.getAmountMb(0));
        fuelFluidId = sync.addInt(() -> getFluidRegistryId(engine.tankFuel.getFluidStack(0)));
        coolantFluidId = sync.addInt(() -> getFluidRegistryId(engine.tankCoolant.getFluidStack(0)));
        residueFluidId = sync.addInt(() -> getFluidRegistryId(engine.tankResidue.getFluidStack(0)));
        addDataSlots(sync.buildFor(engine));

        // Player inventory — positioned at standard location
        addFullPlayerInventory(8, 95);

        // Register fluid tank widgets for in-GUI bucket interaction. Guarded for a null tile (the
        // client resolver returns null if the block entity isn't present — the menu then closes via
        // stillValid); previously the client resolver threw instead of admitting a null engine.
        widgetFuel = addWidget(new WidgetFluidTank(this, engine != null ? engine.tankFuel : null));
        widgetCoolant = addWidget(new WidgetFluidTank(this, engine != null ? engine.tankCoolant : null));
        widgetResidue = addWidget(new WidgetFluidTank(this, engine != null ? engine.tankResidue : null));
    }

    /** Client constructor (from network buffer) */
    public ContainerEngineIron(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileEngineIron_BC8.class));
    }

    // --- Data accessors for the screen ---

    public long getSyncedPower() {
        return engineData.power();
    }

    public float getSyncedHeat() {
        return engineData.heat();
    }

    public EnumPowerStage getSyncedPowerStage() {
        return engineData.stage();
    }

    public boolean isSyncedBurning() {
        return engineData.burning();
    }

    public long getSyncedCurrentOutput() {
        return engineData.output();
    }

    public int getSyncedFuelAmount() {
        return fuelAmount.get();
    }

    public int getSyncedCoolantAmount() {
        return coolantAmount.get();
    }

    public int getSyncedResidueAmount() {
        return residueAmount.get();
    }

    public Fluid getSyncedFuelFluid() {
        return getFluidFromRegistryId(fuelFluidId.get());
    }

    public Fluid getSyncedCoolantFluid() {
        return getFluidFromRegistryId(coolantFluidId.get());
    }

    public Fluid getSyncedResidueFluid() {
        return getFluidFromRegistryId(residueFluidId.get());
    }

    private static int getFluidRegistryId(buildcraft.api.fluids.BCFluidStack resource) {
        if (resource.isEmpty()) return -1;
        return BuiltInRegistries.FLUID.getId(resource.getFluid());
    }

    private static Fluid getFluidFromRegistryId(int id) {
        if (id < 0) return Fluids.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return fluid != null ? fluid : Fluids.EMPTY;
    }
}
