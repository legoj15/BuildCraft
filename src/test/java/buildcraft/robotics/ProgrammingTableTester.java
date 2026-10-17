/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?}

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.recipes.AssemblyRecipe;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.tile.item.IBCItemHandler;

import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconRecipes;
import buildcraft.silicon.block.BlockLaser;
import buildcraft.silicon.tile.TileIntegrationTable;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.silicon.tile.TileProgrammingTable;

/** Ph7 game tests: the Programming Table's craft loop (selection → laser MJ → programmed board out), the
 *  Integration Table's robot programming with charge preservation, a live laser finding and charging a
 *  working table, the four survival crafting recipes, and the robot-naming data path.
 *
 *  <p>The tile tests call {@code serverTick()} and {@code receiveLaserPower()} directly — the whole test
 *  body runs inside a single scheduled runnable on the server thread, so no natural ticker can interleave
 *  between setup and assertion. The one live-tick test (the laser) gates on observed state via
 *  {@link EntityArenaUtil#tickUntil}, never on a fixed delay. */
public class ProgrammingTableTester {

    private static final long GREEN_BOARD_MICRO_MJ = 800_000_000L;
    private static final long ROBOT_INTEGRATION_MICRO_MJ = 5_000_000_000L;

    // ── Programming Table ───────────────────────────────────────────────────

