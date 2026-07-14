/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjBattery;

/**
 * Bundles the standard MJ-consumer tile scaffolding — an {@link MjBattery} paired with the
 * {@link IMjReceiver} a machine exposes as its {@code CAP_RECEIVER} / {@code CAP_CONNECTOR} capability —
 * so a machine holds one field for its MJ power intake instead of a battery, a hand-rolled receiver, and
 * a getter for each. Register all three MJ capabilities from one call via
 * {@link MjCapabilities#registerMjConsumer}.
 *
 * <p>Most machines take the default {@link MjBatteryReceiver}. The genuinely-custom cases pass a receiver
 * factory: the quarry gates its {@code getPowerRequested} on having work, and the auto-workbench / pump
 * pass a {@link MjRedstoneBatteryReceiver} so engines deliver power in pulses (see the engine base's
 * {@code IMjRedstoneReceiver} check). The factory receives the battery so the receiver can be built over it.
 */
public class MjBatteryComponent {
    private final MjBattery battery;
    private final IMjReceiver receiver;

    /** New battery of {@code capacity} micro-joules, default {@link MjBatteryReceiver}. */
    public MjBatteryComponent(long capacity) {
        this(new MjBattery(capacity));
    }

    /** Wraps an existing battery with the default {@link MjBatteryReceiver}. */
    public MjBatteryComponent(MjBattery battery) {
        this(battery, MjBatteryReceiver::new);
    }

    /** New battery of {@code capacity} micro-joules, custom receiver built by {@code receiverFactory}. */
    public MjBatteryComponent(long capacity, Function<MjBattery, ? extends IMjReceiver> receiverFactory) {
        this(new MjBattery(capacity), receiverFactory);
    }

    /** Wraps an existing battery with a custom receiver built by {@code receiverFactory}. */
    public MjBatteryComponent(MjBattery battery, Function<MjBattery, ? extends IMjReceiver> receiverFactory) {
        this.battery = battery;
        this.receiver = receiverFactory.apply(battery);
    }

    public MjBattery getBattery() {
        return battery;
    }

    public IMjReceiver getMjReceiver() {
        return receiver;
    }

    /** Convenience passthrough — bleeds off excess stored power (call once per tick). */
    public void tick(Level level, BlockPos pos) {
        battery.tick(level, pos);
    }
}
