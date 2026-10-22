/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.ResourceIdBlock;
import buildcraft.robotics.path.PathFindingSearch;

/** The one place that pairs the two ledgers a block a robot works is claimed in:
 *
 *  <ol>
 *  <li>the <b>search cell</b>: a robot's {@code PathFindingSearch} locks onto a target ({@link #searchCells});
 *      runtime-only, owner-tagged, freed with the robot ({@link IRobotRegistry#getBlockReservations(long)});</li>
 *  <li>the <b>job claim</b>: the {@link ResourceIdBlock} reservation the robot holds while it works the block -
 *      taken when its AI collects the search result ({@link #claim}), re-taken by a job reloaded from NBT (the same
 *      call), given back when the job ends ({@link #release}).</li>
 *  </ol>
 *
 *  <p>The invariant: a block is never held by two robots across the two. A search does not lock onto a block anyone
 *  holds in either ledger, a job claim is refused while another robot's search holds the cell, and the hand-over
 *  moves the cell into the job ledger instead of copying it. Every board's candidate filter asks {@link #isClaimed},
 *  so all of them follow the same rule. (Each ledger keeps its own liveness: search cells die with their robot's
 *  session; job claims are also dropped lazily once their robot is no longer loaded, and persist with the registry.)
 *
 *  <p>Without a registry (a client-side robot) nothing is claimed and nothing can be: {@link #isClaimed} answers
 *  false, {@link #claim} true.
 *
 *  <p>Deliberately not a layer on {@link buildcraft.lib.misc.PositionMemory} (the shared store under the refusal and
 *  unreachable-block memories): a claim has an owner and lasts until that owner gives it back or is gone, never until a
 *  window runs out, it is shared across robots through the registry rather than kept per machine, and the job ledger
 *  persists. Giving it a window or a cap would let two robots work one block. */
public final class BlockClaims {

    private BlockClaims() {}

    /** Whether any robot holds {@code pos}, as a search cell or as a job. */
    public static boolean isClaimed(@Nullable IRobotRegistry registry, BlockPos pos) {
        return registry != null
                && (registry.getBlockReservations().contains(pos) || registry.isTaken(new ResourceIdBlock(pos)));
    }

    /** Makes {@code robotId} the job holder of {@code pos}: true when it holds the job claim on return. Refused while
     *  another robot holds the block in either ledger. The robot's own search cell on it is handed over (freed) in the
     *  same step, whatever the outcome, so no cell outlives the search that made it. */
    public static boolean claim(@Nullable IRobotRegistry registry, BlockPos pos, long robotId) {
        if (registry == null) {
            return true;
        }
        Set<BlockPos> mine = registry.getBlockReservations(robotId);
        boolean ownCell = mine.remove(pos);
        if (!ownCell && registry.getBlockReservations().contains(pos)) {
            return false;
        }
        return registry.reclaim(new ResourceIdBlock(pos), robotId);
    }

    /** Gives {@code pos} back from {@code robotId}, in both ledgers, owner-checked: another robot's claim or cell on
     *  it is never touched. */
    public static void release(@Nullable IRobotRegistry registry, BlockPos pos, long robotId) {
        if (registry == null) {
            return;
        }
        registry.release(new ResourceIdBlock(pos), robotId);
        registry.getBlockReservations(robotId).remove(pos);
    }

    /** The reservation table a robot's search runs against: a cell is reserved when any robot holds it in either
     *  ledger, and the search reserves and frees its own cells only. */
    public static PathFindingSearch.Reservations searchCells(IRobotRegistry registry, long robotId) {
        Set<BlockPos> mine = registry.getBlockReservations(robotId);
        return new PathFindingSearch.Reservations() {
            @Override
            public boolean isReserved(BlockPos pos) {
                return isClaimed(registry, pos);
            }

            @Override
            public boolean reserve(BlockPos pos) {
                return !registry.isTaken(new ResourceIdBlock(pos)) && mine.add(pos);
            }

            @Override
            public void unreserve(BlockPos pos) {
                mine.remove(pos);
            }
        };
    }
}
