/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.compat.viewer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.recipes.IntegrationRecipe;
import buildcraft.lib.recipe.IntegrationRecipeRegistry;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** Builds the Integration Table's viewer entries: blank robot + one programmed board → that robot, one per robot
 *  program. Viewer-free, so the mapping is unit-tested ({@code IntegrationRecipeCollectorTester}), and shared with the
 *  guide book's Integration Table lookup, which is why it lives here and not in a viewer's own package.
 *
 *  <p>{@code IntegrationRecipe} only answers "what do these inputs make" — it cannot list its inputs (7.1.x's
 *  {@code generateExample*} methods are gone), so the robot programs are probed from the board registry. Every
 *  output, cost and the very recipe that matches come from the table's own recipe lookup (not its full craft gate,
 *  which only adds slot-content checks), so a board the table's recipes refuse gets no entry. The blank board is skipped on
 *  purpose: the table does accept it, but blank robot + blank board is the same blank robot, so the entry would
 *  only show a no-op.
 *
 *  <p>The table tops a drained robot up to safety charge, so the crafted robot is the charged variant. The drained
 *  one is a separate viewer entry ({@link RoboticsSubtypes#robotKey}) that sits in the creative tab too, so it rides
 *  along as a second output — otherwise "recipe" on it still found nothing. */
public final class IntegrationRecipeCollector {
    /** The ring around the centre slot. */
    private static final int RING_SIZE = 8;

    private IntegrationRecipeCollector() {}

    public static List<IntegrationRecipeEntry> collect() {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return List.of();
        }
        List<RedstoneBoardNBT<?>> boards = new ArrayList<>(registry.getAllBoardNBTs());
        boards.sort(Comparator.comparing(RedstoneBoardNBT::getID));

        ItemStack blankRobot = new ItemStack(BCRoboticsItems.ROBOT.get());
        List<IntegrationRecipeEntry> out = new ArrayList<>();
        for (RedstoneBoardNBT<?> board : boards) {
            if (!(board instanceof RedstoneBoardRobotNBT) || BoardRobotEmptyNBT.ID.equals(board.getID())) {
                continue;
            }
            ItemStack boardStack = ItemRedstoneBoard.createStack(board);
            NonNullList<ItemStack> ring = NonNullList.withSize(RING_SIZE, ItemStack.EMPTY);
            ring.set(0, boardStack.copy());
            IntegrationRecipe recipe = IntegrationRecipeRegistry.INSTANCE.getRecipeFor(blankRobot.copy(), ring);
            if (recipe == null) {
                continue;
            }
            ItemStack crafted = recipe.getOutput(blankRobot.copy(), ring);
            if (crafted.isEmpty()) {
                continue;
            }
            List<ItemStack> outputs = new ArrayList<>();
            outputs.add(crafted);
            String programId = ItemRobot.getBoardId(crafted);
            if (programId != null && ItemRobot.getEnergy(crafted) > 0) {
                outputs.add(ItemRobot.createRobotStack(programId, 0));
            }
            out.add(new IntegrationRecipeEntry("robot/" + board.getID(), blankRobot.copy(), List.of(boardStack),
                    List.copyOf(outputs), recipe.getRequiredMicroJoules(crafted)));
        }
        return out;
    }
}
