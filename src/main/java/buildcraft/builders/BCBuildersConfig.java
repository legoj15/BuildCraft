/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCBuildersConfig {

    /** Blueprints that save larger than this are stored externally, smaller ones are stored directly in the item. */
    // TODO: wire into Blueprint.serializeNBT path to actually route oversized payloads to external storage
    public static ModConfigSpec.IntValue bptStoreExternalThreshold;

    /** The minimum height that all quarry frames must be. */
    public static ModConfigSpec.IntValue quarryFrameMinHeight;

    /** The maximum number of tasks that the quarry will do per tick. */
    public static ModConfigSpec.IntValue quarryMaxTasksPerTick;

    /** 1 divided by this value is added to the power cost for each additional task done per tick. 0 disables. */
    public static ModConfigSpec.IntValue quarryTaskPowerDivisor;

    /** The maximum number of blocks that a quarry is allowed to move, per second. 0 = no limit. */
    public static ModConfigSpec.DoubleValue quarryMaxFrameMoveSpeed;

    /** The maximum number of blocks that the quarry is allowed to mine each second. 0 = no limit. */
    public static ModConfigSpec.DoubleValue quarryMaxBlockMineRate;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        builder.push("builders");

        bptStoreExternalThreshold = builder
                .comment("Blueprints larger than this (in bytes) are stored externally rather than in the item NBT.")
                .defineInRange("bptStoreExternalThreshold", 20_000, 0, Integer.MAX_VALUE);

        builder.push("quarry");

        quarryFrameMinHeight = builder
                .comment("The minimum height that all quarry frames must be.",
                         "A value of 1 will look strange when it drills the uppermost layer.")
                .defineInRange("quarryFrameMinHeight", 4, 1, 256);

        // quarryFrameMoveBoth removed — the both-axis frame movement mode from 1.12.2 is not
        // implemented in 26.1.1. Re-add when/if that animation mode is ported.

        quarryMaxTasksPerTick = builder
                .comment("The maximum number of tasks that the quarry will do per tick.",
                         "(Where a task is either breaking a block, or moving the frame)")
                .defineInRange("quarryMaxTasksPerTick", 4, 1, 20);

        quarryTaskPowerDivisor = builder
                .comment("1 divided by this value is added to the power cost for each additional task done per tick.",
                         "A value of 0 disables this behaviour.")
                .defineInRange("quarryTaskPowerDivisor", 2, 0, 100);

        quarryMaxFrameMoveSpeed = builder
                .comment("The maximum number of blocks that a quarry is allowed to move, per second.",
                         "A value of 0 means no limit.")
                .defineInRange("quarryMaxFrameMoveSpeed", 0.0, 0.0, 5120.0);

        quarryMaxBlockMineRate = builder
                .comment("The maximum number of blocks that the quarry is allowed to mine each second.",
                         "A value of 0 means no limit, and a value of 0.5 will mine up to half a block per second.")
                .defineInRange("quarryMaxBlockMineRate", 0.0, 0.0, 1000.0);

        builder.pop();
        builder.pop();
    }
}
