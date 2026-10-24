/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BCBuildersConfig {

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

    /** The default for {@link #libraryMaxUploadMb}: the largest scan the default config can make (markers 64 apart
     *  enclose 65^3 = 274,625 cells, about 4.4 MB unpacked) with room for its palette, and not much more. */
    public static final int LIBRARY_MAX_UPLOAD_MB_DEFAULT = 32;
    public static final int LIBRARY_MAX_UPLOAD_MB_MIN = 1;
    /** The most the option may be set to, and the most {@code LibraryUploadBuffer.Limits#readCeiling()} lets this game
     *  expand from a received or stored snapshot: one number, so that no upload can be accepted that is then unreadable
     *  (a stored file skipped as corrupt, a preview whose decode fails). */
    public static final int LIBRARY_MAX_UPLOAD_MB_MAX = 4 * LIBRARY_MAX_UPLOAD_MB_DEFAULT;

    /** The largest snapshot, in megabytes, the Electronic Library accepts from a client. */
    public static ModConfigSpec.IntValue libraryMaxUploadMb;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        builder.push("builders");

        builder.push("library");

        libraryMaxUploadMb = builder
                .comment("The largest blueprint or template, in megabytes, that the Electronic Library accepts from a player.",
                         "It bounds the upload as sent, as unpacked on the server and as the library would store it,",
                         "and the size a snapshot may claim (at 16 bytes a cell). Anything larger is refused, so a modified client cannot make the server",
                         "allocate without limit. The default covers the largest scan the default marker range can make",
                         "(65 x 65 x 65 blocks) with room to spare. Raise it if you raise the marker range or let",
                         "players scan larger volume boxes: a scan costs 16 bytes a block, so a cube 100 blocks on a",
                         "side needs about 16 MB, and the maximum, 128, holds one up to about 200 blocks on a side.",
                         "128 is also the most any snapshot is read back at (from the library's store or a",
                         "preview), so the option cannot go higher, and the library refuses a larger upload.",
                         "Scans are held to that same 128, not to this option: an Architect Table will not scan an",
                         "area (a cube about 200 blocks on a side at most) whose snapshot could not be read back,",
                         "and it refuses a finished blueprint scan that turns out too large for the same reason.",
                         "A scan between this option and that limit can be made and read back, but it cannot be",
                         "uploaded to the library.")
                .defineInRange("libraryMaxUploadMb", LIBRARY_MAX_UPLOAD_MB_DEFAULT, LIBRARY_MAX_UPLOAD_MB_MIN,
                        LIBRARY_MAX_UPLOAD_MB_MAX);

        builder.pop();

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
