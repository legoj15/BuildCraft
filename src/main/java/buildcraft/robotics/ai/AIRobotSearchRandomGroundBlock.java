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

import java.util.Random;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.path.IBlockFilter;

/** Picks a random column ({@code zone.getRandomBlockPos}, or a random point in a circle of {@code range}
 *  around the robot) and scans it top-down for the first block matching {@code filter} — the "ground"
 *  search the bomber uses to find somewhere to drop its payload. Ported from 7.1.x
 * {@code AIRobotSearchRandomGroundBlock}; 7.1.x's {@code world.getHeight()} cap is the modern
 * {@code getMaxBuildHeight()} and the scan floor drops to {@code getMinBuildHeight()} (modern worlds
 * extend below zero). */
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
        if (filter == null) {
            terminate();
        }

        attempts++;

        if (attempts > MAX_ATTEMPTS) {
            terminate();
        }

        int x;
        int z;

        if (zone == null) {
            double r = robot.level().getRandom().nextFloat() * range;
            float a = robot.level().getRandom().nextFloat() * 2.0F * (float) Math.PI;

            x = (int) (Mth.cos(a) * r + Mth.floor(robot.position().x));
            z = (int) (Mth.sin(a) * r + Mth.floor(robot.position().z));
        } else {
            if (zoneRandom == null) {
                zoneRandom = new Random(robot.level().getRandom().nextLong());
            }
            BlockPos b = zone.getRandomBlockPos(zoneRandom);
            x = b.getX();
            z = b.getZ();
        }

        // 7.1.x scanned from world.getHeight() (the fixed 256 cap) down to 0; the modern scan spans the
        // level's actual build range (modern worlds extend below zero and past 256).
        //? if >=1.21.10 {
        int top = robot.level().getMaxY();
        int bottom = robot.level().getMinY();
        //?} else {
        /*int top = robot.level().getMaxBuildHeight();
        int bottom = robot.level().getMinBuildHeight();*/
        //?}
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
