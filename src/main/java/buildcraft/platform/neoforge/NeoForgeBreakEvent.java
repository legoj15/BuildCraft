/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import buildcraft.platform.event.PlatformProtection;

/**
 * Cross-version shim for the "a block is being broken" event, which NeoForge restructured
 * between MC 26.1.1 and 26.1.2:
 * <ul>
 *   <li>26.1 / 26.1.1: {@code net.neoforged.neoforge.event.level.BlockEvent$BreakEvent}</li>
 *   <li>26.1.2+      : {@code net.neoforged.neoforge.event.level.block.BreakBlockEvent}</li>
 * </ul>
 * Both extend {@link BlockEvent} and implement {@link ICancellableEvent}, with an identical
 * {@code (Level, BlockPos, BlockState, Player)} constructor and a {@code getPlayer()} accessor &mdash; only
 * the class <i>name</i> differs. Resolving that class reflectively at load lets a single jar run on every
 * 26.1.x. The reflection is confined to the class lookup, the constructor and {@code getPlayer}; posting,
 * cancellation, and the other accessors all go through the shared {@link BlockEvent} /
 * {@link ICancellableEvent} supertypes, so they stay compile-checked.
 */
final class NeoForgeBreakEvent {

    private static final Class<?> EVENT_CLASS;
    private static final Constructor<?> CONSTRUCTOR;
    private static final Method GET_PLAYER;

    /** The probe {@link #canBreak} is posting right now (the innermost one, if a listener probes again), or null. */
    @Nullable
    private static Object probeInFlight;

    static {
        Class<?> cls;
        try {
            cls = Class.forName("net.neoforged.neoforge.event.level.block.BreakBlockEvent"); // 26.1.2+
        } catch (ClassNotFoundException newNameAbsent) {
            try {
                cls = Class.forName("net.neoforged.neoforge.event.level.BlockEvent$BreakEvent"); // 26.1 / 26.1.1
            } catch (ClassNotFoundException oldNameAbsent) {
                throw new IllegalStateException(
                        "BuildCraft: no block-break event class found (neither BreakBlockEvent nor BlockEvent.BreakEvent)",
                        oldNameAbsent);
            }
        }
        EVENT_CLASS = cls;
        try {
            CONSTRUCTOR = cls.getConstructor(Level.class, BlockPos.class, BlockState.class, Player.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "BuildCraft: break event " + cls.getName()
                            + " is missing the expected (Level, BlockPos, BlockState, Player) constructor", e);
        }
        try {
            GET_PLAYER = cls.getMethod("getPlayer");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("BuildCraft: break event " + cls.getName() + " has no getPlayer()", e);
        }
    }

    private NeoForgeBreakEvent() {}

    /**
     * Posts the running version's block-break event with the given context as a PROBE (nothing breaks, cancelled or
     * not) and returns {@code true} if it was NOT cancelled. BuildCraft's own break listeners check {@link #isProbe}.
     */
    static boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEvent event = create(level, pos, state, player);
        Object outer = probeInFlight;
        probeInFlight = event;
        try {
            NeoForge.EVENT_BUS.post((Event) event);
        } finally {
            probeInFlight = outer;
        }
        return !((ICancellableEvent) event).isCanceled();
    }

    /** True while {@code event} is a permission probe posted by {@link #canBreak} rather than a block breaking. */
    static boolean isProbe(Object event) {
        return event != null && event == probeInFlight;
    }

    /** A new, unposted break event of the running version's class. */
    private static BlockEvent create(Level level, BlockPos pos, BlockState state, Player player) {
        try {
            return (BlockEvent) CONSTRUCTOR.newInstance(level, pos, state, player);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("BuildCraft: failed to create block-break event", e);
        }
    }

    /** The player breaking the block. */
    private static Player playerOf(BlockEvent event) {
        try {
            return (Player) GET_PLAYER.invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("BuildCraft: failed to read the break event's player", e);
        }
    }

    /** Registers a listener for the running version's block-break event, at the default priority. */
    @SuppressWarnings("unchecked")
    static PlatformProtection.Registration onBreak(Consumer<PlatformProtection.BlockBreak> handler) {
        Consumer<Event> listener = event -> handler.accept(new View((BlockEvent) event));
        NeoForge.EVENT_BUS.addListener((Class<Event>) EVENT_CLASS, listener);
        return new PlatformProtection.Registration() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    NeoForge.EVENT_BUS.unregister(listener);
                }
            }
        };
    }

    /** A delivered break event, as a listener sees it. */
    private record View(BlockEvent event) implements PlatformProtection.BlockBreak {
        @Override
        public LevelAccessor level() {
            return event.getLevel();
        }

        @Override
        public BlockPos pos() {
            return event.getPos();
        }

        @Override
        public BlockState state() {
            return event.getState();
        }

        @Override
        public Player player() {
            return playerOf(event);
        }

        @Override
        public boolean isProbe() {
            return NeoForgeBreakEvent.isProbe(event);
        }

        @Override
        public void cancel() {
            ((ICancellableEvent) event).setCanceled(true);
        }
    }
}
//?}
