/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import java.util.EnumSet;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.block.BlockBuilder;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterAxis;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterCenter;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterFacing;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterHollow;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterRotation;
import buildcraft.builders.snapshot.pattern.parameter.PatternParameterYDir;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.BCCoreBlocks;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockFloodGate;
import buildcraft.factory.tile.TileFloodGate;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.statement.StatementTypeParam;

/**
 * Machines turn with a blueprint that is built rotated. A block entity that saves a direction of its own (an engine's
 * {@code orientation}, a flood gate's {@code openSides}, a Builder's {@code rotation}) keeps what it was captured with
 * unless the schematic turns that data with the block state, and what the tile saves is what the machine acts on: the
 * engine powers the face it saved, the gate floods through the sides it saved, the Builder lays its blueprint out by
 * the turn it saved. Each case captures the real machine through the real factory chain, rotates the schematic and
 * reads what is built (the Builder's case reads the tag, which is what the placed Builder loads), with the stored
 * schematic left untouched.
 */
public class SchematicMachineRotationTester {

    /**
     * An engine captured pointing NORTH and built after a 90 degree clockwise turn powers EAST, and its block state
     * faces EAST too. The tag-level checks cover the other turns, the vertical faces (which a turn about Y leaves), and
     * a tag that holds a value no direction has or none at all (blueprint data is not guaranteed well formed).
     */
    public static void machineSchematicRotatesEngineOrientation(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        placeEngine(helper, sourcePos, Direction.NORTH);
        SchematicBlockDefault captured = capture(helper, sourcePos);
        helper.assertTrue(orientationOf(captured) == Direction.NORTH,
                "The captured engine must hold orientation NORTH, got " + orientationOf(captured));

        SchematicBlockDefault rotated = captured.getRotated(Rotation.CLOCKWISE_90);
        helper.assertTrue(orientationOf(rotated) == Direction.EAST,
                "The rotated schematic must hold orientation EAST, got " + orientationOf(rotated));
        helper.assertTrue(rotated.getBlockStateForRender().getValue(BuildCraftProperties.BLOCK_FACING_6) == Direction.EAST,
                "The rotated schematic's block state must face EAST");

        BlockPos builtPos = new BlockPos(4, 2, 4);
        BlockPos builtAbs = helper.absolutePos(builtPos);
        helper.assertTrue(rotated.build(helper.getLevel(), builtAbs), "The rotated engine should build into the empty cell");
        BlockState builtState = helper.getLevel().getBlockState(builtAbs);
        helper.assertTrue(builtState.getValue(BuildCraftProperties.BLOCK_FACING_6) == Direction.EAST,
                "The built engine's block state must face EAST, got " + builtState.getValue(BuildCraftProperties.BLOCK_FACING_6));
        TileEngineBase_BC8 built = (TileEngineBase_BC8) helper.getLevel().getBlockEntity(builtAbs);
        helper.assertTrue(built != null, "The built engine must have its block entity");
        helper.assertTrue(built.getOrientation() == Direction.EAST,
                "The built engine must power EAST, got " + built.getOrientation());

        helper.assertTrue(orientationOf(captured.getRotated(Rotation.CLOCKWISE_180)) == Direction.SOUTH,
                "CLOCKWISE_180 must turn NORTH to SOUTH");
        helper.assertTrue(orientationOf(captured.getRotated(Rotation.COUNTERCLOCKWISE_90)) == Direction.WEST,
                "COUNTERCLOCKWISE_90 must turn NORTH to WEST");
        helper.assertTrue(orientationOf(captured.getRotated(Rotation.NONE)) == Direction.NORTH,
                "NONE must keep the captured orientation");
        helper.assertTrue(orientationOf(captured) == Direction.NORTH,
                "getRotated must not mutate the original schematic (its orientation should still be NORTH)");

        for (Direction vertical : new Direction[] {Direction.UP, Direction.DOWN}) {
            captured.tileNbt.putByte("orientation", (byte) vertical.ordinal());
            helper.assertTrue(orientationOf(captured.getRotated(Rotation.CLOCKWISE_90)) == vertical,
                    "A turn about Y must leave an engine pointing " + vertical);
        }
        captured.tileNbt.putByte("orientation", (byte) 9);
        helper.assertTrue(NBTUtilBC.getByte(captured.getRotated(Rotation.CLOCKWISE_90).tileNbt, "orientation", (byte) 0) == 9,
                "An orientation no direction has must be left as it was");
        captured.tileNbt.remove("orientation");
        helper.assertFalse(captured.getRotated(Rotation.CLOCKWISE_90).tileNbt.contains("orientation"),
                "A tag with no orientation must not gain one");
        helper.succeed();
    }

