/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersEventDist;

/**
 * Pins {@link BCBuildersEventDist#findOwnersToAward} — the pure predicate behind the
 * {@code destroying_the_world} advancement scan. The full grant path
 * ({@link BCBuildersEventDist#onServerTick} → {@link buildcraft.lib.misc.AdvancementUtil#unlockAdvancement})
 * requires a {@code ServerPlayer}, which {@link GameTestHelper#makeMockPlayer} cannot
 * produce (see CLAUDE.md "Player-state testing limitation"), so the award itself is
 * verified in-client. Here we cover the predicate's four eligibility rules — same
 * owner, both 64×64+, both within the freshness window, non-null owner — by stamping
 * tile state directly via the package-private {@link TileQuarry#lastFullSpeedTick}.
 *
 * Each test uses a freshly-randomised owner UUID so leftover quarries from prior tests
 * (the test world's overworld is shared and {@code allQuarries} is keyed by Level)
 * cannot taint the assertion.
 */
public class DestroyingTheWorldTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Two 64×64 quarries with the same owner, both stamped this tick → owner is in the
     *  award set. */
    public static void samePlayerTwoFullQuarriesGrants(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            UUID owner = UUID.randomUUID();
            TileQuarry q1 = placeQuarry(helper, new BlockPos(1, 2, 1));
            TileQuarry q2 = placeQuarry(helper, new BlockPos(5, 2, 1));
            setSize64(q1);
            setSize64(q2);
            q1.setOwner(new GameProfile(owner, "test"));
            q2.setOwner(new GameProfile(owner, "test"));
            long now = level.getGameTime();
            q1.lastFullSpeedTick = now;
            q2.lastFullSpeedTick = now;

            Set<UUID> winners = BCBuildersEventDist.INSTANCE.findOwnersToAward(level, now);
            assertTrue(winners.contains(owner),
                    "owner with two qualifying quarries must be in the award set");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Two 64×64 quarries with different owners → neither owner is awarded (the
     *  predicate counts per-owner, and each owner only has one matching quarry). */
    public static void differentOwnersDoNotGrant(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            UUID ownerA = UUID.randomUUID();
            UUID ownerB = UUID.randomUUID();
            TileQuarry q1 = placeQuarry(helper, new BlockPos(1, 2, 1));
            TileQuarry q2 = placeQuarry(helper, new BlockPos(5, 2, 1));
            setSize64(q1);
            setSize64(q2);
            q1.setOwner(new GameProfile(ownerA, "a"));
            q2.setOwner(new GameProfile(ownerB, "b"));
            long now = level.getGameTime();
            q1.lastFullSpeedTick = now;
            q2.lastFullSpeedTick = now;

            Set<UUID> winners = BCBuildersEventDist.INSTANCE.findOwnersToAward(level, now);
            assertTrue(!winners.contains(ownerA) && !winners.contains(ownerB),
                    "owners with only one matching quarry each must not be in the award set");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Same owner, two quarries, both stamped — but only one is 64×64. Predicate filters
     *  the undersized one before counting, so the owner is short of the 2-pair threshold. */
    public static void undersizedFrameDoesNotGrant(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            UUID owner = UUID.randomUUID();
            TileQuarry q1 = placeQuarry(helper, new BlockPos(1, 2, 1));
            TileQuarry q2 = placeQuarry(helper, new BlockPos(5, 2, 1));
            setSize64(q1);
            setSize(q2, 32); // smaller than 64 — gets filtered out
            q1.setOwner(new GameProfile(owner, "test"));
            q2.setOwner(new GameProfile(owner, "test"));
            long now = level.getGameTime();
            q1.lastFullSpeedTick = now;
            q2.lastFullSpeedTick = now;

            Set<UUID> winners = BCBuildersEventDist.INSTANCE.findOwnersToAward(level, now);
            assertTrue(!winners.contains(owner),
                    "owner with only one 64×64 quarry (other is 32×32) must not be in the award set");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Two 64×64 quarries placed by the same owner but never powered (lastFullSpeedTick is
     *  still at the {@code Long.MIN_VALUE} default). Regression for the overflow where
     *  {@code currentTick - Long.MIN_VALUE} wraps to a large negative and falsely passed the
     *  freshness gate, causing the advancement to fire on placement of two unpowered quarries. */
    public static void neverStampedDoesNotGrant(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            UUID owner = UUID.randomUUID();
            TileQuarry q1 = placeQuarry(helper, new BlockPos(1, 2, 1));
            TileQuarry q2 = placeQuarry(helper, new BlockPos(5, 2, 1));
            setSize64(q1);
            setSize64(q2);
            q1.setOwner(new GameProfile(owner, "test"));
            q2.setOwner(new GameProfile(owner, "test"));
            // Deliberately do NOT touch lastFullSpeedTick — both stay at Long.MIN_VALUE.

            Set<UUID> winners = BCBuildersEventDist.INSTANCE.findOwnersToAward(level, level.getGameTime());
            assertTrue(!winners.contains(owner),
                    "owner with two never-powered quarries must not be in the award set");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /** Both 64×64 with the same owner, but one's stamp is older than the window. Predicate
     *  drops the stale quarry, leaving the owner short of the 2-pair threshold. */
    public static void outsideWindowDoesNotGrant(GameTestHelper helper) {
        try {
            ServerLevel level = helper.getLevel();
            UUID owner = UUID.randomUUID();
            TileQuarry q1 = placeQuarry(helper, new BlockPos(1, 2, 1));
            TileQuarry q2 = placeQuarry(helper, new BlockPos(5, 2, 1));
            setSize64(q1);
            setSize64(q2);
            q1.setOwner(new GameProfile(owner, "test"));
            q2.setOwner(new GameProfile(owner, "test"));
            long now = level.getGameTime();
            q1.lastFullSpeedTick = now;
            // q2 was last at full speed FULL_SPEED_WINDOW_TICKS+1 ticks ago — outside window
            q2.lastFullSpeedTick = now - (BCBuildersEventDist.FULL_SPEED_WINDOW_TICKS + 1);

            Set<UUID> winners = BCBuildersEventDist.INSTANCE.findOwnersToAward(level, now);
            assertTrue(!winners.contains(owner),
                    "owner with one stale quarry must not be in the award set");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    // --- helpers ---

    private static TileQuarry placeQuarry(GameTestHelper helper, BlockPos localPos) {
        helper.setBlock(localPos, BCBuildersBlocks.QUARRY.get());
        //? if >=1.21.10 {
        TileQuarry q = helper.getBlockEntity(localPos, TileQuarry.class);
        //?} else {
        /*TileQuarry q = helper.getBlockEntity(localPos);*/
        //?}
        assertTrue(q != null, "quarry BE must be present at " + localPos);
        return q;
    }

    private static void setSize64(TileQuarry q) {
        setSize(q, 64);
    }

    /** Make the quarry's frame box {@code edge × edge} on the XZ plane, anchored at the
     *  block position, height 4 (the default quarry frame minimum). */
    private static void setSize(TileQuarry q, int edge) {
        BlockPos p = q.getBlockPos();
        q.frameBox.reset();
        q.frameBox.setMin(p);
        q.frameBox.setMax(p.offset(edge - 1, 4, edge - 1));
    }
}
