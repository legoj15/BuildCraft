package buildcraft.lib.block;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.Platform;
import buildcraft.platform.event.GameHooks;
import buildcraft.platform.event.HookOrder;
import buildcraft.platform.event.PlatformProtection;

/**
 * Listens for block updates in a given level and notifies all registered
 * {@link ILocalBlockUpdateSubscriber}s whose update range covers the event
 * position.
 *
 * The 1.12.2 version hooked {@code IWorldEventListener.notifyBlockUpdate} for
 * a single catch-all signal. That API was removed in MC 1.21+, so this version
 * hooks the block-placement event ({@link GameHooks#onBlockPlace}), the block-break event
 * (via {@link PlatformProtection#onBreak}), and the neighbour-notify event
 * ({@link GameHooks#onNeighborNotify}). Together those cover player placements/breaks and any {@code setBlock} call that
 * propagates neighbor updates. Consumers (e.g. {@link buildcraft.silicon.tile.TileLaser})
 * should still maintain a periodic rescan as a safety net for sources that
 * skip these events.
 */
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

    /** Forgets an unloading server level's notifier. The map key is weak, but the VALUE is not: a notifier holds
     *  its subscribers (laser block entities) strongly, and a block entity holds its level, so the entry keeps its
     *  own key reachable and a stopped server's whole world stayed pinned by this static map — a laser still
     *  registered at shutdown never runs {@code setRemoved}, so nothing else ever dropped it. A server stop posts
     *  this event once per level, after the final save. Server levels only: the map is server-side state and is
     *  not synchronised, so the client thread's own level unload must not touch it. */
    public static void onLevelUnload(LevelAccessor unloaded) {
        if (unloaded instanceof Level level && !level.isClientSide()) {
            instanceMap.remove(level);
        }
    }

    /** Registered last ({@link HookOrder#LATE}), so a placement a protection mod cancels (and the loader takes back)
     *  wakes nobody: a cancelled placement is not delivered here. */
    public static void onBlockPlaced(GameHooks.BlockPlace event) {
        // A machine's "may I place here?" probe (BlockUtil.canMachinePlace) places nothing; forwarding it would wake
        // nearby lasers once per Builder placement, as the break probe once did for every scanned block.
        if (event.isProbe()) {
            return;
        }
        if (event.level() instanceof Level level && !level.isClientSide()) {
            dispatch(level, event.pos(), event.placedAgainst(), event.placedBlock());
        }
    }

    // Registered through PlatformProtection#onBreak (see registerBreakListener): the break-event class differs
    // across 26.1.x.
    public static void onBlockBroken(PlatformProtection.BlockBreak event) {
        // A machine's "may I break this?" probe (BlockUtil.canMachineBreak) is a real break event that breaks
        // nothing; forwarding it made every per-block check in a quarry/builder scan wake nearby lasers.
        if (event.isProbe()) {
            return;
        }
        LevelAccessor accessor = event.level();
        if (accessor instanceof Level level && !level.isClientSide()) {
            dispatch(level, event.pos(), event.state(), level.getBlockState(event.pos()));
        }
    }

    /** Registers {@link #onBlockBroken} for the running version's break event. Called once from BCCore. */
    public static void registerBreakListener() {
        Platform.protection().onBreak(LocalBlockUpdateNotifier::onBlockBroken);
    }

    public static void onNeighborNotify(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide()) {
            dispatch(level, pos, state, state);
        }
    }

    /** Registers the unload, placement and neighbour-notify listeners. Called once from BCCore. */
    public static void registerHooks(GameHooks hooks) {
        hooks.onLevelUnload(LocalBlockUpdateNotifier::onLevelUnload);
        hooks.onBlockPlace(HookOrder.LATE, false, LocalBlockUpdateNotifier::onBlockPlaced);
        hooks.onNeighborNotify(LocalBlockUpdateNotifier::onNeighborNotify);
    }

    private static void dispatch(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        LocalBlockUpdateNotifier notifier = instanceMap.get(level);
        if (notifier != null) {
            notifier.notifySubscribersInRange(level, pos, oldState, newState, 0);
        }
    }
}