    /**
     * A flood gate floods through the sides {@code TileFloodGate.openSides} names, and its block state only draws them.
     * A gate captured with EAST closed and built after a 90 degree clockwise turn has SOUTH closed in both, and is still
     * open on the other four sides (NORTH turned to EAST, so EAST is open again). The tag-level checks cover the mask
     * itself: every other side, the bits above the sides carried over unchanged, and a tag with no mask.
     */
    public static void machineSchematicRotatesFloodGateSides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        BlockPos sourceAbs = helper.absolutePos(sourcePos);
        helper.setBlock(sourcePos, BCFactoryBlocks.FLOOD_GATE.get());
        TileFloodGate gate = (TileFloodGate) level.getBlockEntity(sourceAbs);
        helper.assertTrue(gate != null, "The placed flood gate must have its block entity");
        // The two writes the wrench toggle makes: the side leaves openSides, and the connected booleans follow it.
        gate.openSides.remove(Direction.EAST);
        BlockState state = level.getBlockState(sourceAbs);
        for (Map.Entry<Direction, Property<Boolean>> entry : BlockFloodGate.CONNECTED_MAP.entrySet()) {
            state = state.setValue(entry.getValue(), gate.openSides.contains(entry.getKey()));
        }
        level.setBlock(sourceAbs, state, Block.UPDATE_CLIENTS);
        SchematicBlockDefault captured = capture(helper, sourcePos);
        helper.assertTrue(maskOf(captured) == mask(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST),
                "The captured gate must be open on DOWN, NORTH, SOUTH and WEST only, got mask " + maskOf(captured));

        SchematicBlockDefault rotated = captured.getRotated(Rotation.CLOCKWISE_90);
        helper.assertTrue(maskOf(rotated) == mask(Direction.DOWN, Direction.NORTH, Direction.WEST, Direction.EAST),
                "The rotated schematic must be open on DOWN, NORTH, WEST and EAST (SOUTH closed), got mask " + maskOf(rotated));

