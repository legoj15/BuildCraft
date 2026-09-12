/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the contents
 * of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt */
package buildcraft.robotics;

import com.google.common.collect.ImmutableList;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.recipes.IngredientStack;
import buildcraft.api.recipes.IntegrationRecipe;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;

/** The Integration Table recipe that programs a robot: robot (centre) + one redstone board (expansion) → the same
 *  robot carrying that board, at a flat laser-energy cost. Ported from 7.1.x {@code buildcraft.robotics.RobotIntegrationRecipe}
 *  (which extended the now-gone {@code IntegrationRecipeBC}) onto the surviving {@link IntegrationRecipe} contract.
 *  The robot's stored charge is carried into the result; a zero-charge robot is topped up to
 *  {@link IRobotAccess#SAFETY_POWER} so a fresh craft never spawns a robot that dies where it stands. 7.1.x's
 *  {@code generateExample*} triples fed the old JEI view — dropped until robotics grows a JEI category. */
public class RobotIntegrationRecipe extends IntegrationRecipe {

    /** 7.1.x charged a flat 50,000 RF, converted at the canonical 1 RF = 100,000 micro-MJ bridge. */
    public static final long REQUIRED_MICROJOULES = 5_000_000_000L;

    public RobotIntegrationRecipe() {
        super(new IngredientStack(Ingredient.of(BCRoboticsItems.ROBOT.get())));
    }

    @Override
    public ItemStack getOutput(ItemStack target, NonNullList<ItemStack> toIntegrate) {
        if (!(target.getItem() instanceof ItemRobot)) {
            return ItemStack.EMPTY;
        }
        ItemStack boardStack = ItemStack.EMPTY;
        for (ItemStack stack : toIntegrate) {
            if (stack.getItem() instanceof ItemRedstoneBoard) {
                boardStack = stack;
                break;
            }
        }
        if (boardStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        RedstoneBoardNBT<?> boardNBT = ItemRedstoneBoard.getBoardNBT(boardStack);
        if (!(boardNBT instanceof RedstoneBoardRobotNBT robotBoard)) {
            return ItemStack.EMPTY;
        }
        long energy = ItemRobot.getEnergy(target);
        if (energy == 0) {
            energy = IRobotAccess.SAFETY_POWER;
        }
        return ItemRobot.createRobotStack(robotBoard.getID(), energy);
    }

    @Override
    public ImmutableList<IngredientStack> getRequirements(ItemStack output) {
        // Item-level matching is sufficient: the table only crafts when the expansion grid holds exactly one
        // board (precise extraction), and the programmed board is the one getOutput just read from that grid.
        return ImmutableList.of(new IngredientStack(Ingredient.of(BCRoboticsItems.REDSTONE_BOARD.get())));
    }

    @Override
    public long getRequiredMicroJoules(ItemStack output) {
        return REQUIRED_MICROJOULES;
    }

    @Override
    public IngredientStack getCenterStack() {
        return new IngredientStack(Ingredient.of(BCRoboticsItems.ROBOT.get()));
    }
}
