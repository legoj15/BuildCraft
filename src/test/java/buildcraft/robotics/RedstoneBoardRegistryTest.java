/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.IRedstoneBoard;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.EntityRobotBase;

/**
 * Pure-JUnit characterization of {@link ImplRedstoneBoardRegistry}: id-keyed lookup, MJ power cost, the
 * createBoard NBT id round-trip, and the unknown-id fallback to the empty robot board.
 */
public class RedstoneBoardRegistryTest {

    private static final long COST = 32_000L * MjAPI.MJ;

    @Test
    public void testRegisterLookupAndPowerCost() {
        ImplRedstoneBoardRegistry registry = new ImplRedstoneBoardRegistry();
        TestBoardNBT board = new TestBoardNBT("buildcraftunofficial:test_board");
        registry.registerBoardType(board, COST);

        Assertions.assertSame(board, registry.getRedstoneBoard("buildcraftunofficial:test_board"),
                "a registered board is found by id");
        Assertions.assertEquals(COST, registry.getPowerCost(board), "the registered MJ cost is returned");
        Assertions.assertTrue(registry.getAllBoardNBTs().contains(board), "the board is enumerated");
    }

    @Test
    public void testGetRedstoneBoardFromNbtRoundTrip() {
        ImplRedstoneBoardRegistry registry = new ImplRedstoneBoardRegistry();
        TestBoardNBT board = new TestBoardNBT("buildcraftunofficial:test_board");
        registry.registerBoardType(board, COST);

        CompoundTag nbt = new CompoundTag();
        board.createBoard(nbt); // writes {"id": getID()}

        Assertions.assertSame(board, registry.getRedstoneBoard(nbt),
                "createBoard's id tag resolves back to the same board");
    }

    @Test
    public void testUnknownIdFallsBackToEmptyBoard() {
        ImplRedstoneBoardRegistry registry = new ImplRedstoneBoardRegistry();
        TestEmptyBoardNBT empty = new TestEmptyBoardNBT();
        registry.setEmptyRobotBoard(empty);

        Assertions.assertSame(empty, registry.getEmptyRobotBoard(), "the empty board is stored and returned");
        Assertions.assertSame(empty, registry.getRedstoneBoard("buildcraftunofficial:does_not_exist"),
                "an unknown id falls back to the empty robot board");
    }

    /** Trivial concrete {@link RedstoneBoardNBT} — only its id is exercised. */
    private static class TestBoardNBT extends RedstoneBoardNBT<Object> {
        private final String id;

        TestBoardNBT(String id) {
            this.id = id;
        }

        @Override
        public String getID() {
            return id;
        }

        @Override
        public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        }

        @Override
        public String getDisplayName() {
            return id;
        }

        @Override
        public IRedstoneBoard<Object> create(CompoundTag nbt, Object object) {
            return null;
        }

        @Override
        public String getItemModelLocation() {
            return id;
        }
    }

    /** Trivial concrete empty-robot board used purely as the unknown-id fallback target. */
    private static class TestEmptyBoardNBT extends RedstoneBoardRobotNBT {
        @Override
        public String getID() {
            return "buildcraftunofficial:empty_robot_board";
        }

        @Override
        public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        }

        @Override
        public String getDisplayName() {
            return "Empty";
        }

        @Override
        public String getItemModelLocation() {
            return getID();
        }

        @Override
        public RedstoneBoardRobot create(EntityRobotBase robot) {
            return null;
        }

        @Override
        public Object getRobotTexture() {
            return null;
        }
    }
}
