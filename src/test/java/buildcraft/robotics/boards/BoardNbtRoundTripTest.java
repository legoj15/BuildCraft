/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.item.ItemRedstoneBoard;

/** {@link ItemRedstoneBoard} blob round-trip: a stack created for a board carries that board's id, survives a
 *  write→read cycle, and resolves back to the same {@link RedstoneBoardRobotNBT}. Also pins that a bare stack
 *  reads as the empty board. Runs against the live registry (booted by the FML-JUnit mod load), so the
 *  picker/carrier boards are registered. */
public class BoardNbtRoundTripTest extends VanillaSetupBaseTester {

    @Test
    public void pickerStackResolvesBackToThePicker() {
        RedstoneBoardNBT<?> resolved = roundTrip(BoardRobotPickerNBT.INSTANCE);

        Assertions.assertSame(BoardRobotPickerNBT.INSTANCE, resolved,
                "a picker stack survives a write→read cycle and resolves to the picker board");
    }

    @Test
    public void carrierStackResolvesBackToTheCarrier() {
        RedstoneBoardNBT<?> resolved = roundTrip(BoardRobotCarrierNBT.INSTANCE);

        Assertions.assertSame(BoardRobotCarrierNBT.INSTANCE, resolved,
                "a carrier stack survives a write→read cycle and resolves to the carrier board");
    }

    // Ph5: the eight work boards, registered in BCRobotics.

    @Test
    public void lumberjackStackResolvesBackToTheLumberjack() {
        Assertions.assertSame(BoardRobotLumberjackNBT.INSTANCE, roundTrip(BoardRobotLumberjackNBT.INSTANCE),
                "a lumberjack stack survives a write→read cycle and resolves to the lumberjack board");
    }

    @Test
    public void minerStackResolvesBackToTheMiner() {
        Assertions.assertSame(BoardRobotMinerNBT.INSTANCE, roundTrip(BoardRobotMinerNBT.INSTANCE),
                "a miner stack survives a write→read cycle and resolves to the miner board");
    }

    @Test
    public void harvesterStackResolvesBackToTheHarvester() {
        Assertions.assertSame(BoardRobotHarvesterNBT.INSTANCE, roundTrip(BoardRobotHarvesterNBT.INSTANCE),
                "a harvester stack survives a write→read cycle and resolves to the harvester board");
    }

    @Test
    public void planterStackResolvesBackToThePlanter() {
        Assertions.assertSame(BoardRobotPlanterNBT.INSTANCE, roundTrip(BoardRobotPlanterNBT.INSTANCE),
                "a planter stack survives a write→read cycle and resolves to the planter board");
    }

    @Test
    public void farmerStackResolvesBackToTheFarmer() {
        Assertions.assertSame(BoardRobotFarmerNBT.INSTANCE, roundTrip(BoardRobotFarmerNBT.INSTANCE),
                "a farmer stack survives a write→read cycle and resolves to the farmer board");
    }

    @Test
    public void pumpStackResolvesBackToThePump() {
        Assertions.assertSame(BoardRobotPumpNBT.INSTANCE, roundTrip(BoardRobotPumpNBT.INSTANCE),
                "a pump stack survives a write→read cycle and resolves to the pump board");
    }

    @Test
    public void knightStackResolvesBackToTheKnight() {
        Assertions.assertSame(BoardRobotKnightNBT.INSTANCE, roundTrip(BoardRobotKnightNBT.INSTANCE),
                "a knight stack survives a write→read cycle and resolves to the knight board");
    }

    @Test
    public void butcherStackResolvesBackToTheButcher() {
        Assertions.assertSame(BoardRobotButcherNBT.INSTANCE, roundTrip(BoardRobotButcherNBT.INSTANCE),
                "a butcher stack survives a write→read cycle and resolves to the butcher board");
    }

    @Test
    public void bareStackReadsAsTheEmptyBoard() {
        ItemStack bare = new ItemStack(buildcraft.robotics.BCRoboticsItems.REDSTONE_BOARD.get());
        RedstoneBoardNBT<?> board = ItemRedstoneBoard.getBoardNBT(bare);

        Assertions.assertSame(BoardRobotEmptyNBT.INSTANCE, board,
                "a board item with no blob is the empty board, exactly as upstream guaranteed");
    }

    @Test
    public void everyRegisteredBoardHasAStableId() {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        Assertions.assertNotNull(registry, "the FML-JUnit boot must have wired the board registry");

        for (RedstoneBoardNBT<?> boardNBT : registry.getAllBoardNBTs()) {
            ItemStack stack = ItemRedstoneBoard.createStack(boardNBT);
            Assertions.assertSame(boardNBT, ItemRedstoneBoard.getBoardNBT(stack),
                    "board id " + boardNBT.getID() + " round-trips through its item stack");
        }
    }

    @Test
    public void createBoardWritesTheIdTag() {
        CompoundTag nbt = new CompoundTag();
        BoardRobotPickerNBT.INSTANCE.createBoard(nbt);

        Assertions.assertEquals(BoardRobotPickerNBT.INSTANCE.getID(),
                buildcraft.api.core.NbtApiUtil.getString(nbt, "id", ""),
                "createBoard stamps the board id under 'id'");
    }

    /** Creates a stack for {@code board}, copies its CUSTOM_DATA blob through a fresh tag (a serialisation
     *  round-trip), and resolves the board on a fresh stack carrying the copied blob. */
    private static RedstoneBoardNBT<?> roundTrip(RedstoneBoardRobotNBT board) {
        ItemStack stack = ItemRedstoneBoard.createStack(board);
        CompoundTag blob = stack.get(DataComponents.CUSTOM_DATA).copyTag().copy();
        ItemStack copy = new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get());
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(blob));
        return ItemRedstoneBoard.getBoardNBT(copy);
    }
}
