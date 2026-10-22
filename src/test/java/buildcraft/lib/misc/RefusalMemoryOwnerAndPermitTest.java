/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What every {@link RefusalMemory} shares whatever its window: answers are about one owner (a machine that now works
 * for someone else starts with a clean slate), the one standing "yes" (the permit) is about one block in one state,
 * and each machine's policy is the documented one.
 */
public class RefusalMemoryOwnerAndPermitTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final GameProfile ALICE = profile("bc_alice");
    private static final GameProfile BOB = profile("bc_bob");

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    @Test
    public void aRefusalIsAboutTheOwnerItWasRefusedTo() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.ROBOT_SEARCH);
        refusals.remember(POS, STONE, 0, ALICE);
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 1, ALICE), "refused to Alice");
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 1, BOB), "a refusal to Alice says nothing about Bob");
        Assertions.assertFalse(refusals.isRefused(POS, STONE, 2, ALICE),
                "and the machine working for Bob now dropped Alice's answers: she is asked afresh if it goes back");
    }

    @Test
    public void ownersThatActAsBuildCraftShareOneKey() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.ROBOT_SEARCH);
        refusals.remember(POS, STONE, 0, null);
        GameProfile nameless = new GameProfile(UUID.nameUUIDFromBytes("bc_nameless".getBytes(StandardCharsets.UTF_8)),
                "");
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 1, nameless),
                "an unowned and a nameless machine both ask as [BuildCraft] (MachineIdentity): one key");
        Assertions.assertTrue(refusals.isRefused(POS, STONE, 1, FakePlayerUtil.BUILDCRAFT_PROFILE));
    }

    @Test
    public void aPermitHoldsForOneBlockInOneState() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.STRIPES);
        Assertions.assertFalse(refusals.isPermitted(POS, STONE, ALICE), "nothing was permitted yet");
        refusals.permit(POS, STONE, ALICE);
        Assertions.assertTrue(refusals.isPermitted(POS, STONE, ALICE));
        Assertions.assertEquals(POS, refusals.permittedPos());
        Assertions.assertFalse(refusals.isPermitted(POS.above(), STONE, ALICE), "another position is another block");
        Assertions.assertFalse(refusals.isPermitted(POS, DIRT, ALICE), "a replaced block is another block");
        Assertions.assertFalse(refusals.isPermitted(POS, STONE, BOB), "a permit to Alice is not one to Bob");
        Assertions.assertFalse(refusals.isPermitted(POS, STONE, ALICE),
                "and switching owner revoked it: the machine asks Alice's claim again if it goes back");
    }

    @Test
    public void revokingAPermitEndsIt() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.DRAIN);
        BlockPos.MutableBlockPos cursor = POS.mutable();
        refusals.permit(cursor, STONE, null);
        cursor.move(0, 5, 0);
        Assertions.assertTrue(refusals.isPermitted(POS, STONE, null), "the permitted position did not move with the cursor");
        refusals.revokePermit();
        Assertions.assertFalse(refusals.isPermitted(POS, STONE, null));
        Assertions.assertNull(refusals.permittedPos());
    }

    @Test
    public void aRefusalRevokesAPermitForTheSameBlock() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.STRIPES);
        refusals.permit(POS, STONE, null);
        refusals.remember(POS, STONE, 0, null);
        Assertions.assertFalse(refusals.isPermitted(POS, STONE, null), "a block refused since is no longer permitted");
    }

    @Test
    public void takeExpiredNeedsAFixedWindow() {
        RefusalMemory refusals = new RefusalMemory(RefusalMemory.DRAIN);
        Assertions.assertThrows(IllegalStateException.class, () -> refusals.takeExpired(0, 1),
                "a spaced window does not end in insertion order, so the cheap scan cannot answer");
    }

    /** The policy table in {@link RefusalMemory}'s Javadoc, pinned: change both together. */
    @Test
    public void eachMachineKeepsItsDocumentedPolicy() {
        Assertions.assertEquals(List.of(200L, 0L, Integer.MAX_VALUE), shape(RefusalMemory.QUARRY_FRAME));
        Assertions.assertEquals(List.of(200L, 0L, Integer.MAX_VALUE), shape(RefusalMemory.BUILDER_PLACE));
        Assertions.assertEquals(List.of(200L, 0L, 4), shape(RefusalMemory.STRIPES));
        Assertions.assertEquals(List.of(1200L, 0L, 256), shape(RefusalMemory.ROBOT_SEARCH));
        Assertions.assertEquals(List.of(6000L, 20L, Integer.MAX_VALUE), shape(RefusalMemory.DRAIN));
    }

    private static List<Object> shape(RefusalMemory.Policy policy) {
        return List.of(policy.windowTicks(), policy.spacingTicks(), policy.capacity());
    }
}
