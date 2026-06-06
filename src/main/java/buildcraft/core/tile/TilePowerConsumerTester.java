/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.core.tile;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.tile.TileBC_Neptune;

import buildcraft.core.BCCoreBlockEntities;

/**
 * Dev-only debug receiver. Accepts up to 100,000 MJ/tick on any side and reports
 * the running tally via {@link IDebuggable} (visible on the F3 debug overlay while
 * looking at the block, see {@code BCDebugOverlay}).
 */
public class TilePowerConsumerTester extends TileBC_Neptune implements IMjReceiver, IDebuggable {

    private long lastReceived;
    private long nextTickReceived;
    private long lastTickReceived;
    private long totalReceived;

    public TilePowerConsumerTester(BlockPos pos, BlockState state) {
        super(BCCoreBlockEntities.POWER_TESTER.get(), pos, state);
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        lastReceived = input.getLongOr("last", 0L);
        nextTickReceived = input.getLongOr("nt", 0L);
        lastTickReceived = input.getLongOr("lt", 0L);
        totalReceived = input.getLongOr("total", 0L);
    }

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putLong("last", lastReceived);
        output.putLong("nt", nextTickReceived);
        output.putLong("lt", lastTickReceived);
        output.putLong("total", totalReceived);
    }

    // --- Tick ---

    /** Called from {@code BlockPowerConsumerTester.getTicker} on the server. */
    public void serverTick() {
        lastTickReceived = nextTickReceived;
        nextTickReceived = 0;
    }

    // --- IMjReceiver ---

    @Override
    public boolean canConnect(IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return 100_000L * MjAPI.MJ;
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        if (!simulate) {
            lastReceived = microJoules;
            nextTickReceived += microJoules;
            totalReceived += microJoules;
        }
        return 0;
    }

    // --- IDebuggable ---

    @Override
    public void getDebugInfo(List<String> left, List<String> right, Direction side) {
        left.add("");
        left.add("Last received = " + LocaleUtil.localizeMj(lastReceived));
        left.add("Tick received = " + LocaleUtil.localizeMj(lastTickReceived));
        left.add("Total received = " + LocaleUtil.localizeMj(totalReceived));
    }
}
