package buildcraft.lib.misc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

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
public final class BreakEventCompat {

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

    private BreakEventCompat() {}

    /**
     * Asks whether a machine may break a block: posts the running version's block-break event with the given
     * context and returns {@code true} if it was NOT cancelled (i.e. the break is permitted by protection mods).
     * <p>
     * The event is a PROBE: nothing breaks, cancelled or not. BuildCraft's own break listeners must therefore not
     * treat it as a real break, and check {@link #isProbe}. Server thread only, like every caller.
     */
    public static boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
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
    public static boolean isProbe(BlockEvent event) {
        return event == probeInFlight;
    }

    /** A new, unposted break event of the running version's class. */
    public static BlockEvent create(Level level, BlockPos pos, BlockState state, Player player) {
        try {
            return (BlockEvent) CONSTRUCTOR.newInstance(level, pos, state, player);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("BuildCraft: failed to create block-break event", e);
        }
    }

    /** The player breaking the block, for a break event delivered by {@link #onBreak}. */
    public static Player playerOf(BlockEvent event) {
        try {
            return (Player) GET_PLAYER.invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("BuildCraft: failed to read the break event's player", e);
        }
    }

    /** Cancels a break event delivered by {@link #onBreak}: what a protection mod does to refuse a break. */
    public static void cancel(BlockEvent event) {
        ((ICancellableEvent) event).setCanceled(true);
    }

    /**
     * Registers a listener for the running version's block-break event, delivered as the common
     * {@link BlockEvent} supertype (which exposes {@code getLevel}/{@code getPos}/{@code getState}).
     *
     * @return a token for {@link #removeListener}
     */
    @SuppressWarnings("unchecked")
    public static Object onBreak(Consumer<BlockEvent> handler) {
        Consumer<Event> listener = event -> handler.accept((BlockEvent) event);
        NeoForge.EVENT_BUS.addListener((Class<Event>) EVENT_CLASS, listener);
        return listener;
    }

    /** Removes a listener added by {@link #onBreak}; {@code token} is what that call returned. */
    public static void removeListener(Object token) {
        NeoForge.EVENT_BUS.unregister(token);
    }
}
