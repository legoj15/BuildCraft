/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Turns the connected sides of a block that keeps one boolean per face (the chute, the flood gate and the frame): the
 * boolean of each side moves to the side the rotation or mirror takes it to. A face the map does not hold (the flood
 * gate has no UP) neither gives nor receives a value, and UP and DOWN map to themselves under a rotation about Y and
 * a mirror along X or Z.
 */
public final class ConnectedSides {
    private ConnectedSides() {
    }

    /** {@code state} with each side of {@code sides} connected the way the side {@code rotation} turns into was. */
    public static BlockState rotate(BlockState state, Map<Direction, Property<Boolean>> sides, Rotation rotation) {
        return turn(state, sides, rotation::rotate);
    }

    /** {@code state} with each side of {@code sides} connected the way the side {@code mirror} swaps it with was. */
    public static BlockState mirror(BlockState state, Map<Direction, Property<Boolean>> sides, Mirror mirror) {
        return turn(state, sides, mirror::mirror);
    }

    private static BlockState turn(BlockState state, Map<Direction, Property<Boolean>> sides,
                                   UnaryOperator<Direction> turn) {
        // Every value is read from the incoming state before any is written, so two sides that trade places (a half
        // turn takes NORTH to SOUTH and SOUTH to NORTH) do not read what the other just set.
        Map<Direction, Boolean> connected = new EnumMap<>(Direction.class);
        for (Map.Entry<Direction, Property<Boolean>> side : sides.entrySet()) {
            connected.put(side.getKey(), state.getValue(side.getValue()));
        }
        BlockState turned = state;
        for (Map.Entry<Direction, Boolean> side : connected.entrySet()) {
            Property<Boolean> target = sides.get(turn.apply(side.getKey()));
            if (target != null) {
                turned = turned.setValue(target, side.getValue());
            }
        }
        return turned;
    }
}
