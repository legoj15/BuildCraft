/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the stripes pipe laser direction reaching the client.
 * <p>
 * {@link PipeBehaviourStripes} stores its current laser direction on the server and ships it
 * to the client by riding the pipe's NBT in the BE update packet. The path is
 * {@code TilePipeHolder.handleUpdateTag → loadAdditional → Pipe.readFromNbt → behaviour.readFromNbt}.
 * If a stripes pipe ever lacks a {@code readFromNbt} override, the base no-op silently drops the
 * direction and the laser never renders on the client — the regression this test pins.
 * <p>
 * Constructor-time NBT loading (the cold-chunk path) is also covered, since
 * {@link PipeBehaviourStripes#PipeBehaviourStripes(buildcraft.api.transport.pipe.IPipe, CompoundTag)}
 * is what runs the first time the client materialises the pipe.
 */
public class PipeBehaviourStripesSyncTester {

    @Test
    public void readFromNbtRestoresDirection() {
        PipeBehaviourStripes source = new PipeBehaviourStripes(null);
        source.direction = Direction.DOWN;
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourStripes target = new PipeBehaviourStripes(null);
        Assertions.assertNull(target.direction, "fresh stripes behaviour starts with null direction");

        target.readFromNbt(nbt);
        Assertions.assertEquals(Direction.DOWN, target.direction,
                "readFromNbt must apply the synced direction so the client laser renders");
    }

    @Test
    public void readFromNbtClearsDirectionWhenAbsent() {
        PipeBehaviourStripes target = new PipeBehaviourStripes(null);
        target.direction = Direction.NORTH;

        CompoundTag emptyDirection = new CompoundTag();
        target.readFromNbt(emptyDirection);
        Assertions.assertNull(target.direction,
                "an in-place sync with no direction tag must clear a previously set direction");
    }

    @Test
    public void nbtConstructorRestoresDirection() {
        PipeBehaviourStripes source = new PipeBehaviourStripes(null);
        source.direction = Direction.EAST;
        CompoundTag nbt = source.writeToNbt();

        PipeBehaviourStripes target = new PipeBehaviourStripes(null, nbt);
        Assertions.assertEquals(Direction.EAST, target.direction,
                "the (IPipe, CompoundTag) constructor must read the direction on cold load");
    }
}
