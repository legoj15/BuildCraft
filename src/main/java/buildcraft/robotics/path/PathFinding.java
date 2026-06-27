/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

import net.minecraft.core.BlockPos;

/**
 * 3D A* path finder, ported from 7.1.x {@code buildcraft.core.lib.utils.PathFinding}. Follows the
 * guidelines at http://www.policyalmanac.org/games/aStarTutorial.htm.
 *
 * <p>Two deliberate, behaviour-preserving changes were made in the port:
 * <ul>
 * <li>The live {@code World} dependency was replaced with the injectable {@link SoftBlockAccess}
 *     predicate. The 7.1.x {@code y < 0} void guard is folded into {@code isSoft} (positions outside
 *     the world report not-soft), which also makes the finder correct on modern negative-Y worlds.</li>
 * <li>A deterministic tie-break was added to {@link #findSmallerWeight}: when two open nodes share the
 *     same total weight the one with the smaller {@link BlockPos#asLong()} wins. 7.1.x relied on
 *     {@link HashMap} iteration order here, so identical inputs could yield different (equal-cost)
 *     paths; the tie-break makes the result a pure function of the inputs, which the determinism tests
 *     depend on.</li>
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
    private final HashMap<BlockPos, Node> closedList = new HashMap<>();

    private Node nextIteration;

    private LinkedList<BlockPos> result;

    private boolean endReached = false;

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
            } else {
                nextIteration = iterate(nextIteration);
            }
        }
    }

    @Override
    public boolean isDone() {
        return nextIteration == null;
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

        ArrayList<Node> nodes = new ArrayList<>();
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
                            nextNode = tentative;
                        } else {
                            openList.put(nextNode.index, nextNode);
                        }
                    } else {
                        openList.put(nextNode.index, nextNode);
                    }

                    nodes.add(nextNode);
                }
            }
        }

        nodes.addAll(openList.values());

        return findSmallerWeight(nodes);
    }

    private static Node findSmallerWeight(Collection<Node> collection) {
        Node found = null;

        for (Node n : collection) {
            if (found == null) {
                found = n;
            } else if (n.totalWeight < found.totalWeight) {
                found = n;
            } else if (n.totalWeight == found.totalWeight
                    && Long.compareUnsigned(n.index.asLong(), found.index.asLong()) < 0) {
                // Deterministic tie-break (see class javadoc): equal-cost nodes resolve by a stable
                // ordering of their position rather than by HashMap iteration order.
                found = n;
            }
        }

        return found;
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
