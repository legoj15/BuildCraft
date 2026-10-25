/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Random;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.path.IBlockFilter;

/** Picks a random column ({@code zone.getRandomBlockPos}, or a random point in a circle of {@code range}
 *  around the robot) and scans it top-down for the first block matching {@code filter} — the "ground"
 *  search the bomber uses to find somewhere to drop its payload. Ported from 7.1.x
 * {@code AIRobotSearchRandomGroundBlock}; 7.1.x's {@code world.getHeight()} cap is the level's highest
 * buildable y and the scan floor drops to its lowest (modern worlds extend below zero). Columns in unloaded
 * chunks are re-picked rather than read, and a zone with nothing to pick from fails the search. */
public class AIRobotSearchRandomGroundBlock extends AIRobot {

    private static final int MAX_ATTEMPTS = 4096;

    public BlockPos blockFound;

    private int range;
    private IBlockFilter filter;
    private IZone zone;
    private int attempts = 0;
    /** {@link IZone#getRandomBlockPos} predates the level's {@code RandomSource} and still draws from a
     *  {@code Random}; one is seeded from the level source for the search's lifetime, exactly as
     *  {@code BlockScannerZoneRandom} does. */
    private Random zoneRandom;

    public AIRobotSearchRandomGroundBlock(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotSearchRandomGroundBlock(IRobotAccess iRobot, int iRange, IBlockFilter iFilter, IZone iZone) {
        this(iRobot);

        range = iRange;
        filter = iFilter;
        zone = iZone;
    }

    @Override
    public void update() {
        // Every give-up returns at once: falling through into the scan could terminate a second time in the same
        // update, and that second terminate() would reach the parent after it had already started its next delegate.
        if (filter == null) {
            terminate();
            return;
        }

        Level level = robot.level();
        int x;
        int z;
        // Not 7.1.x: a column in an unloaded chunk is picked again rather than read (reading it loads or generates
        // the chunk on the server thread), each pick counting as an attempt.
        while (true) {
            attempts++;
            if (attempts > MAX_ATTEMPTS) {
                terminate();
                return;
            }

            if (zone == null) {
                double r = level.getRandom().nextFloat() * range;
                float a = level.getRandom().nextFloat() * 2.0F * (float) Math.PI;

                x = (int) (Mth.cos(a) * r + Mth.floor(robot.position().x));
                z = (int) (Mth.sin(a) * r + Mth.floor(robot.position().z));
            } else {
                if (zoneRandom == null) {
                    zoneRandom = new Random(level.getRandom().nextLong());
                }
                BlockPos b = zone.getRandomBlockPos(zoneRandom);
                if (b == null) {
                    // A zone with nothing to pick from: nothing to find, ever.
                    terminate();
                    return;
                }
                x = b.getX();
                z = b.getZ();
            }

            // In memory, not Level.hasChunk (which on a server only tests the ticket level, true for a chunk still
            // queued to load): the scan below reads the column.
            if (BlockUtil.isChunkPresent(level, x, z)) {
                break;
            }
        }

        // 7.1.x scanned from world.getHeight() (the fixed 256 cap) down to 0; the modern scan spans the
        // level's actual build range (modern worlds extend below zero and past 256).
        int top = RobotUtils.maxBuildY(level);
        int bottom = RobotUtils.minBuildY(level);
        for (int y = top; y >= bottom; --y) {
            BlockPos pos = new BlockPos(x, y, z);
            if (filter.matches(pos)) {
                blockFound = pos;
                terminate();
                return;
            } else if (!robot.level().isEmptyBlock(pos)) {
                // The column's top block failed the filter — not a candidate, try another column.
                return;
            }
        }
    }

    @Override
    public boolean success() {
        return blockFound != null;
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 2 RF per search tick.
        return 200_000;
    }
}