        BlockPos builtPos = new BlockPos(4, 2, 4);
        BlockPos builtAbs = helper.absolutePos(builtPos);
        helper.assertTrue(rotated.build(level, builtAbs), "The rotated flood gate should build into the empty cell");
        TileFloodGate built = (TileFloodGate) level.getBlockEntity(builtAbs);
        helper.assertTrue(built != null, "The built flood gate must have its block entity");
        helper.assertFalse(built.openSides.contains(Direction.SOUTH), "The built gate must be closed on SOUTH");
        helper.assertTrue(built.openSides.contains(Direction.EAST), "The built gate must be open on EAST");
        helper.assertTrue(built.openSides.equals(EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.WEST, Direction.EAST)),
                "The built gate must be open on DOWN, NORTH, WEST and EAST only, got " + built.openSides);
        BlockState builtState = level.getBlockState(builtAbs);
        for (Map.Entry<Direction, Property<Boolean>> entry : BlockFloodGate.CONNECTED_MAP.entrySet()) {
            helper.assertTrue(builtState.getValue(entry.getValue()) == (entry.getKey() != Direction.SOUTH),
                    "The built gate's " + entry.getKey() + " connection must be " + (entry.getKey() != Direction.SOUTH));
        }

        helper.assertTrue(maskOf(captured.getRotated(Rotation.CLOCKWISE_180))
                        == mask(Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.EAST),
                "CLOCKWISE_180 must turn the open sides NORTH, SOUTH, WEST into SOUTH, NORTH, EAST");
        helper.assertTrue(maskOf(captured.getRotated(Rotation.COUNTERCLOCKWISE_90))
                        == mask(Direction.DOWN, Direction.WEST, Direction.EAST, Direction.SOUTH),
                "COUNTERCLOCKWISE_90 must turn the open sides NORTH, SOUTH, WEST into WEST, EAST, SOUTH");
        helper.assertTrue(maskOf(captured.getRotated(Rotation.NONE)) == maskOf(captured),
                "NONE must keep the captured sides");
        helper.assertTrue(maskOf(captured) == mask(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST),
                "getRotated must not mutate the original schematic (its sides should still be the captured ones)");

        captured.tileNbt.putByte("openSides", (byte) (0b1100_0000 | mask(Direction.NORTH, Direction.UP)));
        helper.assertTrue(maskOf(captured.getRotated(Rotation.CLOCKWISE_90))
                        == (0b1100_0000 | mask(Direction.EAST, Direction.UP)),
                "The bits above the six sides must be carried over unchanged");
        captured.tileNbt.remove("openSides");
        helper.assertFalse(captured.getRotated(Rotation.CLOCKWISE_90).tileNbt.contains("openSides"),
                "A tag with no mask must not gain one");
        helper.succeed();
    }

    /**
     * A Builder saves {@code rotation}, the turn that maps its blueprint item's facing onto the Builder block's own
     * facing ({@code TileBuilder.writeData} writes it as the ordinal of that {@link Rotation}). The block's facing
     * turns with the blueprint, so the saved turn is composed with the same rotation: a Builder captured with a
     * quarter turn clockwise and rebuilt after one more has a half turn, and after a quarter turn back has none. The
     * Builder is not built here: its tag is what the placed block entity loads, and the original schematic keeps its
     * own. A tag that holds a turn no rotation has, or none (a Builder with no blueprint saves no turn), is left alone.
     */
    public static void machineSchematicRotatesBuilderRotation(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 2, 1);
        helper.setBlock(sourcePos, BCBuildersBlocks.BUILDER.get().defaultBlockState()
                .setValue(BlockBuilder.FACING, Direction.NORTH));
        SchematicBlockDefault captured = capture(helper, sourcePos);
        helper.assertFalse(captured.tileNbt.contains("rotation"),
                "A Builder with no blueprint saves no rotation, got " + captured.tileNbt);
        helper.assertFalse(captured.getRotated(Rotation.CLOCKWISE_90).tileNbt.contains("rotation"),
                "A tag with no rotation must not gain one");
        captured.tileNbt.putInt("rotation", Rotation.CLOCKWISE_90.ordinal());

        SchematicBlockDefault clockwise = captured.getRotated(Rotation.CLOCKWISE_90);
        helper.assertTrue(rotationOf(clockwise) == Rotation.CLOCKWISE_180.ordinal(),
                "CLOCKWISE_90 on a saved CLOCKWISE_90 must give CLOCKWISE_180, got " + rotationOf(clockwise));
        helper.assertTrue(clockwise.getBlockStateForRender().getValue(BlockBuilder.FACING) == Direction.EAST,
                "The Builder block's own facing must turn with the blueprint");
        helper.assertTrue(rotationOf(captured.getRotated(Rotation.COUNTERCLOCKWISE_90)) == Rotation.NONE.ordinal(),
                "COUNTERCLOCKWISE_90 on a saved CLOCKWISE_90 must give NONE, got "
                        + rotationOf(captured.getRotated(Rotation.COUNTERCLOCKWISE_90)));
        helper.assertTrue(rotationOf(captured.getRotated(Rotation.CLOCKWISE_180)) == Rotation.COUNTERCLOCKWISE_90.ordinal(),
                "CLOCKWISE_180 on a saved CLOCKWISE_90 must give COUNTERCLOCKWISE_90, got "
                        + rotationOf(captured.getRotated(Rotation.CLOCKWISE_180)));
        helper.assertTrue(rotationOf(captured.getRotated(Rotation.NONE)) == Rotation.CLOCKWISE_90.ordinal(),
                "NONE must keep the saved rotation");
        helper.assertTrue(rotationOf(captured) == Rotation.CLOCKWISE_90.ordinal(),
                "getRotated must not mutate the original schematic (its rotation should still be CLOCKWISE_90)");

        captured.tileNbt.putInt("rotation", 9);
        helper.assertTrue(rotationOf(captured.getRotated(Rotation.CLOCKWISE_90)) == 9,
                "A rotation no Rotation has must be left as it was");
        helper.succeed();
    }

    /**
     * A Filler lays its pattern out in world axes inside its box and never reads its own facing, so the direction
     * parameters it saved turn with a rotated blueprint or the built Filler would shape its box another way. Each
     * Filler is captured with a pattern, rotated by 90 degrees clockwise, built, and the built Filler's parameters read
     * back: a hemisphere's open face NORTH is EAST, a quarter sphere's second open face (DOWN, chosen by its quarter
     * turn count together with its face) stays DOWN, which takes a different count on the new face, a pyramid's apex
     * NORTH is EAST, and a triangle lying flat is turned a quarter in its plane. The other turns are read from the
     * rotated schematic's tag, the original keeps its own, and a tag with no pattern gains none.
     */
    public static void machineSchematicRotatesFillerPattern(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IStatementParameter[][] given = {
            {PatternParameterHollow.FILLED_INNER, PatternParameterFacing.NORTH},
            {PatternParameterHollow.HOLLOW, PatternParameterFacing.NORTH, PatternParameterRotation.NONE},
            {PatternParameterYDir.DOWN, PatternParameterCenter.NORTH},
            {PatternParameterAxis.Y, PatternParameterHollow.HOLLOW, PatternParameterRotation.NONE}};
        IFillerPattern[] patterns = {BCBuildersStatements.PATTERN_HEMI_SPHERE, BCBuildersStatements.PATTERN_QUARTER_SPHERE,
            BCBuildersStatements.PATTERN_PYRAMID, BCBuildersStatements.PATTERN_TRIANGLE};
        IStatementParameter[][] expected = {
            {PatternParameterHollow.FILLED_INNER, PatternParameterFacing.EAST},
            {PatternParameterHollow.HOLLOW, PatternParameterFacing.EAST, PatternParameterRotation.QUARTER},
            {PatternParameterYDir.DOWN, PatternParameterCenter.EAST},
            {PatternParameterAxis.Y, PatternParameterHollow.HOLLOW, PatternParameterRotation.QUARTER}};
        for (int i = 0; i < patterns.length; i++) {
            // Two blocks apart along Z inside the 6x7 cell, the copies three blocks along X from their sources.
            BlockPos sourcePos = new BlockPos(1, 2, 2 * i);
            helper.setBlock(sourcePos, BCBuildersBlocks.FILLER.get());
            TileFiller source = (TileFiller) level.getBlockEntity(helper.absolutePos(sourcePos));
            helper.assertTrue(source != null, "The placed Filler must have its block entity");
            source.setPattern(patterns[i], given[i]);
            SchematicBlockDefault captured = capture(helper, sourcePos);
            String name = patterns[i].getUniqueTag();
            assertPattern(helper, captured, patterns[i], given[i], name + " as captured");

            SchematicBlockDefault rotated = captured.getRotated(Rotation.CLOCKWISE_90);
            assertPattern(helper, rotated, patterns[i], expected[i], name + " rotated CLOCKWISE_90");

            BlockPos builtPos = new BlockPos(4, 2, 2 * i);
            helper.assertTrue(rotated.build(level, helper.absolutePos(builtPos)), name + ": the Filler should build");
            TileFiller built = (TileFiller) level.getBlockEntity(helper.absolutePos(builtPos));
            helper.assertTrue(built != null, "The built Filler must have its block entity");
            helper.assertTrue(built.patternStatement.get() == patterns[i],
                    name + ": the built Filler must keep its pattern, got " + built.patternStatement.get());
            for (int slot = 0; slot < expected[i].length; slot++) {
                helper.assertTrue(built.patternStatement.get(slot) == expected[i][slot],
                        name + ": the built Filler's parameter " + slot + " must be " + expected[i][slot] + ", got "
                                + built.patternStatement.get(slot));
            }
            assertPattern(helper, captured, patterns[i], given[i], name + " as captured, after rotating a copy");
        }

        // The other turns, on the hemisphere's NORTH face and a vertical one, and a tag that holds no pattern.
        BlockPos hemiPos = new BlockPos(1, 2, 0);
        SchematicBlockDefault hemi = capture(helper, hemiPos);
        IStatementParameter[] hemiGiven = given[0];
        assertPattern(helper, hemi.getRotated(Rotation.CLOCKWISE_180), BCBuildersStatements.PATTERN_HEMI_SPHERE,
                new IStatementParameter[] {hemiGiven[0], PatternParameterFacing.SOUTH}, "hemisphere rotated CLOCKWISE_180");
        assertPattern(helper, hemi.getRotated(Rotation.COUNTERCLOCKWISE_90), BCBuildersStatements.PATTERN_HEMI_SPHERE,
                new IStatementParameter[] {hemiGiven[0], PatternParameterFacing.WEST}, "hemisphere rotated COUNTERCLOCKWISE_90");
        assertPattern(helper, hemi.getRotated(Rotation.NONE), BCBuildersStatements.PATTERN_HEMI_SPHERE, hemiGiven,
                "hemisphere rotated NONE");
        TileFiller upFiller = (TileFiller) level.getBlockEntity(helper.absolutePos(hemiPos));
        upFiller.setPattern(BCBuildersStatements.PATTERN_HEMI_SPHERE,
                new IStatementParameter[] {PatternParameterHollow.FILLED_INNER, PatternParameterFacing.UP});
        assertPattern(helper, capture(helper, hemiPos).getRotated(Rotation.CLOCKWISE_90),
                BCBuildersStatements.PATTERN_HEMI_SPHERE,
                new IStatementParameter[] {PatternParameterHollow.FILLED_INNER, PatternParameterFacing.UP},
                "an UP face rotated CLOCKWISE_90");
        hemi.tileNbt.remove("patternStatement");
        helper.assertFalse(hemi.getRotated(Rotation.CLOCKWISE_90).tileNbt.contains("patternStatement"),
                "A tag with no pattern must not gain one");
        helper.succeed();
    }

    /** Checks the pattern kind and parameters of the Filler schematic's tag: {@code patternStatement}'s {@code s.kind}
     *  and its numbered parameter tags, as {@code FullStatement.writeToNbt} lays them out. */
    private static void assertPattern(GameTestHelper helper, SchematicBlockDefault schematic, IFillerPattern pattern,
                                      IStatementParameter[] parameters, String label) {
        CompoundTag statement = NBTUtilBC.getCompound(schematic.tileNbt, "patternStatement");
        helper.assertTrue(NBTUtilBC.getString(NBTUtilBC.getCompound(statement, "s"), "kind", "").equals(pattern.getUniqueTag()),
                label + ": the pattern kind must be kept, got " + statement);
        for (int slot = 0; slot < parameters.length; slot++) {
            IStatementParameter held = StatementTypeParam.INSTANCE.readFromNbt(
                    NBTUtilBC.getCompound(statement, Integer.toString(slot)));
            helper.assertTrue(held == parameters[slot],
                    label + ": parameter " + slot + " must be " + parameters[slot] + ", got " + held);
        }
    }

    /** Places an engine facing {@code facing} whose tile powers {@code facing} too, as a placed and settled one does. */
    private static void placeEngine(GameTestHelper helper, BlockPos relPos, Direction facing) {
        helper.setBlock(relPos, BCCoreBlocks.ENGINE_REDSTONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, facing));
        TileEngineBase_BC8 engine = (TileEngineBase_BC8) helper.getLevel().getBlockEntity(helper.absolutePos(relPos));
        helper.assertTrue(engine != null, "The placed engine must have its block entity");
        // The tile's orientation is its own copy of the facing; nothing re-checks it until the server ticks it.
        engine.setOrientation(facing);
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

    private static Direction orientationOf(ISchematicBlock schematic) {
        int ordinal = NBTUtilBC.getByte(schematic.getTileNbtForRender(), "orientation", (byte) -1);
        return ordinal < 0 || ordinal >= Direction.values().length ? null : Direction.values()[ordinal];
    }

    /** The {@code openSides} byte of a captured gate, as an unsigned value. */
    private static int maskOf(ISchematicBlock schematic) {
        return NBTUtilBC.getByte(schematic.getTileNbtForRender(), "openSides", (byte) 0) & 0xFF;
    }

    /** The mask {@code TileFloodGate.writeData} writes for these open sides: bit {@code 3D data value} per side. */
    private static int mask(Direction... open) {
        int mask = 0;
        for (Direction side : open) {
            mask |= 1 << side.get3DDataValue();
        }
        return mask;
    }

    private static int rotationOf(ISchematicBlock schematic) {
        CompoundTag tag = schematic.getTileNbtForRender();
        return NBTUtilBC.getInt(tag, "rotation", -1);
    }
}
