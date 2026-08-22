package buildcraft.robotics.statements;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.api.statements.StatementParameterItemStack;

/** A statement parameter holding a robot board. Matching a robot against it compares the board id in
 *  the stack against the robot's installed board. */
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

    /** Red-baseline degenerate: no board matches yet. Ph6-green compares the board id in {@code p}'s
     *  stack ({@code ItemRobot.getBoardId}) against the robot's installed board. */
    public boolean matches(IStatementParameter p, IRobotAccess robot) {
        return false;
    }

    @Override
    public StatementParameterRobot onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        // Red-baseline degenerate: no cycling yet. Ph6-green inlines the base decrement semantics and
        // adds the board-cycling behaviour (RobotUtils.getNextBoard) when the held stack is a robot.
        return this;
    }
}
