/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
//?}

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.test.EntityArenaUtil;

import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;
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
        ItemStack gearDiamond = new ItemStack(BCCoreItems.GEAR_DIAMOND.get());
        ItemStack gearGold = new ItemStack(BCCoreItems.GEAR_GOLD.get());

        // Blank board: PPP / PRP / PPP (8 paper + 1 redstone). The bare result stack is the blank
        // template — it inherits the item's stacksTo(16); only programmed stacks carry MAX_STACK_SIZE 1.
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                paper, paper, paper,
                paper, redstone, paper,
                paper, paper, paper)),
                new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get()), "blank redstone board");

        // Robot: PPP / PRP / C C (5 iron + 1 redstone + 2 diamond chipsets)
        assertCraft(helper, CraftingInput.of(3, 3, List.of(
                iron, iron, iron,
                iron, redstone, iron,
                chipsetDiamond, ItemStack.EMPTY, chipsetDiamond)),
                new ItemStack(BCRoboticsItems.ROBOT.get()), "robot");

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
