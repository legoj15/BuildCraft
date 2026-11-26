package buildcraft.builders.snapshot.pattern;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

import buildcraft.api.filler.FillerManager;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.statements.IActionExternal;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.containers.IFillerStatementContainer;

import buildcraft.lib.misc.LocaleUtil;

import buildcraft.builders.BCBuildersStatements;
import buildcraft.core.statements.BCStatement;

@SuppressWarnings("this-escape")
public abstract class Pattern extends BCStatement implements IFillerPattern, IActionExternal {
    private final String desc;

    public Pattern(String tag) {
        super("buildcraft:" + tag);
        desc = "fillerpattern." + tag;
        FillerManager.registry.addPattern(this);
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize(desc);
    }

    @Override
    public void actionActivate(BlockEntity target, Direction side, IStatementContainer source, IStatementParameter[] parameters) {
        if (source instanceof IFillerStatementContainer) {
            ((IFillerStatementContainer) source).setPattern(this, parameters);
        } else if (target instanceof IFillerStatementContainer) {
            ((IFillerStatementContainer) target).setPattern(this, parameters);
        }
    }

    @Override
    public IFillerPattern[] getPossible() {
        return BCBuildersStatements.PATTERNS;
    }

    /**
     * The parameters this pattern holds once what it belongs to is turned one clockwise quarter turn about the Y axis
     * ({@code Rotation.CLOCKWISE_90}: NORTH becomes EAST), in the same slots, a slot that holds nothing kept as it
     * was. The shape is laid out in world axes inside the Filler's box, so a Filler in a blueprint built rotated turns
     * its parameters to keep the shape turned with the blueprint. Each parameter turns by its own
     * {@link IStatementParameter#rotateLeft()}, which is all a pattern needs whose parameters name a direction or an
     * axis on their own; a pattern with a parameter whose meaning depends on another one overrides this.
     */
    public IStatementParameter[] rotateParameters(IStatementParameter[] params) {
        IStatementParameter[] turned = new IStatementParameter[params.length];
        for (int i = 0; i < params.length; i++) {
            turned[i] = params[i] == null ? null : params[i].rotateLeft();
        }
        return turned;
    }
}
