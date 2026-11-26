/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BuilderRefloodTester;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.builders.tile.TileFiller;
import buildcraft.lib.misc.NBTUtilBC;

/**
 * A machine built from a blueprint starts with no working area of its own. The Filler, the Architect Table, the Builder,
 * the Mining Well and the Laser save the absolute world positions of the area they work on, and a copy built somewhere
 * else would load them as they are and go on working the original's area; the rules that rebuild a machine drop those
 * keys on the build path, so a blueprint captured before they did is covered too. {@link SchematicReplaceNbtTester}
 * pins the data every machine's rules produce. This pins the other half for the two machines with the most to lose:
 * the placed block entity, read after the real {@link SchematicBlockDefault#build}. A Filler loses its box. A Builder
 * loses its path and the index into it (an index past the single base position it falls back to would leave the copy
 * unable to resolve its blueprint at all) and works out its own box from its blueprint and its own position.
 */
public class SchematicMachineAreaTester {

    /**
     * A Filler with a box, captured and built elsewhere, has none and is not finished: the box and the finished flag are
     * not copied, and what is not its area (the invert flag) is. The scan holds the box, so the stored blueprint is the one an older capture would have produced.
     */
    public static void machineSchematicBuildsFillerWithoutItsBox(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos builtPos = new BlockPos(4, 2, 4);
        helper.setBlock(sourcePos, BCBuildersBlocks.FILLER.get());
        TileFiller source = (TileFiller) level.getBlockEntity(helper.absolutePos(sourcePos));
        helper.assertTrue(source != null, "The placed Filler must have its block entity");
        // The area a Filler placed against markers holds: the same public box TileFiller.onPlacedBy fills in.
        source.box.reset();
        source.box.setMin(helper.absolutePos(new BlockPos(0, 3, 0)));
        source.box.setMax(helper.absolutePos(new BlockPos(2, 4, 2)));
        // What onPlacedBy leaves on a Filler placed against a volume box. A copy that kept it would take any volume
        // box behind it on its first tick, the box of the Filler it was copied from included.
        source.markerBox = false;
        source.inverted = true;
        try {
            SchematicBlockDefault captured = capture(helper, sourcePos);
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "The scan of a Filler with a box must hold it, got " + captured.tileNbt);
            // A Filler that has filled its box saves finished (TileFiller.writeData), which has no public setter.
            captured.tileNbt.putBoolean("finished", true);

            helper.assertTrue(captured.build(level, helper.absolutePos(builtPos)), "The Filler should build into the empty cell");
            TileFiller built = (TileFiller) level.getBlockEntity(helper.absolutePos(builtPos));
            helper.assertTrue(built != null, "The built Filler must have its block entity");
            helper.assertFalse(built.box.isInitialized(), "The built Filler must start with no box, got " + built.box);
            helper.assertTrue(built.markerBox,
                    "The built Filler must not be marked as owing its box to a volume box behind it");
            helper.assertFalse(built.isFinished(),
                    "The built Filler must not start out finished: once it had a box of its own it would stay parked");
            helper.assertTrue(built.inverted, "The built Filler must keep its invert flag");
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "Building must not edit the stored blueprint (it still holds the box)");
        } finally {
            helper.setBlock(sourcePos, Blocks.AIR);
            helper.setBlock(builtPos, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * A Builder that had a path of base positions, captured with its blueprint in the slot and built elsewhere, has no
     * path, and works out its own box from its blueprint relative to its own position: the original's box moved by the
     * distance between the two, which is where the copy's base position stands. The stored index into the path goes with
     * it. Left behind it points past the one base position a Builder without a path falls back to, the copy resolves no
     * blueprint and keeps no box, which is what this checks the box is back for.
     */
    public static void machineSchematicBuildsBuilderWithItsOwnBoxAndNoPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TileBuilder source = BuilderRefloodTester.setUpBuilder(helper,
                new int[] {BuilderRefloodTester.STONE, BuilderRefloodTester.STONE}, new BlockPos[0]);
        BlockPos sourcePos = BuilderRefloodTester.BUILDER;
        BlockPos builtPos = new BlockPos(4, 2, 5);
        BlockPos sourceAbs = helper.absolutePos(sourcePos);
        BlockPos builtAbs = helper.absolutePos(builtPos);
        TileBuilder[] built = {null};
        try {
            helper.assertTrue(source.getBox().isInitialized(), "The source Builder must have worked out its box");
            // The path a path-marker chain gives a Builder, through the public field the tile fills in at placement.
            source.path = List.of(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(0, 2, 5)));
            SchematicBlockDefault captured = capture(helper, sourcePos);
            helper.assertTrue(NBTUtilBC.getInt(captured.tileNbt, "path_count", 0) == 2,
                    "The scan of a Builder with a path must hold it, got " + captured.tileNbt);
            helper.assertTrue(NBTUtilBC.getBoolean(captured.tileNbt, "box_initialized", false),
                    "The scan of a Builder with a blueprint must hold its box, got " + captured.tileNbt);
            // A Builder that finished its first base position saves the index of the next one (TileBuilder.writeData).
            captured.tileNbt.putInt("currentBasePosIndex", 1);

            helper.assertTrue(captured.build(level, builtAbs), "The Builder should build into the empty cell");
            built[0] = (TileBuilder) level.getBlockEntity(builtAbs);
            helper.assertTrue(built[0] != null, "The built Builder must have its block entity");
            helper.assertTrue(built[0].path == null, "The built Builder must start with no path, got " + built[0].path);
            helper.assertTrue(built[0].getBox().isInitialized(),
                    "The built Builder must work out its own box from its blueprint");
            BlockPos shift = builtAbs.subtract(sourceAbs);
            helper.assertTrue(built[0].getBox().min().equals(source.getBox().min().offset(shift))
                            && built[0].getBox().max().equals(source.getBox().max().offset(shift)),
                    "The built Builder's box must be the original's moved with it, got " + built[0].getBox()
                            + " against " + source.getBox() + " moved by " + shift);
            helper.assertTrue(NBTUtilBC.getInt(captured.tileNbt, "path_count", 0) == 2,
                    "Building must not edit the stored blueprint (it still holds the path)");
        } finally {
            clear(level, source, sourceAbs);
            if (built[0] != null) {
                clear(level, built[0], builtAbs);
            }
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                }
            }
        }
        helper.succeed();
    }

    /** Wipes a Builder so removing it spills nothing into the world: the snapshot goes first (a task in flight refunds
     *  its items into the resources), then the contents. */
    private static void clear(ServerLevel level, TileBuilder tile, BlockPos abs) {
        tile.setSnapshot(ItemStack.EMPTY);
        tile.clearContent();
        level.removeBlock(abs, false);
    }

    /** Captures the block in the cell the way the Architect does, through the real factory chain. */
    private static SchematicBlockDefault capture(GameTestHelper helper, BlockPos relPos) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(relPos);
        BlockState state = level.getBlockState(abs);
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, BlockPos.ZERO, abs, state, state.getBlock()));
        helper.assertTrue(schematic instanceof SchematicBlockDefault,
                "A machine should route to SchematicBlockDefault, got " + schematic.getClass().getSimpleName());
        SchematicBlockDefault captured = (SchematicBlockDefault) schematic;
        helper.assertTrue(captured.tileNbt != null, "The scan must keep the machine's tile data");
        return captured;
    }
}
