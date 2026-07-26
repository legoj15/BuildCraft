package buildcraft.lib.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;

public class BlockRenderShapeTester {

    /**
     * {@link BlockBCTile_Neptune} does not supply {@code getRenderShape} itself, and its
     * {@code BaseEntityBlock} parent defaults it to {@code INVISIBLE} on the 1.21.1 node only (the
     * override was dropped at 1.21.10) — so a subclass that forgets to declare
     * {@code RenderShape.MODEL} compiles clean and looks correct on every other node while rendering
     * nothing on 1.21.1. Walks the live block registry instead of a hand-list so a future subclass is
     * covered automatically instead of silently falling outside the check.
     */
    public static void testNeptuneSubclassesDeclareModelRenderShape(GameTestHelper helper) {
        StringBuilder offenders = new StringBuilder();
        int checked = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof BlockBCTile_Neptune)) {
                continue;
            }
            checked++;
            RenderShape shape = block.defaultBlockState().getRenderShape();
            if (shape != RenderShape.MODEL) {
                offenders.append("\n  ").append(block.getClass().getSimpleName()).append(" -> ").append(shape);
            }
        }
        if (checked == 0) {
            throw new IllegalStateException(
                "No BlockBCTile_Neptune subclasses found in the block registry -- registration broke");
        }
        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                "BlockBCTile_Neptune subclass(es) missing an explicit getRenderShape -> RenderShape.MODEL override:"
                    + offenders);
        }
        helper.succeed();
    }
}
