/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.IZone;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotRegistry;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.entity.EntityRobot;

/** A board robot whose work zone AND load/unload zone are fixed by the test instead of coming from a linked
 *  station's gates. By default both are the test's own arena cell ({@link EntityArenaUtil#cellZone}).
 *
 *  <p>Without a zone a board's searches cover the whole dimension: a picker takes a sibling test's dropped items, a
 *  carrier or delivery robot flies to a sibling's station, a lumberjack chops a sibling's tree. A real robot gets its
 *  zones from gate actions on its linked station; a test that does not need that wiring uses this instead, and
 *  <b>every game test that runs a board's {@code update()} must</b> ({@code ZonePinnedRobotUseTester} fails a
 *  {@code new EntityRobot(level, board)} anywhere else). Never for a test whose point is a gate-supplied zone.
 *
 *  <p>This is the ONE zone-overriding robot subclass the tests have; its variations are chained setters so no tester
 *  grows a private copy:
 *  <ul>
 *  <li>{@link #ZonePinnedRobot(Level, RedstoneBoardRobotNBT, IZone)}: a work zone other than the cell (one machine,
 *      a few blocks), with the load/unload zone following it;</li>
 *  <li>{@link #loadUnloadZone}: a different load/unload zone, or {@code null} for none (stations not zone-limited);</li>
 *  <li>{@link #parkedAt}: docked at and linked to a station, without the pluggable's registration dance;</li>
 *  <li>{@link #registry}: an isolated registry, for tests that must not see (or disturb) the level's real stations
 *      and robots.</li>
 *  </ul>
 *  Recharging is zone-free by design ({@code AIRobotRecharge} searches every powered station), so the pin does not
 *  narrow that search; a recharge test stays hermetic through its own environment and the nearest-station rule. */
public class ZonePinnedRobot extends EntityRobot {

    private final IZone workZone;
    private IZone loadUnloadZone;
    private DockingStation home;
    private IRobotRegistry registry;

    /** A pinned robot working and loading/unloading in the test's own cell. {@code board} may be null for a robot
     *  that is only a body. */
    public ZonePinnedRobot(GameTestHelper helper, RedstoneBoardRobotNBT board) {
        this(helper.getLevel(), board, EntityArenaUtil.cellZone(helper));
    }

    /** A pinned robot working {@code workZone}; its load/unload zone follows it until {@link #loadUnloadZone}. */
    public ZonePinnedRobot(Level level, RedstoneBoardRobotNBT board, IZone workZone) {
        super(level, board);
        this.workZone = workZone;
        this.loadUnloadZone = workZone;
    }

    /** A pinned robot at {@code relPos}'s block centre, added to the level (it starts ticking with the level). */
    public static ZonePinnedRobot spawn(GameTestHelper helper, BlockPos relPos, RedstoneBoardRobotNBT board) {
        ZonePinnedRobot robot = new ZonePinnedRobot(helper, board);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        helper.getLevel().addFreshEntity(robot);
        return robot;
    }

    /** Overrides the load/unload zone ({@code null}: not zone-limited). */
    public ZonePinnedRobot loadUnloadZone(IZone zone) {
        this.loadUnloadZone = zone;
        return this;
    }

    /** Docks the robot at, and links it to, {@code station}, whatever the registry says. */
    public ZonePinnedRobot parkedAt(DockingStation station) {
        this.home = station;
        return this;
    }

    /** Replaces the registry the robot reads stations and other robots from (the level's real one by default). */
    public ZonePinnedRobot registry(IRobotRegistry registry) {
        this.registry = registry;
        return this;
    }

    @Override
    public IZone getZoneToWork() {
        return workZone;
    }

    @Override
    public IZone getZoneToLoadUnload() {
        return loadUnloadZone;
    }

    @Override
    public DockingStation getDockingStation() {
        return home != null ? home : super.getDockingStation();
    }

    @Override
    public DockingStation getLinkedStation() {
        return home != null ? home : super.getLinkedStation();
    }

    @Override
    public IRobotRegistry getRegistry() {
        return registry != null ? registry : super.getRegistry();
    }
}
