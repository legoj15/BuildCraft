/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.block.BlockFrame;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.block.BlockChute;
import buildcraft.factory.block.BlockFloodGate;

/**
 * A BuildCraft block with a facing turns when something rotates or mirrors its state: a blueprint built rotated, a
 * structure block, {@code /place} and another mod's rotation all go through {@code BlockState.rotate} and
 * {@code BlockState.mirror}, and vanilla's {@code BlockBehaviour} answers both with the state unchanged, so a block
 * that never overrides them keeps pointing the way it was captured. This walks every state of every BuildCraft block
 * and fails, naming block, property and rotation, for each direction property that does not follow
 * {@code Rotation.rotate} and {@code Mirror.mirror}. It also pins the three blocks that keep their connected sides in
 * one boolean per face (the chute, the flood gate and the frame), which turn by a different rule: the side that
 * was connected moves to the rotated side.
 * <p>
 * Breaks when a block gains a direction property, or loses its rotation override, without turning the property.
 */
public class BlockRotationGuardTester {
    private static final String NAMESPACE = "buildcraftunofficial";

    private static final Rotation[] TURNS = {
        Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90
    };

    /** Vanilla's context-free rotation of a state, which is what a blueprint (no level, no position) calls. */
    @SuppressWarnings("deprecation") // The level-aware form needs a level and a position a blueprint does not have.
    private static BlockState rotated(BlockState state, Rotation rotation) {
        return state.rotate(rotation);
    }

