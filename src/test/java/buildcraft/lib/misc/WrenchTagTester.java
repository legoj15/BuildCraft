package buildcraft.lib.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.Tags;

import buildcraft.api.properties.BuildCraftProperties;

import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.TileEngineBase_BC8;

/**
 * Covers issue #21 — any item tagged {@code c:tools/wrench} acts as a BuildCraft wrench, and
 * BuildCraft's own wrench is published into that tag.
 *
 * <p>{@link #testForeignWrenchDetection} pins the central {@link EntityUtil#isWrench} predicate
 * (the API-interface path, the convention-tag path, the negative/empty cases, and the
 * {@code allowForeignWrenches} config gate) plus the wrench item's tag membership.
 *
 * <p>{@link #testForeignWrenchRotatesEngine} pins the Tier-B half: a foreign (tag-only) wrench
 * has no {@code useOn} hook to drive {@code ICustomRotationHandler}, so engines/dynamos/etc. must
 * rotate themselves block-side via {@link BlockUtil#rotateByForeignWrench}. The test stands up a
 * Stirling engine between two MJ receivers and asserts the helper advances its facing.
 *
 * <p>A test-only datapack tag ({@code src/test/resources/data/c/tags/item/tools/wrench.json}) adds
 * {@code minecraft:shears} to the convention tag so a non-{@code IToolWrench} item is available as
 * the foreign-wrench proxy. Shears is otherwise unused by any test, so tagging it globally for the
 * run has no collateral effect.
 */
public class WrenchTagTester {

    public static void testForeignWrenchDetection(GameTestHelper helper) {
        ItemStack bcWrench = new ItemStack(BCCoreItems.WRENCH.get());
        ItemStack shears = new ItemStack(Items.SHEARS);   // foreign tool, tagged c:tools/wrench (test datapack)
        ItemStack diamond = new ItemStack(Items.DIAMOND); // not a wrench by any tag

        // BuildCraft's own wrench: detected via the IToolWrench API AND published into the tag (issue #21 part 2).
        if (!EntityUtil.isWrench(bcWrench)) {
            throw new IllegalStateException("BuildCraft wrench must be detected as a wrench");
        }
        if (!bcWrench.is(Tags.Items.TOOLS_WRENCH)) {
            throw new IllegalStateException(
                    "BuildCraft wrench must be a member of c:tools/wrench (data/c/tags/item/tools/wrench.json)");
        }

        // A foreign tool that is ONLY in the tag (not an IToolWrench) is accepted by default.
        if (!shears.is(Tags.Items.TOOLS_WRENCH)) {
            throw new IllegalStateException(
                    "Test precondition: shears must be in c:tools/wrench (test datapack tag did not load/merge)");
        }
        if (!EntityUtil.isWrench(shears)) {
            throw new IllegalStateException("A c:tools/wrench-tagged foreign tool must be detected as a wrench");
        }

        // Negative + empty guards.
        if (EntityUtil.isWrench(diamond)) {
            throw new IllegalStateException("A non-wrench item must not be detected as a wrench");
        }
        if (EntityUtil.isWrench(ItemStack.EMPTY)) {
            throw new IllegalStateException("An empty stack must not be detected as a wrench");
        }

        // Config gate: disabling allowForeignWrenches restricts detection to the IToolWrench API,
        // without affecting BuildCraft's own wrench.
        boolean previous = BCLibConfig.allowForeignWrenches.get();
        try {
            BCLibConfig.allowForeignWrenches.set(false);
            if (EntityUtil.isWrench(shears)) {
                throw new IllegalStateException(
                        "With allowForeignWrenches=false a tag-only foreign wrench must NOT be detected");
            }
            if (!EntityUtil.isWrench(bcWrench)) {
                throw new IllegalStateException(
                        "With allowForeignWrenches=false BuildCraft's own wrench must still be detected");
            }
        } finally {
            BCLibConfig.allowForeignWrenches.set(previous);
        }

        helper.succeed();
    }

    public static void testForeignWrenchRotatesEngine(GameTestHelper helper) {
        if (BCCoreBlocks.POWER_TESTER == null) {
            throw new IllegalStateException(
                    "POWER_TESTER block not registered — test JVM was launched without -Dbuildcraft.dev=true.");
        }
        BlockPos enginePos = new BlockPos(2, 2, 2);

        BlockState engineState = BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
        helper.setBlock(enginePos, engineState);
        // Two MJ receivers, one above (UP) and one north — both are valid engine receivers, so the
        // engine is stable while facing either and auto-orientation never fights the test.
        helper.setBlock(enginePos.above(), BCCoreBlocks.POWER_TESTER.get());
        helper.setBlock(enginePos.north(), BCCoreBlocks.POWER_TESTER.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            //?} else {
            /*TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            if (engine == null) {
                throw new IllegalStateException("Failed to place Stirling engine");
            }
            // Pin the facing to a valid receiver (UP) so the engine is stable going into the test.
            engine.setOrientation(Direction.UP);
            if (engine.getOrientation() != Direction.UP) {
                throw new IllegalStateException(
                        "Test precondition: engine should face UP before rotation, got " + engine.getOrientation());
            }
            if (!engine.hasAlternateReceiver()) {
                throw new IllegalStateException(
                        "Test precondition: engine should see the north power tester as an alternate receiver");
            }

            BlockState before = helper.getBlockState(enginePos);
            BlockPos absPos = helper.absolutePos(enginePos);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);

            // Drive exactly what the block does for a foreign (tag-only) wrench on a non-crouch click.
            BlockUtil.rotateByForeignWrench(
                    helper.getLevel(), absPos, before, player, InteractionHand.MAIN_HAND, Direction.WEST);

            //? if >=1.21.10 {
            TileEngineBase_BC8 after = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            //?} else {
            /*TileEngineBase_BC8 after = helper.getBlockEntity(enginePos);*/
            //?}
            if (after.getOrientation() != Direction.NORTH) {
                throw new IllegalStateException(
                        "Foreign-wrench rotation should advance the engine from UP to the next receiver (NORTH), got "
                                + after.getOrientation());
            }
            if (helper.getBlockState(enginePos).getValue(BuildCraftProperties.BLOCK_FACING_6) != Direction.NORTH) {
                throw new IllegalStateException("Engine blockstate facing should be NORTH after rotation");
            }
            helper.succeed();
        });
    }
}