    public static void craftsSelectedBoard(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        TileProgrammingTable table = blockEntity(helper, pos, TileProgrammingTable.class);
        table.invBoard.setStackInSlot(0, ItemRedstoneBoard.createStack(null));
        table.serverTick(); // resolves the recipe and the option grid

        int picker = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (picker < 0) {
            helper.fail("the picker board is not among the offered options");
        }
        table.selectOption(picker);
        if (table.getTarget() != GREEN_BOARD_MICRO_MJ) {
            helper.fail("selected green-tier board should cost 800 MJ, got " + table.getTarget());
        }
        table.receiveLaserPower(GREEN_BOARD_MICRO_MJ);
        table.serverTick();

        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotPickerNBT.ID.equals(boardIdOf(result))) {
            helper.fail("expected a programmed picker board in the result slot, got " + result);
        }
        if (!table.invBoard.getStackInSlot(0).isEmpty()) {
            helper.fail("the input board was not consumed");
        }
        helper.succeed();
    }

    public static void blockedOutputDoesNotCraft(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        TileProgrammingTable table = blockEntity(helper, pos, TileProgrammingTable.class);
        ItemStack blank = ItemRedstoneBoard.createStack(null);
        table.invBoard.setStackInSlot(0, blank.copy());
        table.invResult.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        table.serverTick();

        int picker = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (picker < 0) {
            helper.fail("the picker board is not among the offered options");
        }
        table.selectOption(picker);
        if (table.getTarget() != 0) {
            helper.fail("an occupied result slot must pause the craft (target 0), got " + table.getTarget());
        }
        table.receiveLaserPower(GREEN_BOARD_MICRO_MJ);
        table.serverTick();

        if (!ItemStack.matches(blank, table.invBoard.getStackInSlot(0))) {
            helper.fail("the input board was consumed despite the blocked output");
        }
        if (table.power != 0) {
            helper.fail("the table banked power with no work to do: " + table.power);
        }
        helper.succeed();
    }

    /** The blank-board option costs nothing and sorts top-left; picking it for a programmed board must still run (it
     *  used to wait on a target above zero that a free option can never have), turning the board back to blank. */
    public static void programmingTableResetsBoardToBlank(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        TileProgrammingTable table = blockEntity(helper, pos, TileProgrammingTable.class);
        table.invBoard.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        table.serverTick(); // resolves the recipe and the option grid

        int blank = optionIndexOf(table, BoardRobotEmptyNBT.ID);
        if (blank < 0) {
            helper.fail("the blank board is not among the offered options");
        }
        table.selectOption(blank);
        if (!table.hasWork()) {
            helper.fail("resetting a programmed board to blank is work, even though it is free");
        }
        if (table.getTarget() != 0) {
            helper.fail("the blank option should cost nothing, got " + table.getTarget());
        }
        table.serverTick();

        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotEmptyNBT.ID.equals(boardIdOf(result))) {
            helper.fail("expected a blank board in the result slot, got " + result);
        }
        if (!table.invBoard.getStackInSlot(0).isEmpty()) {
            helper.fail("the programmed input board was not consumed");
        }
        if (table.power != 0) {
            helper.fail("a free craft must not touch the charge, got " + table.power);
        }
        // The finished craft takes the selection with the board (7.1.x re-matched straight after a craft). Were
        // the free blank option still selected, the next programmed board to land in the slot would be wiped on
        // arrival.
        if (table.optionId != -1) {
            helper.fail("the blank option is still selected after its craft emptied the input, optionId = "
                    + table.optionId);
        }
        ItemStack next = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        table.invResult.setStackInSlot(0, ItemStack.EMPTY);
        table.invBoard.setStackInSlot(0, next.copy());
        table.serverTick();
        if (!ItemStack.matches(next, table.invBoard.getStackInSlot(0))
                || !table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("the next programmed board was blanked on arrival");
        }
        helper.succeed();
    }

    /** An option that would turn a board into the same board id is skipped: there is nothing to convert, so the table
     *  neither asks for power nor consumes the board (a blank into blank, and a programmed board into itself). */
    public static void programmingTableSkipsSameBoardOption(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        TileProgrammingTable table = blockEntity(helper, pos, TileProgrammingTable.class);

        // Blank into blank.
        ItemStack blankBoard = ItemRedstoneBoard.createStack(null);
        table.invBoard.setStackInSlot(0, blankBoard.copy());
        table.serverTick();
        int blank = optionIndexOf(table, BoardRobotEmptyNBT.ID);
        if (blank < 0) {
            helper.fail("the blank board is not among the offered options");
        }
        table.selectOption(blank);
        if (table.hasWork()) {
            helper.fail("a blank board into the blank option is not work");
        }
        table.serverTick();
        if (!table.invResult.getStackInSlot(0).isEmpty()
                || !ItemStack.matches(blankBoard, table.invBoard.getStackInSlot(0))) {
            helper.fail("the table ran a blank-into-blank conversion");
        }

        // A programmed board into itself, with the full price banked: still skipped, charge untouched. The board
        // arrives with no selection (the blank option picked above would otherwise run on it, as it is a real change).
        ItemStack picker = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        table.selectOption(-1);
        table.invBoard.setStackInSlot(0, picker.copy());
        table.serverTick();
        int pickerOption = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (pickerOption < 0) {
            helper.fail("the picker board is not among the offered options");
        }
        table.selectOption(pickerOption);
        table.power = GREEN_BOARD_MICRO_MJ;
        if (table.getTarget() != 0) {
            helper.fail("a picker into the picker option must cost nothing, got " + table.getTarget());
        }
        table.serverTick();
        if (!table.invResult.getStackInSlot(0).isEmpty()
                || !ItemStack.matches(picker, table.invBoard.getStackInSlot(0))) {
            helper.fail("the table ran a picker-into-picker conversion");
        }
        if (table.power != GREEN_BOARD_MICRO_MJ) {
            helper.fail("a skipped conversion spent charge, got " + table.power);
        }

        // Control: a different option on the same board still runs.
        table.selectOption(blank);
        table.serverTick();
        if (!BoardRobotEmptyNBT.ID.equals(boardIdOf(table.invResult.getStackInSlot(0)))) {
            helper.fail("a conversion to a different board must still run, got "
                    + table.invResult.getStackInSlot(0));
        }
        helper.succeed();
    }

    public static void laserTargetsProgrammingTable(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos laserPos = new BlockPos(0, 1, 2);
        BlockPos tablePos = new BlockPos(2, 1, 2);
        helper.setBlock(laserPos, BCSiliconBlocks.LASER.get().defaultBlockState()
                .setValue(BlockLaser.FACING, Direction.EAST));
        helper.setBlock(tablePos, BCSiliconBlocks.PROGRAMMING_TABLE.get());
        TileLaser laser = blockEntity(helper, laserPos, TileLaser.class);
        TileProgrammingTable table = blockEntity(helper, tablePos, TileProgrammingTable.class);

        table.invBoard.setStackInSlot(0, ItemRedstoneBoard.createStack(null));
        table.serverTick();
        int picker = optionIndexOf(table, BoardRobotPickerNBT.ID);
        if (picker < 0) {
            helper.fail("the picker board is not among the offered options");
        }
        table.selectOption(picker);
        if (table.getTarget() <= 0) {
            helper.fail("table should want power once a board is selected");
        }

        // The poll supplier doubles as the laser's power feed — one battery top-up per poll tick keeps
        // the ramp saturated, so delivery is limited only by the scan/target intervals. Delivery alone
        // is the assertion here: the craft itself is pinned by craftsSelectedBoard without the scan
        // latency, and a full craft through the live laser would need ~200 ticks at 4 MJ/t.
        EntityArenaUtil.tickUntil(helper, 300,
                () -> {
                    laser.getMjReceiver().receivePower(64 * MjAPI.MJ, false);
                    return table.power > 0;
                },
                helper::succeed,
                "the laser never delivered power to the working Programming Table");
    }

    // ── Integration Table ───────────────────────────────────────────────────

    public static void integrationTableProgramsRobot(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        table.invTarget.setStackInSlot(0, ItemRobot.createRobotStack(null, 123_456L));
        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        table.serverTick(); // updateRecipe discovers the robot integration recipe

        if (table.getTarget() != ROBOT_INTEGRATION_MICRO_MJ) {
            helper.fail("robot integration should cost 5000 MJ, got " + table.getTarget());
        }
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();

        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotPickerNBT.ID.equals(ItemRobot.getBoardId(result))) {
            helper.fail("expected a picker-programmed robot in the result slot, got " + result);
        }
        if (ItemRobot.getEnergy(result) != 123_456L) {
            helper.fail("the robot's charge must ride into the result, got " + ItemRobot.getEnergy(result));
        }
        if (!table.invTarget.getStackInSlot(0).isEmpty() || !table.invToIntegrate.getStackInSlot(0).isEmpty()) {
            helper.fail("the robot and the board were not both consumed");
        }
        if (table.power != 0) {
            helper.fail("a craft must spend the laser charge it ran on, but "
                    + (table.power / MjAPI.MJ) + " MJ is still banked");
        }
        helper.succeed();
    }

    /** One charge programs one robot: the craft deducts exactly its cost (a stale over-filled buffer keeps only the
     *  surplus), so a second robot has to wait for its own 5000 MJ instead of riding the first one's. */
    public static void integrationTableSpendsItsCostPerCraft(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        long surplus = 7_000_000L;
        table.invTarget.setStackInSlot(0, ItemRobot.createRobotStack(null, 1_000L));
        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        table.serverTick();
        table.power = ROBOT_INTEGRATION_MICRO_MJ + surplus;
        table.serverTick();

        if (table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("the first robot was not programmed");
        }
        if (table.power != surplus) {
            helper.fail("a craft must spend exactly its cost: expected " + surplus + " micro-MJ left, got "
                    + table.power);
        }

        // A second robot and board, the output slot cleared: the leftover surplus is nowhere near 5000 MJ.
        table.invResult.setStackInSlot(0, ItemStack.EMPTY);
        table.invTarget.setStackInSlot(0, ItemRobot.createRobotStack(null, 2_000L));
        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE));
        table.serverTick();
        if (!table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("a second robot was programmed on the first one's charge");
        }
        if (table.invTarget.getStackInSlot(0).isEmpty() || table.invToIntegrate.getStackInSlot(0).isEmpty()) {
            helper.fail("the second robot or its board was consumed without a charge");
        }

        // Deliver only the shortfall: now it crafts, and the bank is empty again.
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();
        if (table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("the second robot was not programmed once its own charge arrived");
        }
        if (table.power != 0) {
            helper.fail("the second craft left " + table.power + " micro-MJ banked");
        }
        helper.succeed();
    }

    /** A robot in the centre, a board in the ring and ANY other stack in the ring: the recipe reads as valid (so the
     *  table asks for and banks the 5000 MJ) but the precise ring requirement can never be met. Nothing may be
     *  consumed while it can't; clearing the stray stack must let the very same charge go through. */
    public static void integrationTableExtraRingStackKeepsTheBoard(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        ItemStack robot = ItemRobot.createRobotStack(null, 123_456L);
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);
        table.invTarget.setStackInSlot(0, robot.copy());
        table.invToIntegrate.setStackInSlot(0, board.copy());
        table.invToIntegrate.setStackInSlot(3, new ItemStack(Items.PAPER));
        table.serverTick();
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();
        table.serverTick();

        if (!ItemStack.matches(board, table.invToIntegrate.getStackInSlot(0))) {
            helper.fail("the board was consumed although the craft could not complete, got "
                    + table.invToIntegrate.getStackInSlot(0));
        }
        if (!ItemStack.matches(robot, table.invTarget.getStackInSlot(0))) {
            helper.fail("the centre robot was touched by a craft that could not complete");
        }
        if (!table.invToIntegrate.getStackInSlot(3).is(Items.PAPER)) {
            helper.fail("the stray stack was altered");
        }
        if (!table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("a robot came out of a ring the recipe rejects: " + table.invResult.getStackInSlot(0));
        }
        if (table.power != ROBOT_INTEGRATION_MICRO_MJ) {
            helper.fail("no craft ran, so the charge must be untouched, got " + table.power);
        }

        // Not wedged: take the stray stack away and the same charge programs the robot.
        table.invToIntegrate.setStackInSlot(3, ItemStack.EMPTY);
        table.serverTick();
        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty() || !BoardRobotPickerNBT.ID.equals(ItemRobot.getBoardId(result))) {
            helper.fail("the table stayed wedged after the stray stack was removed, got " + result);
        }
        if (table.power != 0) {
            helper.fail("the craft left " + table.power + " micro-MJ banked");
        }
        helper.succeed();
    }

    /** Pipe access mirrors the machine (7.1.x {@code canInsertItem}/{@code canExtractItem}, and the Programming
     *  Table): the robot and the board go IN, only the programmed robot comes OUT. */
    public static void integrationTablePipeAccessInputsInResultOut(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        // The side handler concatenates the three inventories: 0 = centre, 1..8 = ring, 9 = result.
        IBCItemHandler pipe = table.getItemHandler(Direction.UP);
        if (pipe == null) {
            helper.fail("the Integration Table exposes no item handler to pipes");
        }
        ItemStack robot = ItemRobot.createRobotStack(null, 500L);
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);

        if (pipeInsert(pipe, 0, robot) != 1) {
            helper.fail("a pipe cannot insert the robot into the centre slot");
        }
        if (pipeInsert(pipe, 1, board) != 1) {
            helper.fail("a pipe cannot insert a board into the ring");
        }
        if (pipeInsert(pipe, 9, new ItemStack(Items.PAPER)) != 0
                || !table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can push items into the output slot");
        }
        if (pipeExtract(pipe, 0, 1) != 0 || table.invTarget.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can pull the robot back out of the centre slot");
        }
        if (pipeExtract(pipe, 1, 1) != 0 || table.invToIntegrate.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can pull the board back out of the ring");
        }

        ItemStack programmed = ItemRobot.createRobotStack(BoardRobotPickerNBT.ID, 500L);
        table.invResult.setStackInSlot(0, programmed.copy());
        if (pipeExtract(pipe, 9, 1) != 1 || !table.invResult.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe cannot take the finished robot out of the output slot");
        }
        helper.succeed();
    }

    /** A pipe (or a hand) may only feed the table what it can craft from, as 7.1.x's {@code isItemValidForSlot}:
     *  the centre takes a robot, and the ring takes a board only once a robot is in and only while the ring would
     *  still hold exactly what the recipe consumes. A second board used to be accepted, and since the precise
     *  ring check then failed forever and a pipe cannot pull an input back out, the table jammed. */
    public static void integrationTablePipeRingTakesOnlyWhatCrafts(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        IBCItemHandler pipe = table.getItemHandler(Direction.UP);
        if (pipe == null) {
            helper.fail("the Integration Table exposes no item handler to pipes");
        }
        ItemStack board = ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE);

        if (pipeInsert(pipe, 0, new ItemStack(Items.PAPER)) != 0 || !table.invTarget.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can push something that is not a robot into the centre slot");
        }
        if (pipeInsert(pipe, 1, board.copy()) != 0 || !table.invToIntegrate.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can push a board into the ring before there is a robot to program");
        }
        if (pipeInsert(pipe, 0, ItemRobot.createRobotStack(null, 500L)) != 1) {
            helper.fail("a pipe cannot insert the robot into the centre slot");
        }
        if (pipeInsert(pipe, 1, new ItemStack(Items.PAPER)) != 0 || !table.invToIntegrate.getStackInSlot(0).isEmpty()) {
            helper.fail("a pipe can push something that is not a board into the ring");
        }
        if (pipeInsert(pipe, 1, board.copy()) != 1) {
            helper.fail("a pipe cannot insert the board into the ring once the robot is in");
        }
        if (pipeInsert(pipe, 2, ItemRedstoneBoard.createStack(BoardRobotPickerNBT.INSTANCE)) != 0
                || !table.invToIntegrate.getStackInSlot(1).isEmpty()) {
            helper.fail("a pipe can push a second board into the ring, which jams the table");
        }

        table.serverTick();
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();
        ItemStack result = table.invResult.getStackInSlot(0);
        if (!BoardRobotPickerNBT.ID.equals(ItemRobot.getBoardId(result))) {
            helper.fail("the pipe-fed table should still program the robot, got " + result);
        }
        helper.succeed();
    }

    public static void integrationTableTopsUpDrainedRobot(GameTestHelper helper) {
        BCRoboticsRecipes.ensureInitialized();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, BCSiliconBlocks.INTEGRATION_TABLE.get());
        TileIntegrationTable table = blockEntity(helper, pos, TileIntegrationTable.class);
        table.invTarget.setStackInSlot(0, ItemRobot.createRobotStack(null, 0L));
        table.invToIntegrate.setStackInSlot(0, ItemRedstoneBoard.createStack(null));
        table.serverTick();
        table.receiveLaserPower(ROBOT_INTEGRATION_MICRO_MJ);
        table.serverTick();

        ItemStack result = table.invResult.getStackInSlot(0);
        if (result.isEmpty()) {
            helper.fail("expected a blank robot in the result slot");
        }
        if (!ItemRobot.hasEmptyBoard(result)) {
            helper.fail("integrating a blank board should yield a blank robot");
        }
        if (ItemRobot.getEnergy(result) != IRobotAccess.SAFETY_POWER) {
            helper.fail("a zero-charge robot must be topped up to SAFETY_POWER, got " + ItemRobot.getEnergy(result));
        }
        helper.succeed();
    }

    // ── Survival crafting recipes ───────────────────────────────────────────

    public static void robotSurvivalRecipesResolve(GameTestHelper helper) {
        ItemStack paper = new ItemStack(Items.PAPER);
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN);
        ItemStack chipsetRedstone = new ItemStack(BCSiliconItems.CHIPSET_REDSTONE.get());
        ItemStack chipsetDiamond = new ItemStack(BCSiliconItems.CHIPSET_DIAMOND.get());
        ItemStack redstoneCrystal = new ItemStack(BCSiliconItems.REDSTONE_CRYSTAL.get());
        ItemStack gearDiamond = new ItemStack(BCCoreItems.GEAR_DIAMOND.get());
        ItemStack gearGold = new ItemStack(BCCoreItems.GEAR_GOLD.get());

        // Blank board: PPP / PRP / PPP (8 paper + 1 redstone). The bare result stack is the blank
        // template — it inherits the item's stacksTo(16); only programmed stacks carry MAX_STACK_SIZE 1.
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                paper, paper, paper,
                paper, redstone, paper,
                paper, paper, paper)),
                new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get()), "blank redstone board");

        // Robot: PPP / PRP / C C (5 iron + 1 Redstone Crystal + 2 diamond chipsets)
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                iron, iron, iron,
                iron, redstoneCrystal, iron,
                chipsetDiamond, ItemStack.EMPTY, chipsetDiamond)),
                new ItemStack(BCRoboticsItems.ROBOT.get()), "robot");

        // Plain redstone in the robot's centre must NOT craft — the crystal is the price gate.
        CraftingInput dustRobot = CraftingInput.of(3, 3, List.of(
                iron, iron, iron,
                iron, redstone, iron,
                chipsetDiamond, ItemStack.EMPTY, chipsetDiamond));
        if (helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, dustRobot, helper.getLevel()).isPresent()) {
            helper.fail("robot crafted with redstone dust instead of a Redstone Crystal");
        }

        // Redstone Crystal: Assembly Table, 1 redstone block at exactly 256 000 MJ (7.1.x's
        // 1 000 000 MJ scaled down; see docs/robotics-resurrection.md).
        BCSiliconRecipes.ensureInitialized();
        AssemblyRecipe crystal = AssemblyRecipeRegistry.REGISTRY.get("redstone_crystal");
        if (crystal == null) {
            helper.fail("missing assembly recipe: redstone_crystal");
        }
        Set<ItemStack> crystalOut = crystal.getOutputs(
                NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.REDSTONE_BLOCK)));
        if (crystalOut.size() != 1
                || !ItemStack.matches(redstoneCrystal, crystalOut.iterator().next())) {
            helper.fail("redstone block should assemble into exactly one Redstone Crystal, got " + crystalOut);
        }
        if (!crystal.getOutputs(NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.REDSTONE, 9))).isEmpty()) {
            helper.fail("Redstone Crystal must need the block, not loose dust");
        }
        long cost = crystal.getRequiredMicroJoulesFor(redstoneCrystal);
        if (cost != 256_000 * MjAPI.MJ) {
            helper.fail("Redstone Crystal should cost 256 000 MJ, got " + (cost / MjAPI.MJ) + " MJ");
        }

        // Programming Table: OCO / ORO / OGO
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                obsidian, new ItemStack(Items.EMERALD), obsidian,
                obsidian, chipsetRedstone, obsidian,
                obsidian, gearDiamond, obsidian)),
                new ItemStack(BCSiliconBlocks.PROGRAMMING_TABLE.get()), "programming table");

        // Integration Table: OWO / ORO / OGO
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                obsidian, new ItemStack(Items.CRAFTING_TABLE), obsidian,
                obsidian, chipsetRedstone, obsidian,
                obsidian, gearGold, obsidian)),
                new ItemStack(BCSiliconBlocks.INTEGRATION_TABLE.get()), "integration table");

        helper.succeed();
    }

    private static void assertCraft(GameTestHelper helper, CraftingInput input, ItemStack expected, String label) {
        ServerLevel level = helper.getLevel();
        Optional<RecipeHolder<CraftingRecipe>> match = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isEmpty()) {
            helper.fail("Recipe didn't match: " + label);
        }
        //? if >=26.1 {
        ItemStack out = match.get().value().assemble(input);
        //?} else {
        /*ItemStack out = match.get().value().assemble(input, level.registryAccess());*/
        //?}
        if (!ItemStack.matches(expected, out)) {
            helper.fail("Crafting output mismatch for " + label + ": expected " + expected + " got " + out);
        }
    }

    // ── Robot naming (7.2.x parity) ─────────────────────────────────────────

    public static void robotNameTagDataRoundTrip(GameTestHelper helper) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        robot.setCustomName(Component.literal("Robbie"));
        robot.setCustomNameVisible(true);
        if (!robot.hasCustomName() || !"Robbie".equals(robot.getCustomName().getString())) {
            helper.fail("EntityRobot suppressed the vanilla custom-name data path");
        }
        if (!robot.isCustomNameVisible()) {
            helper.fail("EntityRobot suppressed the vanilla custom-name-visibility flag");
        }
        // The save round-trip pins that nothing in EntityRobot's own save format drops the vanilla
        // fields a name-tagged robot relies on. Same TagValue fork (and reason) as
        // SchematicEntityDefault.
        //? if >=1.21.10 {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        robot.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        EntityRobot reloaded = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        //?} else {
        /*CompoundTag tag = new CompoundTag();
        robot.saveWithoutId(tag);
        EntityRobot reloaded = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        reloaded.load(tag);*/
        //?}
        if (!reloaded.hasCustomName() || !"Robbie".equals(reloaded.getCustomName().getString())) {
            helper.fail("the custom name did not survive the save round-trip");
        }
        if (!reloaded.isCustomNameVisible()) {
            helper.fail("the custom-name-visibility flag did not survive the save round-trip");
        }
        helper.succeed();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Node-neutral typed block-entity lookup: the 2-arg overload exists only on 1.21.10+, where it also
     *  fails the test for a wrong type; 1.21.1's single-arg form returns a raw BlockEntity to cast. */
    private static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, type);
        //?} else {
        /*return type.cast(helper.getBlockEntity(pos));*/
        //?}
    }

    /** How many items of {@code stack} a pipe-side handler takes into {@code slot} (node-neutral: Transfer API on
     *  1.21.10+, classic handler on 1.21.1). */
    private static int pipeInsert(IBCItemHandler handler, int slot, ItemStack stack) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
        //?} else {
        /*return stack.getCount() - handler.insertItem(slot, stack.copy(), false).getCount();*/
        //?}
    }

    /** How many items a pipe-side handler yields from {@code slot}, up to {@code amount}. */
    private static int pipeExtract(IBCItemHandler handler, int slot, int amount) {
        //? if >=1.21.10 {
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(slot, resource, amount, tx);
            tx.commit();
            return extracted;
        }
        //?} else {
        /*return handler.extractItem(slot, amount, false).getCount();*/
        //?}
    }

    private static String boardIdOf(ItemStack board) {
        RedstoneBoardNBT<?> nbt = ItemRedstoneBoard.getBoardNBT(board);
        return nbt == null ? null : nbt.getID();
    }

    private static int optionIndexOf(TileProgrammingTable table, String boardId) {
        if (table.options == null) {
            return -1;
        }
        for (int i = 0; i < table.options.size(); i++) {
            if (boardId.equals(boardIdOf(table.options.get(i)))) {
                return i;
            }
        }
        return -1;
    }
}
