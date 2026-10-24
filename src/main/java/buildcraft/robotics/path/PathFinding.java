/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;

/**
 * 3D A* path finder, ported from 7.1.x {@code buildcraft.core.lib.utils.PathFinding}. Follows the
 * guidelines at http://www.policyalmanac.org/games/aStarTutorial.htm.
 *
 * <p>Deliberate, behaviour-preserving changes made in the port:
 * <ul>
 * <li>The live {@code World} dependency was replaced with the injectable {@link SoftBlockAccess}
 *     predicate. The 7.1.x {@code y < 0} void guard is folded into {@code isSoft} (positions outside
 *     the world report not-soft), which also makes the finder correct on modern negative-Y worlds.</li>
 * <li>A deterministic tie-break decides between open nodes of equal total weight: the one with the
 *     smaller {@link BlockPos#asLong()} wins. 7.1.x relied on {@code HashMap} iteration order here, so
 *     identical inputs could yield different (equal-cost) paths; the tie-break makes the result a pure
 *     function of the inputs, which the determinism tests depend on.</li>
 * <li>The open list is a {@link PriorityQueue} under that same ordering (with the {@link #openList}
 *     map tracking each position's current record, so superseded queue entries are skipped at poll
 *     time). The port originally re-copied the whole open list and linearly scanned it for the minimum
 *     on EVERY expansion — O(open) work per step, which is what made a wide search collapse the server
 *     tick; the queue selects the same minimum in O(log n) with no copying, in the same order, so
 *     results are unchanged.</li>
 * </ul>
 *
 * <p>Cost note: both the movement cost {@code g} and the heuristic {@code h} are squared Euclidean
 * distance, which is non-metric (a diagonal step costs more than two axis steps would). Tests must
 * therefore assert loose bounds on path length, never an exact optimal path.
 */
public class PathFinding implements IIterableAlgorithm {

    public static int PATH_ITERATIONS = 1000;

    private final SoftBlockAccess access;
    private final BlockPos start;
    private final BlockPos end;
    private double maxDistanceToEndSq = 0;
    private float maxTotalDistanceSq = 0;

    private final HashMap<BlockPos, Node> openList = new HashMap<>();
    /** The open list as a real priority queue: least (totalWeight, then unsigned {@code asLong}) first — the
     *  same ordering the per-expansion minimum scan used to pick. May hold superseded entries for a position
     *  whose record later improved (a priority queue cannot decrease a key); {@link #pollOpen} skips them, so
     *  they cost nothing but a discarded poll. */
    private final PriorityQueue<Node> openQueue = new PriorityQueue<>(PathFinding::compareNodes);
    private final HashMap<BlockPos, Node> closedList = new HashMap<>();

    private Node nextIteration;

    private LinkedList<BlockPos> result;

    private boolean endReached = false;

    private int expansions;
    private int expansionBudget = Integer.MAX_VALUE;

    public PathFinding(SoftBlockAccess access, BlockPos start, BlockPos end) {
        this.access = access;
        this.start = start;
        this.end = end;

        Node startNode = new Node();
        startNode.parent = null;
        startNode.movementCost = 0;
        startNode.destinationCost = distanceSq(start, end);
        startNode.totalWeight = startNode.movementCost + startNode.destinationCost;
        startNode.index = start;
        openList.put(start, startNode);
        openQueue.add(startNode);
        nextIteration = startNode;
    }

    public PathFinding(SoftBlockAccess access, BlockPos start, BlockPos end, double maxDistanceToEnd) {
        this(access, start, end);

        maxDistanceToEndSq = maxDistanceToEnd * maxDistanceToEnd;
    }

    public PathFinding(SoftBlockAccess access, BlockPos start, BlockPos end, double maxDistanceToEnd,
            float maxTotalDistance) {
        this(access, start, end, maxDistanceToEnd);

        maxTotalDistanceSq = maxTotalDistance * maxTotalDistance;
    }

    @Override
    public void iterate() {
        iterate(PATH_ITERATIONS);
    }

