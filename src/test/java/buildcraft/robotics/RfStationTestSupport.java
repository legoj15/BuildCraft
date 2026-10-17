/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.function.BooleanSupplier;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.misc.CapUtil;
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
 * 1.21.10 build-script regex that reroutes {@code helper.assertTrue(...)} is textual.
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
            boolean[] finished = { false };
            for (int t = 1; t <= maxTicks; t++) {
                final int tick = t;
                gth.runAfterDelay(tick, () -> {
                    if (finished[0]) {
                        return;
                    }
                    try {
                        if (ready.getAsBoolean()) {
                            finished[0] = true;
                            close();
                            gth.succeed();
                        } else if (tick == maxTicks) {
                            finished[0] = true;
                            close();
                            gth.fail(failureMessage + " (waited " + maxTicks + " ticks)");
                        }
                    } catch (Throwable thrown) {
                        finished[0] = true;
                        close();
                        throw thrown;
                    }
                });
            }
        }
    }

    /** The Forge-Energy handler the RF kinesis pipe exposes on {@code face}, as a plain {@code Object}. */
    public static Object pipeEnergyHandler(TilePipeHolder pipe, Direction face) {
        return pipe.getPipe().getFlow().getCapability(CapUtil.CAP_ENERGY, face);
    }
}
