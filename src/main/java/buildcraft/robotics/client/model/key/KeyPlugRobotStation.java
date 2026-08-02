/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.model.key;

import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.PluggableModelKey;

/**
 * Model key for the docking-station pedestal — side only, deliberately excluding the docking state
 * (available/reserved/linked). {@code KeyPlugGate} in silicon documents why a state-varying baked
 * key is a real FPS bug: a key flip forces the pipe's whole 27-section neighbourhood to re-mesh, and
 * a dock/undock/reserve happens far more often than a block edit. The reserved and linked pedestals
 * are instead re-emitted per-frame by {@code PlugRobotStationRenderer} (an
 * {@code IPlugDynamicRenderer}), so this key never changes for a given side and the baked quads are
 * cached forever.
 */
public class KeyPlugRobotStation extends PluggableModelKey {
    public KeyPlugRobotStation(Direction side) {
        super("cutout", side);
    }
}