    public void iterate(int itNumber) {
        for (int i = 0; i < itNumber; ++i) {
            if (nextIteration == null) {
                return;
            }

            if (endReached) {
                result = new LinkedList<>();

                while (nextIteration != null) {
                    result.addFirst(nextIteration.index);
                    nextIteration = nextIteration.parent;
                }

                return;
            } else if (expansions >= expansionBudget) {
                // Out of budget: give up, done with no path. (Checked only before an expansion, so a finder whose
                // last expansion reached the end still builds its path.)
                nextIteration = null;
                return;
            } else {
                nextIteration = iterate(nextIteration);
                expansions++;
            }
        }
    }

    /** Caps the node expansions this finder may run: once spent, it ends done with no path. Unbounded by default. */
    public void setExpansionBudget(int budget) {
        expansionBudget = budget;
    }

    @Override
    public boolean isDone() {
        return nextIteration == null;
    }

    /** How many nodes this finder has expanded so far: the unit its callers budget it in. */
    public int expansions() {
        return expansions;
    }

    public LinkedList<BlockPos> getResult() {
        if (result != null) {
            return result;
        } else {
            return new LinkedList<>();
        }
    }

    public BlockPos end() {
        return end;
    }

    private Node iterate(Node from) {
        openList.remove(from.index);
        closedList.put(from.index, from);

        byte[][][] resultMoves = movements(from);

        for (int dx = -1; dx <= +1; ++dx) {
            for (int dy = -1; dy <= +1; ++dy) {
                for (int dz = -1; dz <= +1; ++dz) {
                    if (resultMoves[dx + 1][dy + 1][dz + 1] == 0) {
                        continue;
                    }

                    int x = from.index.getX() + dx;
                    int y = from.index.getY() + dy;
                    int z = from.index.getZ() + dz;

                    Node nextNode = new Node();
                    nextNode.parent = from;
                    nextNode.index = new BlockPos(x, y, z);

                    if (resultMoves[dx + 1][dy + 1][dz + 1] == 2) {
                        endReached = true;
                        return nextNode;
                    }

                    nextNode.movementCost = from.movementCost + distanceSq(nextNode.index, from.index);
                    nextNode.destinationCost = distanceSq(nextNode.index, end);
                    nextNode.totalWeight = nextNode.movementCost + nextNode.destinationCost;

                    if (maxTotalDistanceSq > 0 && nextNode.totalWeight > maxTotalDistanceSq) {
                        if (!closedList.containsKey(nextNode.index)) {
                            closedList.put(nextNode.index, nextNode);
                        }
                        continue;
                    }
                    if (closedList.containsKey(nextNode.index)) {
                        continue;
                    } else if (openList.containsKey(nextNode.index)) {
                        Node tentative = openList.get(nextNode.index);

                        if (tentative.movementCost < nextNode.movementCost) {
                            // The queued record already stands: it is in the queue, so nothing to do.
                            continue;
                        } else {
                            // A cheaper-or-equal way in: the new record replaces the map entry and joins the
                            // queue; the superseded entry is skipped by pollOpen when it eventually surfaces.
                            openList.put(nextNode.index, nextNode);
                            openQueue.add(nextNode);
                        }
                    } else {
                        openList.put(nextNode.index, nextNode);
                        openQueue.add(nextNode);
                    }
                }
            }
        }

        return pollOpen();
    }

    /** The open node to expand next — the least by (totalWeight, then unsigned {@code asLong}), exactly the
     *  node the old whole-open-list minimum scan picked. Superseded or already-expanded entries left in the
     *  queue are skipped: a position's queue entry only counts while it is still that position's record in
     *  {@link #openList}. Null when the open list is empty: the search is exhausted, done with no path. */
    private Node pollOpen() {
        while (!openQueue.isEmpty()) {
            Node candidate = openQueue.poll();
            if (openList.get(candidate.index) == candidate) {
                return candidate;
            }
        }
        return null;
    }

    /** The queue's ordering: least total weight first, ties broken by the smaller unsigned
     *  {@link BlockPos#asLong()} (see the class javadoc — the deterministic tie-break that makes the result
     *  independent of iteration order). */
    private static int compareNodes(Node a, Node b) {
        if (a.totalWeight != b.totalWeight) {
            return Double.compare(a.totalWeight, b.totalWeight);
        }
        return Long.compareUnsigned(a.index.asLong(), b.index.asLong());
    }

    private static class Node {
        public Node parent;
        public double movementCost;
        public double destinationCost;
        public double totalWeight;
        public BlockPos index;
    }

