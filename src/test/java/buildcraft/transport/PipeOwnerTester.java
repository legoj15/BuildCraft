/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21.10 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
//?}

import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.tile.AbstractBCBlockEntity;
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

    /**
     * {@code TilePipeHolder} used to keep a private {@code owner} that shadowed {@code AbstractBCBlockEntity.owner},
     * so the inherited {@code setOwner} changed one field and {@code getOwner()} read the other. Both entry points
     * must agree, and the saved owner must still round-trip through the tile's NBT.
     */
    public static void testSetOwnerAndGetOwnerAgreeAndPersist(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 2, 1);
        helper.setBlock(pipePos, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder tile = helper.getBlockEntity(pipePos, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder tile = helper.getBlockEntity(pipePos);*/
        //?}
        GameProfile owner = new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "pipe_owner");

        // Through the base-class reference, the way generic machine code reaches an owner.
        AbstractBCBlockEntity asBase = tile;
        asBase.setOwner(owner);
        helper.assertTrue(owner.equals(tile.getOwner()), "setOwner must be what the pipe's getOwner() returns");
        helper.assertTrue(owner.equals(asBase.getOwner()), "the base-class view of the owner must agree");

        ServerLevel level = helper.getLevel();
        CompoundTag saved = tile.saveCustomOnly(level.registryAccess());
        helper.assertTrue(saved.toString().contains("ownerUUID"), "the owner reaches the saved NBT: " + saved);

        tile.setOwner(null);
        helper.assertTrue(tile.getOwner() == null, "clearing the owner through setOwner clears getOwner()");

        //? if >=1.21.10 {
        tile.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        //?} else {
        /*tile.loadCustomOnly(saved, level.registryAccess());*/
        //?}
        GameProfile loaded = tile.getOwner();
        helper.assertTrue(loaded != null, "the owner survives the NBT round-trip");
        helper.assertTrue(GameProfileUtil.getId(owner).equals(GameProfileUtil.getId(loaded)),
                "the loaded owner keeps its id");
        helper.assertTrue("pipe_owner".equals(GameProfileUtil.getName(loaded)), "the loaded owner keeps its name");
        helper.assertTrue(loaded.equals(asBase.getOwner()), "the base-class view agrees after loading too");
        helper.succeed();
    }
}
