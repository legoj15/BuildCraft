/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every value must come back intact after vanilla's menu-data packet, which carries each slot as a {@code short}
 *  (Fabric has no wider fallback). */
class WideContainerDataTester {

    @Test
    void valuesSurviveTheShortWideChannel() {
        long power = 375L * 1_000_000L;       // 375 MJ in micro-MJ, past 32 bits
        long output = -1_000_000L;            // negative longs sign-extend correctly
        int blocks = 262_144;                 // a 64^3 build, past 16 bits
        int fluidId = -1;                     // "empty tank" sentinel
        float heat = 137.5f;

        Decl server = new Decl(power, output, blocks, fluidId, heat, true, null);
        AtomicLong mirrored = new AtomicLong();
        Decl client = new Decl(0, 0, 0, 0, 0, false, mirrored);
        WideContainerData serverData = server.sync.build(true);
        WideContainerData clientData = client.sync.build(false);

        assertEquals(20f, client.heat.get(), "the client reads its declared initial value before any sync");

        for (int i = 0; i < serverData.getCount(); i++) {
            int sent = serverData.get(i);
            assertTrue(sent >= Short.MIN_VALUE && sent <= Short.MAX_VALUE, "slot " + i + " fits a short");
            clientData.set(i, (short) sent); // what ClientboundContainerSetDataPacket delivers
        }

        assertEquals(power, client.power.get());
        assertEquals(power, mirrored.get(), "the client sink sees the reassembled long");
        assertEquals(output, client.output.get());
        assertEquals(blocks, client.blocks.get());
        assertEquals(fluidId, client.fluidId.get());
        assertEquals(heat, client.heat.get());
        assertTrue(client.flag.get());
    }

    /** The same declarations on both sides, as a menu constructor makes them. */
    private static final class Decl {
        final WideContainerData.Builder sync = WideContainerData.builder();
        final WideContainerData.LongValue power;
        final WideContainerData.LongValue output;
        final WideContainerData.IntValue blocks;
        final WideContainerData.IntValue fluidId;
        final WideContainerData.FloatValue heat;
        final WideContainerData.BooleanValue flag;

        Decl(long power, long output, int blocks, int fluidId, float heat, boolean flag, AtomicLong sink) {
            this.power = sync.addLong(() -> power, sink == null ? null : sink::set);
            this.output = sync.addLong(() -> output);
            this.blocks = sync.addInt(() -> blocks);
            this.fluidId = sync.addInt(() -> fluidId);
            this.heat = sync.addFloat(() -> heat, 20f);
            this.flag = sync.addBoolean(() -> flag);
        }
    }
}
