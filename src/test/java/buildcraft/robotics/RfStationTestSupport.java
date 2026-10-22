/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.RobotManager;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.misc.CapUtil;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.transport.BCTransportConfig;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Shared fixture bits for the game tests that charge a docked robot from a Forge-Energy (RF) kinesis pipe.
 *
 * <p>Two things those tests all need and none should hand-roll:
 * <ul>
 * <li><b>A config scope that always unwinds.</b> The FE path is gated on live config
 *     ({@code powerMode}, {@code disableRfPipe}) and its ratio on {@code mjRfConversionAmount}, so a test has to
 *     flip them, and the flip has to be undone on <em>every</em> exit -- a test that fails or throws while the
 *     power mode is left on autoconvert silently changes what every later test in the batch sees.
 *     {@link ConfigScope#succeedWhen} polls and restores on success, timeout and exception alike.</li>
 * <li><b>The pipe's FE handler</b>, as a plain {@code Object} for {@link FeHandlerAccess} (the version-neutral
 *     insert/extract/read helpers -- the energy handler type cliffs at 1.21.10).</li>
 * </ul>
 *
 * <p>The {@link GameTestHelper} parameter is deliberately named {@code gth}, never {@code helper}: the
 * pre-1.21.11 (1.21.10 and 1.21.1) build-script regex that reroutes assertTrue/assertFalse calls on
 * {@code helper} is textual.
 */
public final class RfStationTestSupport {

    private RfStationTestSupport() {}

    /** Flips {@code powerMode}, the MJ/RF ratio and {@code disableRfPipe}; {@link #close()} puts them back. */
    public static final class ConfigScope {
        private final BCLibConfig.PowerMode previousMode = BCLibConfig.powerMode.get();
        private final double previousRatio = BCLibConfig.mjRfConversionAmount.get();
        private final boolean previousDisable = BCTransportConfig.disableRfPipe.get();
        private boolean closed;

        /** @param ratio MJ per RF; {@code null} leaves the configured ratio alone. */
        public ConfigScope(BCLibConfig.PowerMode mode, Double ratio, boolean disableRfPipe) {
            apply(mode, ratio, disableRfPipe);
        }

        /** Re-points the scope at another combination; {@link #close()} still restores the ORIGINAL values. */
        public void apply(BCLibConfig.PowerMode mode, Double ratio, boolean disableRfPipe) {
            BCLibConfig.powerMode.set(mode);
            if (ratio != null) {
                BCLibConfig.mjRfConversionAmount.set(ratio);
            }
            BCTransportConfig.disableRfPipe.set(disableRfPipe);
        }

        /** Idempotent: safe to call from a success path and a failure path that both fire. */
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            BCLibConfig.powerMode.set(previousMode);
            BCLibConfig.mjRfConversionAmount.set(previousRatio);
            BCTransportConfig.disableRfPipe.set(previousDisable);
        }

        /** Polls {@code ready} once per tick for up to {@code maxTicks} ticks (every poll scheduled up front --
         *  see {@code EntityArenaUtil#tickUntil}), succeeds the test the first time it returns true and fails it
         *  if it never does. This scope is closed on every exit, including a throw out of {@code ready}. */
        public void succeedWhen(GameTestHelper gth, int maxTicks, BooleanSupplier ready, String failureMessage) {
            succeedWhen(gth, maxTicks, ready, failureMessage, () -> {});
        }

        /** {@link #succeedWhen(GameTestHelper, int, BooleanSupplier, String)} that also runs {@code cleanup} on
         *  every exit (before the scope closes): discard the test's robots there, so a timeout does not leave one
         *  docked at a station in the shared level. */
        public void succeedWhen(GameTestHelper gth, int maxTicks, BooleanSupplier ready, String failureMessage,
                                Runnable cleanup) {
            EntityArenaUtil.tickUntil(gth, maxTicks, ready, gth::succeed, failureMessage, () -> {
                try {
                    cleanup.run();
                } finally {
                    close();
                }
            });
        }
    }

    /** Drops the station at {@code absPos}/{@code side} from the robot registry (a no-op if absent).
     *
     *  <p>The station search is dimension-wide and the runner never clears the registry between tests, so a
     *  station a test leaves behind is a candidate for every robot that runs after it -- which is exactly how
     *  {@code robot_station_forbid_robot}'s carrier ends up flying to a stranger's arena and missing its
     *  timeout. Deregistering (rather than breaking the pipe) leaves nothing to drop into the world. The
     *  pluggable's {@code onTick} registers only while it is not yet valid, so it does not come back. */
    public static void removeStation(GameTestHelper gth, BlockPos absPos, Direction side) {
        var registry = RobotManager.registryProvider.getRegistry(gth.getLevel());
        DockingStation station = registry.getStation(absPos, side);
        if (station != null) {
            registry.removeStation(station);
        }
    }

    /** The Forge-Energy handler the RF kinesis pipe exposes on {@code face}, as a plain {@code Object}. */
    public static Object pipeEnergyHandler(TilePipeHolder pipe, Direction face) {
        return pipe.getPipe().getFlow().getCapability(CapUtil.CAP_ENERGY, face);
    }
}
