package buildcraft.core.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.core.tile.TileMarkerPath;

public class MarkerTester {

    public static void testMarkerOrientation(GameTestHelper helper) {
        // Test placing markers on all 6 faces of a central block.
        BlockPos center = new BlockPos(2, 2, 2);
        helper.setBlock(center, Blocks.STONE);
        
        for (Direction dir : Direction.values()) {
            BlockPos markerPos = center.relative(dir);
            // Place marker attached to the 'dir' face of the center block
            BlockState state = BCCoreBlocks.MARKER_VOLUME.get().defaultBlockState()
                    .setValue(BuildCraftProperties.BLOCK_FACING_6, dir.getOpposite());
            helper.setBlock(markerPos, state);
            
            // Verify it was placed
            if (!helper.getBlockState(markerPos).is(BCCoreBlocks.MARKER_VOLUME.get())) {
                throw new IllegalStateException("Failed to place marker on face: " + dir);
            }
        }
        
        // Remove the center block
        helper.setBlock(center, Blocks.AIR);
        
        // Wait a tick for the block update to process
        helper.runAfterDelay(2, () -> {
            for (Direction dir : Direction.values()) {
                BlockPos markerPos = center.relative(dir);
                // All markers should have popped off because they are unsupported!
                if (!helper.getBlockState(markerPos).isAir()) {
                    throw new IllegalStateException("Marker did not pop off when its supporting block was removed: " + dir);
                }
            }
            helper.succeed();
        });
    }

    public static void testVolumeLineOfSight(GameTestHelper helper) {
        BlockPos pos1 = new BlockPos(1, 2, 1);
        BlockPos pos2 = new BlockPos(5, 2, 1);
        BlockPos wallPos = new BlockPos(3, 2, 1);

        helper.setBlock(pos1, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos2, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(wallPos, Blocks.STONE); // Solid block obstructing

        helper.runAfterDelay(1, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos1)) instanceof TileMarkerVolume volume) {
                volume.onManualConnectionAttempt(null);
                
                // Validate connection
                helper.runAfterDelay(5, () -> {
                    BlockPos expectedMin = helper.absolutePos(pos1);
                    BlockPos expectedMax = helper.absolutePos(pos2);
                    if (!volume.min().equals(expectedMin) || !volume.max().equals(expectedMax)) {
                        throw new IllegalStateException("Line of sight failed through solid block! Min: " + volume.min() + ", Max: " + volume.max());
                    }
                    helper.succeed();
                });
            } else {
                throw new IllegalStateException("TileEntity at pos1 is not a TileMarkerVolume!");
            }
        });
    }

    public static void testPathLineOfSight(GameTestHelper helper) {
        BlockPos pos1 = new BlockPos(1, 2, 1);
        BlockPos pos2 = new BlockPos(5, 2, 5); // Diagonal
        BlockPos wallPos = new BlockPos(3, 2, 3);

        helper.setBlock(pos1, BCCoreBlocks.MARKER_PATH.get());
        helper.setBlock(pos2, BCCoreBlocks.MARKER_PATH.get());
        helper.setBlock(wallPos, Blocks.STONE); // Solid block obstructing

        helper.runAfterDelay(1, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos1)) instanceof TileMarkerPath path) {
                path.getCache().getSubCache(helper.getLevel()).tryConnect(helper.absolutePos(pos1), helper.absolutePos(pos2));
                
                helper.runAfterDelay(5, () -> {
                    BlockPos expectedPos = helper.absolutePos(pos2);
                    if (path.getCurrentConnection() == null || !path.getCurrentConnection().getMarkerPositions().contains(expectedPos)) {
                        throw new IllegalStateException("Diagonal Path line of sight failed through solid block!");
                    }
                    helper.succeed();
                });
            } else {
                throw new IllegalStateException("TileEntity at pos1 is not a TileMarkerPath!");
            }
        });
    }

    public static void testVolumeTriangulation2D(GameTestHelper helper) {
        BlockPos pos1 = new BlockPos(1, 2, 1);
        BlockPos pos2 = new BlockPos(5, 2, 1);
        BlockPos pos3 = new BlockPos(1, 2, 5);

        helper.setBlock(pos1, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos2, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos3, BCCoreBlocks.MARKER_VOLUME.get());

        helper.runAfterDelay(1, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos1)) instanceof TileMarkerVolume volume) {
                volume.onManualConnectionAttempt(null);
                
                helper.runAfterDelay(5, () -> {
                    BlockPos expectedMin = helper.absolutePos(pos1);
                    BlockPos expectedMax = helper.absolutePos(new BlockPos(5, 2, 5));
                    if (!volume.min().equals(expectedMin) || !volume.max().equals(expectedMax)) {
                        throw new IllegalStateException("2D Triangulation failed! Expected plane: " + expectedMin + " to " + expectedMax + ". Got: " + volume.min() + " to " + volume.max());
                    }
                    helper.succeed();
                });
            }
        });
    }

    public static void testVolumeTriangulation3D(GameTestHelper helper) {
        BlockPos pos1 = new BlockPos(2, 2, 2);
        BlockPos pos2 = new BlockPos(5, 2, 2);
        BlockPos pos3 = new BlockPos(2, 5, 2);
        BlockPos pos4 = new BlockPos(2, 2, 5);

        helper.setBlock(pos1, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos2, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos3, BCCoreBlocks.MARKER_VOLUME.get());
        helper.setBlock(pos4, BCCoreBlocks.MARKER_VOLUME.get());

        helper.runAfterDelay(1, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos1)) instanceof TileMarkerVolume volume) {
                volume.onManualConnectionAttempt(null);
                
                helper.runAfterDelay(10, () -> {
                    BlockPos expectedMin = helper.absolutePos(pos1);
                    BlockPos expectedMax = helper.absolutePos(new BlockPos(5, 5, 5));
                    if (!volume.min().equals(expectedMin) || !volume.max().equals(expectedMax)) {
                        throw new IllegalStateException("3D Triangulation failed! Expected volume: " + expectedMin + " to " + expectedMax + ". Got: " + volume.min() + " to " + volume.max());
                    }
                    helper.succeed();
                });
            }
        });
    }
}
