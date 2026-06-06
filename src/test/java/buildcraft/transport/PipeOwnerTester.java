/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import buildcraft.transport.tile.TilePipeHolder;

/**
 * Regression guard for pipe owner tracking.
 * <p>
 * {@code TilePipeHolder.getOwner()} used to be stubbed to {@code return null} ("Owner tracking
 * not yet ported"), so the Stripes pipe — the only pipe that consumes the owner — attributed its
 * block-breaking and stripe actions to a generic BuildCraft fake player rather than the placer.
 * This test confirms {@code onPlacedBy} records the placing player's {@code GameProfile} and
 * {@code getOwner()} returns it.
 */
public class PipeOwnerTester {

    public static void testPipeRecordsOwnerOnPlacement(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipePos);*/
        //?}

        helper.assertTrue(tile.getOwner() == null, "A pipe tile has no owner before it is placed");

        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        tile.onPlacedBy(placer, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));

        GameProfile owner = tile.getOwner();
        helper.assertTrue(owner != null, "Placing a pipe must record the placing player as its owner");
        helper.assertTrue(owner.equals(placer.getGameProfile()),
                "Recorded pipe owner must match the placing player's GameProfile");
        helper.succeed();
    }
}
