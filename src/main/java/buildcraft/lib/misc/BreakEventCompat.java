package buildcraft.lib.misc;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

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
 * {@code (Level, BlockPos, BlockState, Player)} constructor &mdash; only the class <i>name</i>
 * differs. Resolving that class reflectively at load lets a single jar run on every 26.1.x.
 * The reflection is confined to the class lookup + constructor; posting, cancellation, and the
 * accessors all go through the shared {@link BlockEvent} / {@link ICancellableEvent} supertypes,
 * so they stay compile-checked.
 */
public final class BreakEventCompat {

    private static final Class<?> EVENT_CLASS;
    private static final Constructor<?> CONSTRUCTOR;

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
    }

    private BreakEventCompat() {}

    /**
     * Posts the running version's block-break event with the given context and returns
     * {@code true} if it was NOT cancelled (i.e. the break is permitted by protection mods).
     */
    public static boolean canBreak(Level level, BlockPos pos, BlockState state, Player player) {
        try {
            Object event = CONSTRUCTOR.newInstance(level, pos, state, player);
            NeoForge.EVENT_BUS.post((Event) event);
            return !((ICancellableEvent) event).isCanceled();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("BuildCraft: failed to post block-break event", e);
        }
    }

    /**
     * Registers a listener for the running version's block-break event, delivered as the common
     * {@link BlockEvent} supertype (which exposes {@code getLevel}/{@code getPos}/{@code getState}).
     */
    @SuppressWarnings("unchecked")
    public static void onBreak(Consumer<BlockEvent> handler) {
        NeoForge.EVENT_BUS.addListener((Class<Event>) EVENT_CLASS, event -> handler.accept((BlockEvent) event));
    }
}