    private static List<Block> buildCraftBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (NAMESPACE.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
                blocks.add(block);
            }
        }
        Assertions.assertFalse(blocks.isEmpty(), "no BuildCraft blocks are registered");
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private static List<Property<Direction>> directionProperties(Block block) {
        List<Property<Direction>> properties = new ArrayList<>();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            if (property.getValueClass() == Direction.class) {
                properties.add((Property<Direction>) property);
            }
        }
        return properties;
    }

    /** One line per block, property and turn that left a direction where it was; the walked count is returned in
     *  {@code walked[0]} so a test cannot pass over an empty walk. */
    private static List<String> offenders(boolean mirror, int[] walked) {
        List<String> offenders = new ArrayList<>();
        for (Block block : buildCraftBlocks()) {
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            for (Property<Direction> property : directionProperties(block)) {
                walked[0]++;
                List<String> failed = new ArrayList<>();
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    Direction before = state.getValue(property);
                    if (mirror) {
                        for (Mirror each : new Mirror[] {Mirror.LEFT_RIGHT, Mirror.FRONT_BACK}) {
                            Direction expected = each.mirror(before);
                            Direction actual = state.mirror(each).getValue(property);
                            if (actual != expected && !failed.contains(each.name())) {
                                failed.add(each.name());
                            }
                        }
                    } else {
                        for (Rotation each : TURNS) {
                            Direction expected = each.rotate(before);
                            Direction actual = rotated(state, each).getValue(property);
                            if (actual != expected && !failed.contains(each.name())) {
                                failed.add(each.name());
                            }
                        }
                    }
                }
                if (!failed.isEmpty()) {
                    offenders.add(name + " property \"" + property.getName() + "\" is left as it was by " + failed);
                }
            }
        }
        return offenders;
    }

    @Test
    public void everyDirectionPropertyFollowsARotation() {
        int[] walked = new int[1];
        List<String> offenders = offenders(false, walked);
        Assertions.assertTrue(walked[0] > 10, "the walk reached only " + walked[0] + " direction properties");
        Assertions.assertEquals(List.of(), offenders,
            "a BuildCraft block's direction property does not turn with BlockState.rotate");
    }

    @Test
    public void everyDirectionPropertyFollowsAMirror() {
        int[] walked = new int[1];
        List<String> offenders = offenders(true, walked);
        Assertions.assertTrue(walked[0] > 10, "the walk reached only " + walked[0] + " direction properties");
        Assertions.assertEquals(List.of(), offenders,
            "a BuildCraft block's direction property does not turn with BlockState.mirror");
    }

    /** {@code state} with exactly the faces of {@code connected} set in {@code map}, every other face of it cleared. */
    private static BlockState connectedOnly(BlockState state, Map<Direction, Property<Boolean>> map,
                                            Direction... connected) {
        BlockState result = state;
        for (Map.Entry<Direction, Property<Boolean>> entry : map.entrySet()) {
            result = result.setValue(entry.getValue(), false);
        }
        for (Direction face : connected) {
            result = result.setValue(map.get(face), true);
        }
        return result;
    }

    /** The faces of {@code map} that {@code state} has connected, in Direction order. */
    private static List<Direction> connectedFaces(BlockState state, Map<Direction, Property<Boolean>> map) {
        List<Direction> faces = new ArrayList<>();
        for (Direction face : Direction.values()) {
            Property<Boolean> property = map.get(face);
            if (property != null && state.getValue(property)) {
                faces.add(face);
            }
        }
        return faces;
    }

    /** Notes in {@code problems} that {@code actual} is not {@code expected} for {@code what}. */
    private static void expect(List<String> problems, String what, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }

    @Test
    public void theThreeConnectedSideBlocksMoveEachConnectedSideToTheRotatedSide() {
        Block chute = BCFactoryBlocks.CHUTE.get();
        Block floodGate = BCFactoryBlocks.FLOOD_GATE.get();
        Block frame = BCBuildersBlocks.FRAME.get();
        Map<Direction, Property<Boolean>> chuteMap = BlockChute.CONNECTED_MAP;
        Map<Direction, Property<Boolean>> floodGateMap = BlockFloodGate.CONNECTED_MAP;
        Map<Direction, Property<Boolean>> frameMap = BlockFrame.CONNECTED_MAP;
        Assertions.assertFalse(floodGateMap.containsKey(Direction.UP), "the flood gate has no UP face");
        List<String> problems = new ArrayList<>();

        // Exactly NORTH connected: EAST after a quarter turn clockwise, the chute facing turned the same way.
        BlockState chuteNorth = connectedOnly(chute.defaultBlockState(), chuteMap, Direction.NORTH)
            .setValue(BlockChute.FACING, Direction.NORTH);
        BlockState chuteTurned = rotated(chuteNorth, Rotation.CLOCKWISE_90);
        expect(problems, "chute: faces connected after CLOCKWISE_90 from NORTH", List.of(Direction.EAST),
            connectedFaces(chuteTurned, chuteMap));
        expect(problems, "chute: facing after CLOCKWISE_90 from NORTH", Direction.EAST,
            chuteTurned.getValue(BlockChute.FACING));
        expect(problems, "flood gate: faces connected after CLOCKWISE_90 from NORTH", List.of(Direction.EAST),
            connectedFaces(rotated(connectedOnly(floodGate.defaultBlockState(), floodGateMap, Direction.NORTH),
                Rotation.CLOCKWISE_90), floodGateMap));
        expect(problems, "frame: faces connected after CLOCKWISE_90 from NORTH", List.of(Direction.EAST),
            connectedFaces(rotated(connectedOnly(frame.defaultBlockState(), frameMap, Direction.NORTH),
                Rotation.CLOCKWISE_90), frameMap));

        // A turn that swaps two faces reads each from the incoming state: NORTH alone becomes SOUTH alone, and the
        // vertical faces stay where they are.
        for (Object[] row : new Object[][] {
            {"chute", chute, chuteMap}, {"flood gate", floodGate, floodGateMap}, {"frame", frame, frameMap}}) {
            String name = (String) row[0];
            Block block = (Block) row[1];
            @SuppressWarnings("unchecked")
            Map<Direction, Property<Boolean>> map = (Map<Direction, Property<Boolean>>) row[2];
            BlockState state = connectedOnly(block.defaultBlockState(), map, Direction.NORTH, Direction.DOWN);
            expect(problems, name + ": NORTH and DOWN after CLOCKWISE_180", List.of(Direction.DOWN, Direction.SOUTH),
                connectedFaces(rotated(state, Rotation.CLOCKWISE_180), map));
            expect(problems, name + ": NORTH and DOWN after COUNTERCLOCKWISE_90", List.of(Direction.DOWN, Direction.WEST),
                connectedFaces(rotated(state, Rotation.COUNTERCLOCKWISE_90), map));
            expect(problems, name + ": NORTH and DOWN after LEFT_RIGHT", List.of(Direction.DOWN, Direction.SOUTH),
                connectedFaces(state.mirror(Mirror.LEFT_RIGHT), map));
            expect(problems, name + ": NORTH and DOWN after FRONT_BACK", List.of(Direction.DOWN, Direction.NORTH),
                connectedFaces(state.mirror(Mirror.FRONT_BACK), map));
            expect(problems, name + ": NORTH and DOWN after NONE", List.of(Direction.DOWN, Direction.NORTH),
                connectedFaces(rotated(state, Rotation.NONE), map));
        }
        Assertions.assertEquals(List.of(), problems,
            "a block that keeps its connected sides in one boolean per face leaves them where they were");
    }
}
