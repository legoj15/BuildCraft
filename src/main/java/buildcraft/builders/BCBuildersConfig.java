/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCBuildersConfig {

    /** Blueprints that save larger than this are stored externally, smaller ones are stored directly in the item. */
    public static ModConfigSpec.IntValue BPT_STORE_EXTERNAL_THRESHOLD;

    /** The minimum height that all quarry frames must be. */
    public static ModConfigSpec.IntValue QUARRY_FRAME_MIN_HEIGHT;

    /** If true then the frame will move with the drill in both axis. */
    public static ModConfigSpec.BooleanValue QUARRY_FRAME_MOVE_BOTH;

    /** The maximum number of tasks that the quarry will do per tick. */
    public static ModConfigSpec.IntValue QUARRY_MAX_TASKS_PER_TICK;

    /** 1 divided by this value is added to the power cost for each additional task done per tick. 0 disables. */
    public static ModConfigSpec.IntValue QUARRY_TASK_POWER_DIVISOR;

    /** The maximum number of blocks that a quarry is allowed to move, per second. 0 = no limit. */
    public static ModConfigSpec.DoubleValue QUARRY_MAX_FRAME_MOVE_SPEED;

    /** The maximum number of blocks that the quarry is allowed to mine each second. 0 = no limit. */
    public static ModConfigSpec.DoubleValue QUARRY_MAX_BLOCK_MINE_RATE;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        BPT_STORE_EXTERNAL_THRESHOLD = builder
                .comment("Blueprints larger than this (in bytes) are stored externally rather than in the item NBT.")
                .defineInRange("bptStoreExternalThreshold", 20_000, 0, Integer.MAX_VALUE);

        QUARRY_FRAME_MIN_HEIGHT = builder
                .comment("The minimum height that all quarry frames must be.",
                         "A value of 1 will look strange when it drills the uppermost layer.")
                .defineInRange("quarryFrameMinHeight", 4, 1, 256);

        QUARRY_FRAME_MOVE_BOTH = builder
                .comment("If true then the quarry frame will move with both of its axis rather than just one.")
                .define("quarryFrameMoveBoth", false);

        QUARRY_MAX_TASKS_PER_TICK = builder
                .comment("The maximum number of tasks that the quarry will do per tick.",
                         "(Where a task is either breaking a block, or moving the frame)")
                .defineInRange("quarryMaxTasksPerTick", 4, 1, 20);

        QUARRY_TASK_POWER_DIVISOR = builder
                .comment("1 divided by this value is added to the power cost for each additional task done per tick.",
                         "A value of 0 disables this behaviour.")
                .defineInRange("quarryTaskPowerDivisor", 2, 0, 100);

        QUARRY_MAX_FRAME_MOVE_SPEED = builder
                .comment("The maximum number of blocks that a quarry is allowed to move, per second.",
                         "A value of 0 means no limit.")
                .defineInRange("quarryMaxFrameMoveSpeed", 0.0, 0.0, 5120.0);

        QUARRY_MAX_BLOCK_MINE_RATE = builder
                .comment("The maximum number of blocks that the quarry is allowed to mine each second.",
                         "A value of 0 means no limit, and a value of 0.5 will mine up to half a block per second.")
                .defineInRange("quarryMaxBlockMineRate", 0.0, 0.0, 1000.0);
    }
}
