/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import java.util.function.LongSupplier;

import buildcraft.api.enums.EnumPowerStage;

import buildcraft.lib.gui.WideContainerData;

/**
 * The engine-ledger values every engine menu syncs: stored power, heat, current output, power stage and the burning
 * flag. Declared on a {@link WideContainerData.Builder} so they survive the 16-bit menu-data channel on both loaders.
 * The sources run only on the server copy, so a {@code null} client-side engine is fine.
 */
public final class EngineMenuData {
    private final WideContainerData.LongValue power;
    private final WideContainerData.FloatValue heat;
    private final WideContainerData.LongValue output;
    private final WideContainerData.IntValue stage;
    private final WideContainerData.BooleanValue burning;

    /** @param storedPower the stored-power reading the ledger shows (the engine's buffer; a dynamo's MJ battery). */
    public EngineMenuData(WideContainerData.Builder sync, TileEngineBase_BC8 engine, LongSupplier storedPower) {
        power = sync.addLong(storedPower);
        // The client reads the resting heat until the first sync, so the ledger never shows 0 °C.
        heat = sync.addFloat(() -> engine.getHeat(), TileEngineBase_BC8.MIN_HEAT);
        output = sync.addLong(() -> engine.currentOutput);
        stage = sync.addInt(() -> engine.getPowerStage().ordinal());
        burning = sync.addBoolean(() -> engine.isBurning());
    }

    /** Stored power in micro-MJ. */
    public long power() {
        return power.get();
    }

    public float heat() {
        return heat.get();
    }

    /** Current output in micro-MJ/tick. */
    public long output() {
        return output.get();
    }

    public EnumPowerStage stage() {
        int ordinal = stage.get();
        EnumPowerStage[] values = EnumPowerStage.VALUES;
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : EnumPowerStage.BLUE;
    }

    public boolean burning() {
        return burning.get();
    }
}
