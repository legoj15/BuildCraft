/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.wire;

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.transport.BCTransportPipes;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.plug.PluggableBlocker;

/**
 * {@link WireSystem#canWireConnect} must give one answer for a face, whichever of its two pipes is asked:
 * {@link SavedDataWireSystems}'s rebuild walks the wires from wherever it is seeded, and a link only one pipe counts
 * leaves a wire in no system (#251). The pipes here are stubs answering only what the rule reads, so the states the
 * game reaches only for a moment (one pipe reporting a connection its neighbour has not recomputed yet) are tried
 * like any other.
 */
public class WireLinkSymmetryTester {

    private static final boolean[] BOTH = { false, true };

    /** One pipe of the pair. Any question the wire rule is not expected to ask fails the test. */
    private static final class End {
        final Direction face;
        final boolean connected;
        final boolean blocked;
        final PipeDefinition definition;
        final DyeColor colour;
        End other;
        IPipe pipe;
        IPipeHolder holder;

        /** @param face the face this pipe shares with the other one
         *  @param connected whether this pipe reports that face connected
         *  @param blocked whether this pipe carries a blocking pluggable on that face */
        End(Direction face, boolean connected, boolean blocked, PipeDefinition definition, DyeColor colour) {
            this.face = face;
            this.connected = connected;
            this.blocked = blocked;
            this.definition = definition;
            this.colour = colour;
            pipe = stub(IPipe.class, (name, args) -> switch (name) {
                case "getHolder" -> holder;
                case "isConnected" -> args[0] == face && connected;
                case "getDefinition" -> definition;
                case "getColour" -> colour;
                default -> throw new UnsupportedOperationException("IPipe." + name);
            });
            holder = stub(IPipeHolder.class, (name, args) -> switch (name) {
                case "getPipe" -> pipe;
                case "getNeighbourPipe" -> args[0] == face ? other.pipe : null;
                case "getPluggable" -> args[0] == face && blocked
                    ? new PluggableBlocker(BCTransportPlugs.blocker, null, face) : null;
                default -> throw new UnsupportedOperationException("IPipeHolder." + name);
            });
        }

        @Override
        public String toString() {
            return (definition == BCTransportPipes.structure ? "structure" : "item") + " pipe, "
                + (connected ? "connected" : "not connected") + (blocked ? ", blocked" : "") + ", colour " + colour;
        }
    }

    private static <T> T stub(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (proxy, method, args) -> answers.apply(method.getName(), args)));
    }

    /** Asks both pipes, fails when they disagree, and returns the one answer. */
    private static boolean links(End west, End east) {
        west.other = east;
        east.other = west;
        boolean fromWest = WireSystem.canWireConnect(west.holder, Direction.EAST);
        boolean fromEast = WireSystem.canWireConnect(east.holder, Direction.WEST);
        Assertions.assertEquals(fromWest, fromEast,
            () -> "the west pipe (" + west + ") and the east pipe (" + east + ") disagree about the link");
        return fromWest;
    }

    private static End itemPipe(Direction face, boolean connected, boolean blocked) {
        return new End(face, connected, blocked, BCTransportPipes.cobbleItem, null);
    }

    @Test
    public void bothPipesGiveTheSameAnswerInEveryState() {
        PipeDefinition[] kinds = { BCTransportPipes.cobbleItem, BCTransportPipes.structure };
        DyeColor[] colours = { null, DyeColor.RED, DyeColor.BLUE };
        for (PipeDefinition westKind : kinds) {
            for (PipeDefinition eastKind : kinds) {
                for (DyeColor westColour : colours) {
                    for (DyeColor eastColour : colours) {
                        for (int state = 0; state < 16; state++) {
                            links(
                                new End(Direction.EAST, BOTH[state & 1], BOTH[(state >> 1) & 1], westKind,
                                    westColour),
                                new End(Direction.WEST, BOTH[(state >> 2) & 1], BOTH[(state >> 3) & 1], eastKind,
                                    eastColour));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void aPipeConnectionLinksOnlyWhenBothPipesReportIt() {
        Assertions.assertTrue(links(itemPipe(Direction.EAST, true, false), itemPipe(Direction.WEST, true, false)),
            "two pipes that both report the connection link their wires");
        // One pipe has recomputed its connections and its neighbour has not yet.
        Assertions.assertFalse(links(itemPipe(Direction.EAST, true, false), itemPipe(Direction.WEST, false, false)),
            "a connection only the west pipe reports links nothing");
        Assertions.assertFalse(links(itemPipe(Direction.EAST, false, false), itemPipe(Direction.WEST, true, false)),
            "a connection only the east pipe reports links nothing");
    }

    @Test
    public void aBlockingPluggableCutsTheLinkWhateverThePipesReport() {
        // A kinesis pipe beside a Power Adaptor: it reports the face connected, the adaptor's own pipe does not.
        Assertions.assertFalse(links(itemPipe(Direction.EAST, true, false), itemPipe(Direction.WEST, false, true)),
            "a pluggable on the east pipe cuts the link the west pipe's connection would make");
        Assertions.assertFalse(links(itemPipe(Direction.EAST, false, true), itemPipe(Direction.WEST, true, false)),
            "a pluggable on the west pipe cuts the link the east pipe's connection would make");
        Assertions.assertFalse(links(itemPipe(Direction.EAST, true, true), itemPipe(Direction.WEST, true, false)),
            "a pluggable cuts the link even when both pipes report the connection");
    }
}
