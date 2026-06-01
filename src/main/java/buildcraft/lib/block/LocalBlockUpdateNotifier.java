package buildcraft.lib.block;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Listens for block updates in a given level and notifies all registered
 * {@link ILocalBlockUpdateSubscriber}s whose update range covers the event
 * position.
 *
 * The 1.12.2 version hooked {@code IWorldEventListener.notifyBlockUpdate} for
 * a single catch-all signal. That API was removed in MC 1.21+, so this version
 * hooks NeoForge's {@link BlockEvent.EntityPlaceEvent}, the block-break event
 * (via {@link buildcraft.lib.misc.BreakEventCompat}), and {@link BlockEvent.NeighborNotifyEvent}. Together
 * those cover player placements/breaks and any {@code setBlock} call that
 * propagates neighbor updates. Consumers (e.g. {@link buildcraft.silicon.tile.TileLaser})
 * should still maintain a periodic rescan as a safety net for sources that
 * skip these events.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public class LocalBlockUpdateNotifier {

    private static final Map<Level, LocalBlockUpdateNotifier> instanceMap = new WeakHashMap<>();
    private final Set<ILocalBlockUpdateSubscriber> subscriberSet = new HashSet<>();

    private LocalBlockUpdateNotifier(Level world) {
    }

    public static LocalBlockUpdateNotifier instance(Level world) {
        return instanceMap.computeIfAbsent(world, LocalBlockUpdateNotifier::new);
    }

    public void registerSubscriberForUpdateNotifications(ILocalBlockUpdateSubscriber subscriber) {
        subscriberSet.add(subscriber);
    }

    public void removeSubscriberFromUpdateNotifications(ILocalBlockUpdateSubscriber subscriber) {
        subscriberSet.remove(subscriber);
    }

    public void notifySubscribersInRange(Level world, BlockPos eventPos, BlockState oldState, BlockState newState, int flags) {
        for (ILocalBlockUpdateSubscriber subscriber : subscriberSet) {
            BlockPos keyPos = subscriber.getSubscriberPos();
            int updateRange = subscriber.getUpdateRange();
            if (Math.abs(keyPos.getX() - eventPos.getX()) <= updateRange &&
                    Math.abs(keyPos.getY() - eventPos.getY()) <= updateRange &&
                    Math.abs(keyPos.getZ() - eventPos.getZ()) <= updateRange) {
                subscriber.setLevelUpdated(world, eventPos, oldState, newState, flags);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            dispatch(level, event.getPos(), event.getPlacedAgainst(), event.getPlacedBlock());
        }
    }

    // NOT @SubscribeEvent: the break-event class differs across 26.1.x, so this is registered
    // reflectively via BreakEventCompat (see registerBreakListener) against the common BlockEvent base.
    public static void onBlockBroken(BlockEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (accessor instanceof Level level && !level.isClientSide()) {
            dispatch(level, event.getPos(), event.getState(), level.getBlockState(event.getPos()));
        }
    }

    /** Registers {@link #onBlockBroken} for the running version's break event. Called once from BCCore. */
    public static void registerBreakListener() {
        buildcraft.lib.misc.BreakEventCompat.onBreak(LocalBlockUpdateNotifier::onBlockBroken);
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockState state = event.getState();
            dispatch(level, event.getPos(), state, state);
        }
    }

    private static void dispatch(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        LocalBlockUpdateNotifier notifier = instanceMap.get(level);
        if (notifier != null) {
            notifier.notifySubscribersInRange(level, pos, oldState, newState, 0);
        }
    }
}
