/*
 * Copyright (c) 2026 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.snapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Covers the canSurvive gate inside {@link SchematicBlockDefault#build(net.minecraft.world.level.Level, BlockPos, EnumFluidHandlingMode)}.
 * <p>
 * The terminal {@code build} call is exercised directly (same pattern as
 * {@link FluidHandlingModeTester}) — the full Builder tick loop is irrelevant here because the
 * regression we're guarding against lived entirely inside {@code build}: vanilla
 * {@code level.setBlock} doesn't validate support, so an unsupported torch would be set in the
 * world, popped off by the immediate neighbor-update tick, and {@code build} would return true
 * anyway. The item then gets silently consumed.
 */
public class SupportRequiredPlacementTester {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new IllegalStateException(msg);
    }

    /** Constructs a bare {@link SchematicBlockDefault} for {@code state}. Same pattern as
     *  {@link FluidHandlingModeTester#schem}. */
    private static SchematicBlockDefault schem(BlockState state) {
        SchematicBlockDefault s = new SchematicBlockDefault();
        s.blockState = state;
        s.placeBlock = state.getBlock();
        return s;
    }

    /**
     * Floor torch placed on top of a sturdy block must succeed. Baseline of the canSurvive gate:
     * given valid support, the gate must not over-reject.
     */
    public static void testTorchOnFloorPlaces(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.TORCH.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "build() must report success when torch has floor support");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.TORCH,
                    "torch should be in the world after build()");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Wall torch with the supporting wall in place behind it must succeed.
     * {@code facing=north} means the torch sticks out to the north — the wall is to the south.
     */
    public static void testWallTorchSurvivesWithSupport(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            BlockState wallTorch = Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH);
            // Wall torch facing north → wall is to the south (the side the torch is mounted on).
            helper.getLevel().setBlock(abs.relative(Direction.SOUTH), Blocks.STONE.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(wallTorch);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "build() must report success when wall torch has its supporting wall");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.WALL_TORCH,
                    "wall torch should be in the world after build()");
            assertTrue(after.getValue(WallTorchBlock.FACING) == Direction.NORTH,
                    "wall torch should preserve its facing");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The critical regression test. Wall torch with no supporting wall must be rejected at
     * build() time — without the canSurvive gate, vanilla setBlock would briefly set the state
     * and the immediate neighbor-update tick would pop it off, but build() would already have
     * returned true and the item would be silently consumed.
     */
    public static void testWallTorchRejectedWithoutSupport(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            BlockState wallTorch = Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH);
            // Deliberately leave the supporting position empty.

            SchematicBlockDefault s = schem(wallTorch);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(!placed, "build() must return false when wall torch has no support — this is what triggers cancelPlaceTask and refunds the item");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.isAir(),
                    "world position must be air; nothing should be placed when canSurvive fails");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Ladder needs a vertical wall behind it. Same canSurvive gate, different block — guards
     * against a regression where the gate is somehow torch-specific.
     */
    public static void testLadderRejectedFreestanding(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            BlockState ladder = Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, Direction.NORTH);
            // No wall behind the ladder.

            SchematicBlockDefault s = schem(ladder);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(!placed, "build() must return false for a freestanding ladder");

            assertTrue(helper.getLevel().getBlockState(abs).isAir(),
                    "world position must be air; ladder should not be placed without support");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Control: a fence floating in mid-air still places (canSurvive returns true). Guards
     * against an over-eager check that would break the common case of building solid blocks
     * sitting on nothing yet (the user expects them to be placed regardless — they're going to
     * sit there until the rest of the structure is built around them).
     */
    public static void testFenceAlwaysSurvives(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            // No support of any kind.

            SchematicBlockDefault s = schem(Blocks.OAK_FENCE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "fence has no support requirement; build() must succeed in mid-air");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_FENCE, "oak fence should be present");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Builder-placed leaves must be persistent regardless of the schematic capture, so they
     * don't decay in the new spot when the nearest log is more than 6 blocks away. Captures the
     * symptom that triggered the fix: the schematic has persistent=false, distance=7 (typical
     * for naturally-generated leaves), and without the override the placed state would inherit
     * those values, decay() would fire, and the Builder would loop place→decay→place.
     */
    public static void testLeavesPlacedAsPersistent(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            BlockState naturalLeaves = Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(LeavesBlock.PERSISTENT, false)
                    .setValue(LeavesBlock.DISTANCE, 7);

            SchematicBlockDefault s = schem(naturalLeaves);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "build() must report success for leaves in mid-air");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.OAK_LEAVES, "oak leaves should be present");
            assertTrue(after.getValue(LeavesBlock.PERSISTENT),
                    "Builder-placed leaves must have PERSISTENT=true even when the schematic captured persistent=false; otherwise decay() loops");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Cobblestone walls have neighbour-aware connection properties (north/east/south/west:
     * NONE/LOW/TALL, plus up). The schematic captures them with whatever connections the wall
     * had next to its neighbours in the source; after Builder placement, vanilla updateShape
     * recalculates them against the *current* neighbours and writes a different state. Without
     * an ignored-properties carve-out, isBuilt's strict equality fails on those connections,
     * marking the position TO_BREAK forever (break→re-place→re-update→break loop). Verifies
     * isBuilt accepts the world state once north/east/south/west/up are ignored.
     */
    public static void testWallIsBuiltIgnoresConnections(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);

            BlockState schematicWall = Blocks.COBBLESTONE_WALL.defaultBlockState()
                    .setValue(WallBlock.NORTH, WallSide.LOW)
                    .setValue(WallBlock.EAST, WallSide.LOW);
            BlockState worldWall = Blocks.COBBLESTONE_WALL.defaultBlockState();
            // World state has all sides NONE (default) — what updateShape produces when no
            // wall neighbours are present. Schematic asks for two LOW connections.
            helper.getLevel().setBlock(abs, worldWall, 3);

            SchematicBlockDefault s = schem(schematicWall);
            s.canBeReplacedWithBlocks.add(Blocks.COBBLESTONE_WALL);
            // Match what the JSON rule populates at init time.
            s.ignoredProperties.add(WallBlock.NORTH);
            s.ignoredProperties.add(WallBlock.EAST);
            s.ignoredProperties.add(WallBlock.SOUTH);
            s.ignoredProperties.add(WallBlock.WEST);
            s.ignoredProperties.add(WallBlock.UP);
            assertTrue(s.isBuilt(helper.getLevel(), abs),
                    "isBuilt must accept world wall with no connections when schematic asked for LOW connections (connections are ignored)");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Same shape as the wall test but for fence blocks. Fence connection properties are
     * booleans (north/east/south/west) defined on CrossCollisionBlock, while wall connections
     * are WallSide enums on WallBlock. Both share the property *names*, so a single ignored-
     * properties JSON entry covers both — this test guards that the fix actually applies to the
     * boolean variant too.
     */
    public static void testFenceIsBuiltIgnoresConnections(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);

            BlockState schematicFence = Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(CrossCollisionBlock.NORTH, true)
                    .setValue(CrossCollisionBlock.SOUTH, true);
            BlockState worldFence = Blocks.OAK_FENCE.defaultBlockState();
            helper.getLevel().setBlock(abs, worldFence, 3);

            SchematicBlockDefault s = schem(schematicFence);
            s.canBeReplacedWithBlocks.add(Blocks.OAK_FENCE);
            s.ignoredProperties.add(CrossCollisionBlock.NORTH);
            s.ignoredProperties.add(CrossCollisionBlock.EAST);
            s.ignoredProperties.add(CrossCollisionBlock.SOUTH);
            s.ignoredProperties.add(CrossCollisionBlock.WEST);
            assertTrue(s.isBuilt(helper.getLevel(), abs),
                    "isBuilt must accept world fence with no connections when schematic asked for north+south connections");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Schematics saved before a JSON rule modernization (e.g. before walls/leaves got their
     * connection/persistent properties listed in `multiple_variants.json`) have empty
     * ignoredProperties baked into their NBT. Loading them later goes through deserializeNBT
     * (NOT init), so without an explicit migration the old data wins forever and the user has
     * to re-scan every blueprint after every rule update. Verifies that deserializeNBT re-
     * derives ignoredProperties from the current rules so old blueprints transparently pick up
     * new carve-outs.
     */
    public static void testDeserializeMigratesIgnoredPropertiesFromCurrentRules(GameTestHelper helper) {
        try {
            // Simulate the on-disk state of a pre-fix blueprint: a cobblestone_wall schematic
            // whose ignoredProperties list was empty when serialized.
            SchematicBlockDefault original = new SchematicBlockDefault();
            original.blockState = Blocks.COBBLESTONE_WALL.defaultBlockState();
            original.placeBlock = Blocks.COBBLESTONE_WALL;
            // Deliberately do NOT add anything to original.ignoredProperties — that's exactly
            // the pre-fix state we're guarding against.
            net.minecraft.nbt.CompoundTag nbt = original.serializeNBT();

            SchematicBlockDefault loaded = new SchematicBlockDefault();
            try {
                loaded.deserializeNBT(nbt);
            } catch (buildcraft.api.core.InvalidInputDataException e) {
                throw new IllegalStateException("deserializeNBT should not throw on round-trip data: " + e.getMessage(), e);
            }

            boolean hasNorth = loaded.ignoredProperties.stream()
                .anyMatch(p -> p.getName().equals("north"));
            boolean hasUp = loaded.ignoredProperties.stream()
                .anyMatch(p -> p.getName().equals("up"));
            assertTrue(hasNorth, "deserialize must re-derive the 'north' ignored property from current rules so old blueprints stop oscillating on wall connection mismatches");
            assertTrue(hasUp, "deserialize must re-derive the 'up' ignored property from current rules");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Mirror of the wall migration test for leaves: pre-fix schematics have empty
     * ignoredProperties; loading them must pick up `persistent` and `distance` from the current
     * rules so isBuilt stops re-queueing the position when vanilla updates `distance` after
     * placement.
     */
    public static void testDeserializeMigratesLeavesIgnoredProperties(GameTestHelper helper) {
        try {
            SchematicBlockDefault original = new SchematicBlockDefault();
            original.blockState = Blocks.OAK_LEAVES.defaultBlockState();
            original.placeBlock = Blocks.OAK_LEAVES;
            net.minecraft.nbt.CompoundTag nbt = original.serializeNBT();

            SchematicBlockDefault loaded = new SchematicBlockDefault();
            try {
                loaded.deserializeNBT(nbt);
            } catch (buildcraft.api.core.InvalidInputDataException e) {
                throw new IllegalStateException("deserializeNBT should not throw on round-trip data: " + e.getMessage(), e);
            }

            boolean hasPersistent = loaded.ignoredProperties.stream()
                .anyMatch(p -> p.getName().equals("persistent"));
            boolean hasDistance = loaded.ignoredProperties.stream()
                .anyMatch(p -> p.getName().equals("distance"));
            assertTrue(hasPersistent, "deserialize must re-derive 'persistent' from current rules so isBuilt stops triggering decay→re-place loops");
            assertTrue(hasDistance, "deserialize must re-derive 'distance' from current rules");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * The tall-grass regression. A {@link DoublePlantBlock} is two stacked half-blocks; vanilla
     * {@code DoublePlantBlock.updateShape} turns a lone half into air the moment its partner is
     * missing. Building the LOWER half must place the UPPER half in the same {@code build()} call
     * (before the post-place updateShape sweep), exactly like the bed FOOT/door LOWER cases —
     * otherwise the lone LOWER self-destructs, {@code build()} rolls it back and refunds the item,
     * the position re-queues, and the Builder loops forever ("keeps throwing the tall grass out").
     */
    public static void testTallGrassLowerPlacesBothHalves(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            // Ground for the plant to grow on (grass block is in SUPPORTS_VEGETATION).
            helper.getLevel().setBlock(abs.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);

            BlockState lower = Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
            SchematicBlockDefault s = schem(lower);
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.NO_REPLACE);
            assertTrue(placed, "build() must succeed for a tall grass lower half on a grass block");

            BlockState atLower = helper.getLevel().getBlockState(abs);
            BlockState atUpper = helper.getLevel().getBlockState(abs.above());
            assertTrue(atLower.getBlock() == Blocks.TALL_GRASS
                            && atLower.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER,
                    "lower half must be present at the build position");
            assertTrue(atUpper.getBlock() == Blocks.TALL_GRASS
                            && atUpper.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER,
                    "upper half must be placed atomically by the lower half's build — without it both halves self-destruct via updateShape and the Builder loops");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Guards the anti-loop linkage on the UPPER half. The UPPER schematic must (a) require the
     * position directly below it (the LOWER half) so it never queues an independent place task,
     * and (b) list no required items — the LOWER half's build places both halves with one item.
     * Without (a) the UPPER queues on its own, sets a lone half, and updateShape destroys it on
     * loop; without (b) the Builder consumes two plant items per plant.
     */
    public static void testTallGrassUpperHalfLinkedToLower(GameTestHelper helper) {
        try {
            BlockState upper = Blocks.TALL_GRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
            SchematicBlockDefault s = schem(upper);
            // Mirror what init() does for a freshly scanned schematic (the schem() helper sets
            // only blockState/placeBlock).
            s.addClassBasedRequiredBlockOffsets(upper.getBlock(), upper);

            assertTrue(s.getRequiredBlockOffsets().contains(new BlockPos(0, -1, 0)),
                    "upper half must require the lower position so it never queues its own place task (the loop)");
            assertTrue(s.computeRequiredItems().isEmpty(),
                    "upper half must list no items — the lower half's build places both halves with one item");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    /**
     * Sea pickle needs a sturdy block below AND its survival rules are sensitive to the
     * waterlogged state. Verifies the canSurvive gate runs AFTER the WATERLOGGED toggle in the
     * fluid-handling block — otherwise sea pickles in REPLACE mode would be evaluated against
     * the dry default and behave inconsistently with the post-placement world state.
     */
    public static void testSeaPickleOverWaterSurvives(GameTestHelper helper) {
        try {
            BlockPos local = new BlockPos(1, 2, 1);
            BlockPos abs = helper.absolutePos(local);
            helper.getLevel().setBlock(abs.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(abs, Blocks.WATER.defaultBlockState(), 3);

            SchematicBlockDefault s = schem(Blocks.SEA_PICKLE.defaultBlockState());
            boolean placed = s.build(helper.getLevel(), abs, EnumFluidHandlingMode.REPLACE);
            assertTrue(placed, "sea pickle on stone in water must place under REPLACE");

            BlockState after = helper.getLevel().getBlockState(abs);
            assertTrue(after.getBlock() == Blocks.SEA_PICKLE, "sea pickle should be present");
            helper.succeed();
        } catch (Throwable t) {
            helper.fail(t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }
}
