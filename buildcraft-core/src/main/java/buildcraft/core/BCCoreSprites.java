/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core;

import java.util.EnumMap;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.tiles.IControllable.Mode;
import buildcraft.lib.client.sprite.SpriteHolderRegistry;
import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;

import buildcraft.core.statements.TriggerFluidContainer;
import buildcraft.core.statements.TriggerFluidContainerLevel;
import buildcraft.core.statements.TriggerInventory;
import buildcraft.core.statements.TriggerInventoryLevel;

/**
 * Sprite holder definitions for BuildCraft Core.
 * Includes laser textures, statement icons, and trigger/action sprites.
 */
public class BCCoreSprites {
    // Laser textures — marker connections
    public static final SpriteHolder MARKER_VOLUME_CONNECTED = h("lasers/marker_volume_connected");
    public static final SpriteHolder MARKER_VOLUME_POSSIBLE = h("lasers/marker_volume_possible");
    public static final SpriteHolder MARKER_VOLUME_SIGNAL = h("lasers/marker_volume_signal");
    public static final SpriteHolder MARKER_PATH_CONNECTED = h("lasers/marker_path_connected");
    public static final SpriteHolder MARKER_PATH_POSSIBLE = h("lasers/marker_path_possible");
    public static final SpriteHolder MARKER_DEFAULT_POSSIBLE = h("lasers/marker_default_possible");

    // Laser textures — stripes
    public static final SpriteHolder STRIPES_READ = h("lasers/stripes_read");
    public static final SpriteHolder STRIPES_WRITE = h("lasers/stripes_write");
    public static final SpriteHolder STRIPES_WRITE_DIRECTION = h("lasers/stripes_write_direction");

    // Laser textures — power levels
    public static final SpriteHolder LASER_POWER_LOW = h("lasers/power_low");
    public static final SpriteHolder LASER_POWER_MED = h("lasers/power_med");
    public static final SpriteHolder LASER_POWER_HIGH = h("lasers/power_high");
    public static final SpriteHolder LASER_POWER_FULL = h("lasers/power_full");

    // Triggers
    public static final SpriteHolder TRIGGER_MACHINE_ACTIVE = h("triggers/trigger_machine_active");
    public static final SpriteHolder TRIGGER_MACHINE_INACTIVE = h("triggers/trigger_machine_inactive");
    public static final SpriteHolder TRIGGER_REDSTONE_ACTIVE = h("triggers/trigger_redstone_active");
    public static final SpriteHolder TRIGGER_REDSTONE_INACTIVE = h("triggers/trigger_redstone_inactive");
    public static final SpriteHolder TRIGGER_TRUE = h("triggers/trigger_true");
    public static final SpriteHolder TRIGGER_POWER_HIGH = h("triggers/trigger_power_high");
    public static final SpriteHolder TRIGGER_POWER_LOW = h("triggers/trigger_power_low");

    // Power stage triggers
    public static final EnumMap<EnumPowerStage, SpriteHolder> TRIGGER_POWER_STAGE = new EnumMap<>(EnumPowerStage.class);

    // Fluid triggers
    public static final EnumMap<TriggerFluidContainer.State, SpriteHolder> TRIGGER_FLUID = new EnumMap<>(TriggerFluidContainer.State.class);
    public static final EnumMap<TriggerFluidContainerLevel.TriggerType, SpriteHolder> TRIGGER_FLUID_LEVEL = new EnumMap<>(TriggerFluidContainerLevel.TriggerType.class);

    // Inventory triggers
    public static final EnumMap<TriggerInventory.State, SpriteHolder> TRIGGER_INVENTORY = new EnumMap<>(TriggerInventory.State.class);
    public static final EnumMap<TriggerInventoryLevel.TriggerType, SpriteHolder> TRIGGER_INVENTORY_LEVEL = new EnumMap<>(TriggerInventoryLevel.TriggerType.class);

    // Actions
    public static final SpriteHolder ACTION_REDSTONE = h("triggers/action_redstone");
    public static final EnumMap<Mode, SpriteHolder> ACTION_MACHINE_CONTROL = new EnumMap<>(Mode.class);

    // Parameters
    public static final SpriteHolder PARAM_GATE_SIDE_ONLY = h("triggers/param_gate_side_only");
    public static final SpriteHolder[] PARAM_REDSTONE_LEVEL = new SpriteHolder[16];

    static {
        for (EnumPowerStage stage : EnumPowerStage.VALUES) {
            TRIGGER_POWER_STAGE.put(stage, h("triggers/trigger_power_stage_" + stage.getSerializedName()));
        }
        for (TriggerFluidContainer.State state : TriggerFluidContainer.State.VALUES) {
            TRIGGER_FLUID.put(state, h("triggers/trigger_fluid_" + state.name().toLowerCase()));
        }
        for (TriggerFluidContainerLevel.TriggerType type : TriggerFluidContainerLevel.TriggerType.VALUES) {
            TRIGGER_FLUID_LEVEL.put(type, h("triggers/trigger_fluid_level_" + type.name().toLowerCase()));
        }
        for (TriggerInventory.State state : TriggerInventory.State.VALUES) {
            TRIGGER_INVENTORY.put(state, h("triggers/trigger_inventory_" + state.name().toLowerCase()));
        }
        for (TriggerInventoryLevel.TriggerType type : TriggerInventoryLevel.TriggerType.VALUES) {
            TRIGGER_INVENTORY_LEVEL.put(type, h("triggers/trigger_inventory_level_" + type.name().toLowerCase()));
        }
        for (Mode mode : Mode.VALUES) {
            ACTION_MACHINE_CONTROL.put(mode, h("triggers/action_machine_control_" + mode.lowerCaseName));
        }
        for (int i = 0; i < 16; i++) {
            PARAM_REDSTONE_LEVEL[i] = h("triggers/param_redstone_level_" + i);
        }
    }

    private static SpriteHolder h(String path) {
        return SpriteHolderRegistry.getHolder("buildcraftunofficial:" + path);
    }
}
