/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.block;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}
import net.minecraft.world.phys.shapes.CollisionContext;

import buildcraft.api.transport.EnumWirePart;

import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.plug.PluggablePowerAdaptor;
import buildcraft.transport.tile.TilePipeHolder;
import buildcraft.transport.wire.EnumWireBetween;
import buildcraft.transport.wire.SavedDataWireSystems;
import buildcraft.transport.wire.WireSystem;

/**
 * Pins the {@link BlockPipeHolder#isWireConnected} predicate that gates the
 * {@code logic_transportation} advancement. The advancement should fire when a wire
 * placement actually completes a connection — not for an isolated wire — so the
 * predicate must distinguish four cases: isolated, in-cube neighbour matching colour,
 * in-cube neighbour wrong colour, and cross-tile neighbour matching colour.
 *
 * <p>A second group pins what the server keeps for wires that cross between pipes. The wire systems
 * ({@link SavedDataWireSystems}) must follow a neighbour's removal, a blocking pluggable, a repaint, a pipe
 * connection forming or breaking, and NBT written into a pipe; and a pipe with no wires must drop a saved system
 * that still names a wire at its position. The drawn links ({@code WireManager.betweens}, part of the collision
 * shape) are asserted for the removal and the NBT write.
 */
public class PipeWireConnectivityTester {

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos) {
        return placePipe(helper, relPos, new ItemStack(BCTransportItems.PIPE_STRUCTURE.get()));
    }

    private static TilePipeHolder placePipe(GameTestHelper helper, BlockPos relPos, ItemStack pipeItem) {
        helper.setBlock(relPos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(relPos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(relPos);*/
        //?}
        tile.onPlacedBy(null, pipeItem);
        return tile;
    }

    /** An isolated wire on an otherwise-empty pipe is not "connected". */
    public static void testIsolatedWireNotConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);

        helper.assertFalse(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Isolated wire must not register as connected");
        helper.succeed();
    }

    /** Two same-colour wires at adjacent corners of the same cube are connected. */
    public static void testInCubeSameColourConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        // WEST_DOWN_NORTH (0,0,0) and EAST_DOWN_NORTH (1,0,0) are adjacent in X within the same cube.
        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);
        tile.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.RED);

        helper.assertTrue(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Two same-colour wires at adjacent corners must register as connected");
        helper.succeed();
    }

    /** Adjacent wires of different colours are not connected. */
    public static void testInCubeDifferentColourNotConnected(GameTestHelper helper) {
        BlockPos relPos = new BlockPos(1, 2, 1);
        TilePipeHolder tile = placePipe(helper, relPos);
        BlockPos worldPos = helper.absolutePos(relPos);

        tile.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED);
        tile.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.BLUE);

        helper.assertFalse(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), worldPos, tile, EnumWirePart.WEST_DOWN_NORTH, DyeColor.RED),
            "Adjacent wires of different colours must not register as connected");
        helper.succeed();
    }

    /** A same-colour wire on an adjacent pipe (reached via WireNode.offset across the boundary)
     *  counts as connected. EAST_DOWN_NORTH on the west tile offsets to WEST_DOWN_NORTH on the
     *  east tile when stepping +X, so a wire at each of those positions forms a cross-tile pair. */
    public static void testCrossTileSameColourConnected(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(1, 2, 1);
        BlockPos eastRel = new BlockPos(2, 2, 1);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel);

        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);

        helper.assertTrue(
            BlockPipeHolder.isWireConnected(
                helper.getLevel(), helper.absolutePos(westRel), west,
                EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW),
            "Same-colour wires on adjacent tiles at the touching corners must register as connected");
        helper.succeed();
    }

    /** Poll budget, counted from the cell ticking, for the link to form and then to drop (#243). */
    private static final int LINK_WORK_TICKS = 60;

    /** The server wire systems holding {@code part} of the pipe at {@code relPos}. */
    private static List<WireSystem> systemsOf(GameTestHelper helper, BlockPos relPos, EnumWirePart part) {
        return SavedDataWireSystems.get(helper.getLevel())
            .getWireSystemsWithElement(new WireSystem.WireElement(helper.absolutePos(relPos), part));
    }

    /** Whether one wire system holds both wires, so power on one reaches the other. */
    private static boolean inOneSystem(GameTestHelper helper, BlockPos aRel, EnumWirePart aPart, BlockPos bRel,
            EnumWirePart bPart) {
        WireSystem.WireElement b = new WireSystem.WireElement(helper.absolutePos(bRel), bPart);
        for (WireSystem system : systemsOf(helper, aRel, aPart)) {
            if (system.hasElement(b)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the two wires are in separate systems, each still in one (a cut, with neither wire left in none). */
    private static boolean inSeparateSystems(GameTestHelper helper, BlockPos aRel, EnumWirePart aPart, BlockPos bRel,
            EnumWirePart bPart) {
        return !inOneSystem(helper, aRel, aPart, bRel, bPart)
            && !systemsOf(helper, aRel, aPart).isEmpty()
            && !systemsOf(helper, bRel, bPart).isEmpty();
    }

    /** The server drops a wire link into a neighbouring pipe once that pipe is removed, and the collision shape (which
     *  merges the link's box) drops it too. Before #243 the server recomputed the links only on its own wire edits,
     *  so the survivor kept a solid sliver reaching into the empty block. A structure pipe beside a wood item pipe
     *  links wires without the pipes connecting, so the removal changes no connection and only the removed pipe's
     *  own neighbour refresh can drop the link. The server wire system must drop the removed pipe too (#246). */
    public static void testCrossTileLinkDropsWhenNeighbourRemoved(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos eastRel = new BlockPos(3, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);
        BlockPos westAbs = helper.absolutePos(westRel);
        // Inside EAST_DOWN_NORTH's link box (x 13..16, y 3..4, z 3..4) and outside the pipe's own arm.
        double px = 14.5 / 16, py = 3.5 / 16, pz = 3.5 / 16;
        BooleanSupplier linkInShape = () -> helper.getLevel().getBlockState(westAbs)
            .getCollisionShape(helper.getLevel(), westAbs, CollisionContext.empty()).toAabbs().stream()
            .anyMatch(box -> box.contains(px, py, pz));

        EntityArenaUtil.forceLoadCell(helper);
        boolean[] removed = { false };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                boolean linked = west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH);
                if (!removed[0]) {
                    // Phase 1: the link (and its box) exists while the neighbour is there.
                    if (linked && linkInShape.getAsBoolean()) {
                        helper.setBlock(eastRel, Blocks.AIR);
                        removed[0] = true;
                    }
                    return false;
                }
                // Phase 2: both go once the neighbour is gone, and the surviving wire keeps a wire system that
                // no longer names the removed pipe.
                List<WireSystem> westSystems = systemsOf(helper, westRel, EnumWirePart.EAST_DOWN_NORTH);
                WireSystem.WireElement eastWire =
                    new WireSystem.WireElement(helper.absolutePos(eastRel), EnumWirePart.WEST_DOWN_NORTH);
                boolean systemDroppedPipe = !westSystems.isEmpty()
                    && westSystems.stream().noneMatch(system -> system.hasElement(eastWire));
                return !linked && !linkInShape.getAsBoolean() && systemDroppedPipe;
            }, () -> {
                boolean linked = west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH);
                boolean inShape = linkInShape.getAsBoolean();
                List<WireSystem> westSystems = systemsOf(helper, westRel, EnumWirePart.EAST_DOWN_NORTH);
                String state = "(betweens " + (linked ? "has" : "lacks") + " the link, collision shape "
                    + (inShape ? "has" : "lacks") + " its box, " + westSystems.size() + " system"
                    + (westSystems.size() == 1 ? "" : "s") + " hold the surviving wire)";
                return (removed[0] ? "the server still has wire state for the removed pipe " : "the link never formed ")
                    + state;
            }),
            helper::succeed, "wire link never dropped", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }

    /** The server wire systems must follow a structure pipe's unconnected link: a blocking pluggable across the face
     *  cuts it, a colour mismatch cuts it, and taking either away restores it. Before #246 the server rebuilt the
     *  systems only when a wire part moved, so it kept one system across a cut — a signal still crossed a link that
     *  was no longer drawn — and never joined the two wires again once the link came back. (#246) */
    public static void testWireSystemFollowsUnconnectedLink(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos eastRel = new BlockPos(3, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);

        EntityArenaUtil.forceLoadCell(helper);
        int[] phase = { 0 };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                boolean together = inOneSystem(helper, westRel, EnumWirePart.EAST_DOWN_NORTH, eastRel,
                    EnumWirePart.WEST_DOWN_NORTH);
                boolean apart = inSeparateSystems(helper, westRel, EnumWirePart.EAST_DOWN_NORTH, eastRel,
                    EnumWirePart.WEST_DOWN_NORTH);
                switch (phase[0]) {
                    case 0 -> {
                        // The structure pipe links the wires with no pipe connection between them.
                        if (together) {
                            west.replacePluggable(Direction.EAST,
                                new PluggableBlocker(BCTransportPlugs.blocker, west, Direction.EAST));
                            phase[0] = 1;
                        }
                        return false;
                    }
                    case 1 -> {
                        // The blocker cuts the link; each wire keeps a system of its own.
                        if (apart) {
                            west.replacePluggable(Direction.EAST, null);
                            phase[0] = 2;
                        }
                        return false;
                    }
                    case 2 -> {
                        // Mismatched pipe colours cut a structure pipe's link too.
                        if (together) {
                            west.getPipe().setColour(DyeColor.RED);
                            east.getPipe().setColour(DyeColor.BLUE);
                            phase[0] = 3;
                        }
                        return false;
                    }
                    case 3 -> {
                        // Matching colours restore it.
                        if (apart) {
                            east.getPipe().setColour(DyeColor.RED);
                            phase[0] = 4;
                        }
                        return false;
                    }
                    default -> {
                        return together;
                    }
                }
            }, () -> {
                if (phase[0] == 0) {
                    return "the wires never shared a system";
                } else if (phase[0] == 1) {
                    return "the blocker never split the wire system";
                } else if (phase[0] == 2) {
                    return "removing the blocker never rejoined it";
                } else if (phase[0] == 3) {
                    return "mismatched pipe colours never split it";
                }
                return "matching colours never rejoined it";
            }),
            helper::succeed, "wire system never followed the unconnected link", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }

    /** Two item pipes link wires only through their pipe connection, which forms on their first tick after the wires
     *  were already placed, and a blocking pluggable across the face breaks that connection and with it the link.
     *  Before #246 the server rebuilt the wire systems only when a wire part moved, so the two never shared a system
     *  however the link was drawn, and after the blocker cut it each kept a system reaching across nothing. (#246) */
    public static void testWireSystemFollowsConnection(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos eastRel = new BlockPos(3, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_COBBLE_ITEM.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);

        EntityArenaUtil.forceLoadCell(helper);
        int[] phase = { 0 };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                boolean together = inOneSystem(helper, westRel, EnumWirePart.EAST_DOWN_NORTH, eastRel,
                    EnumWirePart.WEST_DOWN_NORTH);
                boolean apart = inSeparateSystems(helper, westRel, EnumWirePart.EAST_DOWN_NORTH, eastRel,
                    EnumWirePart.WEST_DOWN_NORTH);
                switch (phase[0]) {
                    case 0 -> {
                        // The pipes connect on their first tick, joining the wires through that connection.
                        if (together) {
                            west.replacePluggable(Direction.EAST,
                                new PluggableBlocker(BCTransportPlugs.blocker, west, Direction.EAST));
                            phase[0] = 1;
                        }
                        return false;
                    }
                    case 1 -> {
                        // The blocker breaks the connection and with it the link.
                        if (apart) {
                            west.replacePluggable(Direction.EAST, null);
                            phase[0] = 2;
                        }
                        return false;
                    }
                    default -> {
                        return together;
                    }
                }
            }, () -> {
                if (phase[0] == 0) {
                    return "the pipe connection never joined the wires in one system";
                } else if (phase[0] == 1) {
                    return "the blocker never split the wire system";
                }
                return "removing the blocker never rejoined it";
            }),
            helper::succeed, "wire system never followed the pipe connection", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }

    /** Wires on a kinesis pipe and on the pipe whose Power Adaptor faces it stay in separate wire systems, each wire
     *  in exactly one, and no wire link is drawn across the adaptor. The kinesis pipes keep their tile connection to
     *  the adaptor (that is how power reaches the wooden pipe), but that connection is one pipe's alone, and a wire
     *  rule that answers from it alone links the wires from the kinesis side while cutting them from the adaptor's —
     *  so a wire-system walk depends on where it starts, and one rebuild pass strands a wire in no system. (#251) */
    public static void testWireSystemCutByPowerAdaptor(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos midRel = new BlockPos(3, 2, 2);
        BlockPos eastRel = new BlockPos(4, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel, new ItemStack(BCTransportItems.PIPE_STONE_POWER.get()));
        TilePipeHolder mid = placePipe(helper, midRel, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_STONE_POWER.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        mid.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);
        mid.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);
        // The wooden pipe's behaviour is an MJ receiver, which the adaptor exposes to the kinesis pipes, so both
        // kinesis pipes connect to the adaptor faces while the wooden pipe connects to neither of them.
        mid.replacePluggable(Direction.WEST,
            new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor, mid, Direction.WEST));
        mid.replacePluggable(Direction.EAST,
            new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor, mid, Direction.EAST));

        EntityArenaUtil.forceLoadCell(helper);
        int[] phase = { 0 };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                switch (phase[0]) {
                    case 0 -> {
                        // The precondition of the bug: both kinesis pipes count the adaptor face connected, while the
                        // adaptor's own pipe counts neither. Mark all three stale in one pass, west first — the
                        // order that left a wire without a system before the fix.
                        if (west.getPipe().isConnected(Direction.EAST) && east.getPipe().isConnected(Direction.WEST)) {
                            SavedDataWireSystems.get(helper.getLevel()).markStale(helper.absolutePos(westRel));
                            SavedDataWireSystems.get(helper.getLevel()).markStale(helper.absolutePos(midRel));
                            SavedDataWireSystems.get(helper.getLevel()).markStale(helper.absolutePos(eastRel));
                            phase[0] = 1;
                        }
                        return false;
                    }
                    default -> {
                        // The cut: the four wires in three systems (the adaptor's pipe's two wires share theirs), the
                        // rule answering the same from both pipes, no link drawn across either adaptor face, and the
                        // kinesis pipes still connected to the adaptor, so power still flows.
                        boolean westCutFromMid = inSeparateSystems(helper, westRel, EnumWirePart.EAST_DOWN_NORTH,
                            midRel, EnumWirePart.WEST_DOWN_NORTH);
                        boolean eastCutFromMid = inSeparateSystems(helper, eastRel, EnumWirePart.WEST_DOWN_NORTH,
                            midRel, EnumWirePart.EAST_DOWN_NORTH);
                        boolean westCutFromEast = inSeparateSystems(helper, westRel, EnumWirePart.EAST_DOWN_NORTH,
                            eastRel, EnumWirePart.WEST_DOWN_NORTH);
                        boolean midWiresTogether = inOneSystem(helper, midRel, EnumWirePart.WEST_DOWN_NORTH, midRel,
                            EnumWirePart.EAST_DOWN_NORTH);
                        boolean sameAnswerFromBothPipes = !WireSystem.canWireConnect(west, Direction.EAST)
                            && !WireSystem.canWireConnect(mid, Direction.WEST)
                            && !WireSystem.canWireConnect(mid, Direction.EAST)
                            && !WireSystem.canWireConnect(east, Direction.WEST);
                        // Each pipe draws its own half of a link into its neighbour, so all four halves are read.
                        boolean noLinkDrawnAcrossAdaptor =
                            !west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH)
                                && !mid.getWireManager().betweens.containsKey(EnumWireBetween.WEST_DOWN_NORTH)
                                && !mid.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH)
                                && !east.getWireManager().betweens.containsKey(EnumWireBetween.WEST_DOWN_NORTH);
                        boolean powerStillFlows = west.getPipe().isConnected(Direction.EAST)
                            && east.getPipe().isConnected(Direction.WEST);
                        return westCutFromMid && eastCutFromMid && westCutFromEast && midWiresTogether
                            && sameAnswerFromBothPipes && noLinkDrawnAcrossAdaptor && powerStillFlows;
                    }
                }
            }, () -> {
                if (phase[0] == 0) {
                    return "the kinesis pipes never connected to the power adaptor (west "
                        + (west.getPipe().isConnected(Direction.EAST) ? "did" : "did not") + ", east "
                        + (east.getPipe().isConnected(Direction.WEST) ? "did" : "did not") + ")";
                }
                return "the wires are not cut from the adaptor: the west kinesis pipe's wire is in "
                    + systemsOf(helper, westRel, EnumWirePart.EAST_DOWN_NORTH).size() + " system(s), the adaptor's"
                    + " pipe's west wire in " + systemsOf(helper, midRel, EnumWirePart.WEST_DOWN_NORTH).size()
                    + ", its east wire in " + systemsOf(helper, midRel, EnumWirePart.EAST_DOWN_NORTH).size()
                    + ", the east kinesis pipe's wire in "
                    + systemsOf(helper, eastRel, EnumWirePart.WEST_DOWN_NORTH).size() + " (0 = left in no system)"
                    + (west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH)
                        ? ", a link is still drawn from the west kinesis pipe across the adaptor" : "")
                    + (mid.getWireManager().betweens.containsKey(EnumWireBetween.WEST_DOWN_NORTH)
                        ? ", a link is still drawn from the adaptor's pipe to the west kinesis pipe" : "")
                    + (mid.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH)
                        ? ", a link is still drawn from the adaptor's pipe to the east kinesis pipe" : "")
                    + (east.getWireManager().betweens.containsKey(EnumWireBetween.WEST_DOWN_NORTH)
                        ? ", a link is still drawn from the east kinesis pipe across the adaptor" : "")
                    + (!west.getPipe().isConnected(Direction.EAST) || !east.getPipe().isConnected(Direction.WEST)
                        ? ", and a kinesis pipe lost its power connection to the adaptor" : "");
            }),
            helper::succeed, "the power adaptor never cut the wire system", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(midRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }

    /** NBT written into a pipe that is already live (a data command, another mod's write) replaces its pipe,
     *  pluggables and wire parts, so the server's wire systems, wire links and the pipe's connections must follow
     *  what was written: before #250 the read refreshed only the client, leaving the server describing the wires
     *  that were there before the write — a system still reaching into a pipe whose wires were removed, and a link
     *  still drawn (and solid, in the collision shape) into it. (#250) */
    public static void testWireSystemFollowsNbtWrite(GameTestHelper helper) {
        BlockPos westRel = new BlockPos(2, 2, 2);
        BlockPos eastRel = new BlockPos(3, 2, 2);
        TilePipeHolder west = placePipe(helper, westRel);
        TilePipeHolder east = placePipe(helper, eastRel, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        west.getWireManager().addPart(EnumWirePart.EAST_DOWN_NORTH, DyeColor.YELLOW);
        east.getWireManager().addPart(EnumWirePart.WEST_DOWN_NORTH, DyeColor.YELLOW);

        EntityArenaUtil.forceLoadCell(helper);
        int[] phase = { 0 };
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS, () -> {
                switch (phase[0]) {
                    case 0 -> {
                        // The wires share a system, the link is drawn and the east pipe has ticked.
                        if (inOneSystem(helper, westRel, EnumWirePart.EAST_DOWN_NORTH, eastRel,
                            EnumWirePart.WEST_DOWN_NORTH)
                                && west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH)
                                && east.wireManager.initialised) {
                            ServerLevel level = helper.getLevel();
                            CompoundTag tag = east.saveCustomOnly(level.registryAccess());
                            // A tag without the wires key names no wires, as one without plugs names no pluggables.
                            tag.remove("wires");
                            //? if >=1.21.10 {
                            east.loadCustomOnly(
                                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                            //?} else {
                            /*east.loadCustomOnly(tag, level.registryAccess());*/
                            //?}
                            phase[0] = 1;
                        }
                        return false;
                    }
                    default -> {
                        // The east pipe has no wires left, the west wire keeps a system that no longer names the
                        // east element, and the link into the east pipe is gone.
                        WireSystem.WireElement eastWire =
                            new WireSystem.WireElement(helper.absolutePos(eastRel), EnumWirePart.WEST_DOWN_NORTH);
                        List<WireSystem> westSystems = systemsOf(helper, westRel, EnumWirePart.EAST_DOWN_NORTH);
                        return east.getWireManager().parts.isEmpty()
                            && !westSystems.isEmpty()
                            && westSystems.stream().noneMatch(system -> system.hasElement(eastWire))
                            && !west.getWireManager().betweens.containsKey(EnumWireBetween.EAST_DOWN_NORTH);
                    }
                }
            }, () -> {
                if (phase[0] == 0) {
                    return "the wires never shared a system before the NBT write";
                }
                return "the wire system never followed the NBT write";
            }),
            helper::succeed, "wire system never followed the NBT write", () -> {
                helper.setBlock(westRel, Blocks.AIR);
                helper.setBlock(eastRel, Blocks.AIR);
            });
    }

    /** A pipe with no wires drops, on its first tick, a wire system that still names a wire at its position: the wire
     *  went without the systems being told (NBT written into the pipe before it first ticked, or a save that already
     *  held such a system). Left registered, the system keeps joining its other wires through a wire that is gone.
     *  Built straight from one element, as a system read back from disk is. (#250) */
    public static void testFirstTickDropsSystemNamingMissingWire(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        TilePipeHolder tile = placePipe(helper, rel);
        SavedDataWireSystems wireSystems = SavedDataWireSystems.get(helper.getLevel());
        WireSystem ghost = new WireSystem(ImmutableList.of(
            new WireSystem.WireElement(helper.absolutePos(rel), EnumWirePart.EAST_DOWN_NORTH)), DyeColor.YELLOW);
        wireSystems.addWireSystem(ghost, false);

        EntityArenaUtil.forceLoadCell(helper);
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + LINK_WORK_TICKS,
            EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, LINK_WORK_TICKS,
                () -> tile.wireManager.initialised && systemsOf(helper, rel, EnumWirePart.EAST_DOWN_NORTH).isEmpty(),
                () -> "the pipe " + (tile.wireManager.initialised ? "ticked" : "never ticked")
                    + " and the system naming its missing wire is still registered"),
            helper::succeed, "system naming a missing wire never dropped", () -> {
                wireSystems.removeWireSystem(ghost);
                helper.setBlock(rel, Blocks.AIR);
            });
    }
}