    private static double distanceSq(BlockPos i1, BlockPos i2) {
        double dx = (double) i1.getX() - (double) i2.getX();
        double dy = (double) i1.getY() - (double) i2.getY();
        double dz = (double) i1.getZ() - (double) i2.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    private boolean endReached(int x, int y, int z) {
        if (maxDistanceToEndSq == 0) {
            return end.getX() == x && end.getY() == y && end.getZ() == z;
        } else {
            BlockPos pos = new BlockPos(x, y, z);
            return access.isSoft(pos) && distanceSq(pos, end) <= maxDistanceToEndSq;
        }
    }

    private byte[][][] movements(Node from) {
        byte[][][] resultMoves = new byte[3][3][3];

        for (int dx = -1; dx <= +1; ++dx) {
            for (int dy = -1; dy <= +1; ++dy) {
                for (int dz = -1; dz <= +1; ++dz) {
                    int x = from.index.getX() + dx;
                    int y = from.index.getY() + dy;
                    int z = from.index.getZ() + dz;

                    if (endReached(x, y, z)) {
                        resultMoves[dx + 1][dy + 1][dz + 1] = 2;
                    } else if (!access.isSoft(new BlockPos(x, y, z))) {
                        // Folds 7.1.x's separate `y < 0` void guard: out-of-world cells report not-soft.
                        resultMoves[dx + 1][dy + 1][dz + 1] = 0;
                    } else {
                        resultMoves[dx + 1][dy + 1][dz + 1] = 1;
                    }
                }
            }
        }

        resultMoves[1][1][1] = 0;

        // Disallow diagonal moves that would clip the corner of a non-soft block: if an orthogonally
        // adjacent cell is blocked, every diagonal that depends on squeezing past it is also blocked.
        if (resultMoves[0][1][1] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[0][i][j] = 0;
                }
            }
        }

        if (resultMoves[2][1][1] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[2][i][j] = 0;
                }
            }
        }

        if (resultMoves[1][0][1] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[i][0][j] = 0;
                }
            }
        }

        if (resultMoves[1][2][1] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[i][2][j] = 0;
                }
            }
        }

        if (resultMoves[1][1][0] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[i][j][0] = 0;
                }
            }
        }

        if (resultMoves[1][1][2] == 0) {
            for (int i = 0; i <= 2; ++i) {
                for (int j = 0; j <= 2; ++j) {
                    resultMoves[i][j][2] = 0;
                }
            }
        }

        if (resultMoves[0][0][1] == 0) {
            resultMoves[0][0][0] = 0;
            resultMoves[0][0][2] = 0;
        }

        if (resultMoves[0][2][1] == 0) {
            resultMoves[0][2][0] = 0;
            resultMoves[0][2][2] = 0;
        }

        if (resultMoves[2][0][1] == 0) {
            resultMoves[2][0][0] = 0;
            resultMoves[2][0][2] = 0;
        }

        if (resultMoves[2][2][1] == 0) {
            resultMoves[2][2][0] = 0;
            resultMoves[2][2][2] = 0;
        }

        if (resultMoves[0][1][0] == 0) {
            resultMoves[0][0][0] = 0;
            resultMoves[0][2][0] = 0;
        }

        if (resultMoves[0][1][2] == 0) {
            resultMoves[0][0][2] = 0;
            resultMoves[0][2][2] = 0;
        }

        if (resultMoves[2][1][0] == 0) {
            resultMoves[2][0][0] = 0;
            resultMoves[2][2][0] = 0;
        }

        if (resultMoves[2][1][2] == 0) {
            resultMoves[2][0][2] = 0;
            resultMoves[2][2][2] = 0;
        }

        if (resultMoves[1][0][0] == 0) {
            resultMoves[0][0][0] = 0;
            resultMoves[2][0][0] = 0;
        }

        if (resultMoves[1][0][2] == 0) {
            resultMoves[0][0][2] = 0;
            resultMoves[2][0][2] = 0;
        }

        if (resultMoves[1][2][0] == 0) {
            resultMoves[0][2][0] = 0;
            resultMoves[2][2][0] = 0;
        }

        if (resultMoves[1][2][2] == 0) {
            resultMoves[0][2][2] = 0;
            resultMoves[2][2][2] = 0;
        }

        return resultMoves;
    }
}
