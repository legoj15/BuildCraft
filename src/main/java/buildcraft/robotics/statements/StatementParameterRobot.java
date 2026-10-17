package buildcraft.robotics.statements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.items.IList;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.api.statements.StatementParameterItemStack;
import buildcraft.lib.misc.StackUtil;
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.robotics.item.ItemRobot;

/** A statement parameter naming robots: a robot stack (by installed board), a List (by what the list holds - the
 *  robot's board stack or anything it wears) or a wearable (by what the robot wears). Matching a robot against it
 *  is {@link #matches}; the click rules for putting a stack in are {@link #onClick}. */
public class StatementParameterRobot extends StatementParameterItemStack {

    public StatementParameterRobot() {
        super();
    }

    public StatementParameterRobot(ItemStack stack) {
        super(stack);
    }

    public StatementParameterRobot(CompoundTag nbt) {
        super(nbt);
    }

    public static StatementParameterRobot readFromNbt(CompoundTag nbt) {
        return new StatementParameterRobot(nbt);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:robot";
    }

    /** Whether {@code param}'s stack names {@code robot}'s board (7.1.x verbatim): a robot stack matches
     *  by board NBT id; an {@link IList} stack matches the robot's board stack or any wearable; any other
     *  stack matches a wearable by item identity. Wearables only resolve through {@link EntityRobot} —
     *  {@link IRobotAccess} has no wearables (documented fidelity gap). */
    public static boolean matches(IStatementParameter param, IRobotAccess robot) {
        ItemStack stack = param.getItemStack();
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof IList list) {
                RedstoneBoardRobot board = robot.getBoard();
                if (board != null && list.matches(stack,
                        ItemRobot.createRobotStack(board.getNBTHandler().getID(), robot.getPower()))) {
                    return true;
                }
                if (robot instanceof EntityRobot entityRobot) {
                    for (ItemStack target : entityRobot.getWearables()) {
                        if (!target.isEmpty() && list.matches(stack, target)) {
                            return true;
                        }
                    }
                }
            } else if (stack.getItem() instanceof ItemRobot) {
                RedstoneBoardRobot board = robot.getBoard();
                if (board != null && ItemRobot.getRobotBoard(stack) == board.getNBTHandler()) {
                    return true;
                }
            } else if (robot instanceof EntityRobot entityRobot) {
                for (ItemStack target : entityRobot.getWearables()) {
                    if (!target.isEmpty() && StackUtil.isMatchingItem(stack, target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public StatementParameterRobot onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        if (clickedStack.isEmpty() && (stack.isEmpty() || stack.getItem() instanceof ItemRobot)) {
            // Empty click over an empty or robot-holding parameter cycles to the next board (7.1.x).
            RedstoneBoardRobotNBT nextBoard = RobotUtils.getNextBoard(stack, mouseClick.getButton() > 0);
            return nextBoard != null
                    ? new StatementParameterRobot(ItemRobot.createRobotStack(nextBoard.getID(), 0))
                    : new StatementParameterRobot();
        }
        if (clickedStack.isEmpty()) {
            // An empty click over a List or wearable parameter clears it (7.1.x: the base's empty-hand rule).
            return new StatementParameterRobot();
        }
        if (clickedStack.getItem() instanceof ItemRobot || clickedStack.getItem() instanceof IList
                || EntityRobot.isWearable(clickedStack)) {
            // A held robot stack selects its board; a held List names robots by what the list holds (matches()
            // above); a held wearable names the robots wearing one. 7.1.x took any stack here, but only these
            // three kinds can ever match a robot, so anything else is refused. The base's count-1 copy, inlined —
            // the base ignores the clicked stack, so the widget must handle it itself.
            ItemStack copy = clickedStack.copy();
            copy.setCount(1);
            return new StatementParameterRobot(copy);
        }
        return this;
    }
}
