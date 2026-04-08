package buildcraft.lib.block;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Listens for BlockUpdates in a given world and notifies all registered IBlockUpdateSubscribers of the update provided
 * it was within the update range of the ILocalBlockUpdateSubscriber.
 *
 * Note: ILevelEventListener was removed in MC 1.21+. This class is stubbed out.
 * TODO: Re-implement using NeoForge event bus (@SubscribeEvent on BlockEvent or similar).
 */
public class LocalBlockUpdateNotifier {

    private static final Map<Level, LocalBlockUpdateNotifier> instanceMap = new WeakHashMap<>();
    private final Set<ILocalBlockUpdateSubscriber> subscriberSet = new HashSet<>();

    private LocalBlockUpdateNotifier(Level world) {
        // TODO: Register listener via NeoForge event bus for block update events
    }

    public static LocalBlockUpdateNotifier instance(Level world) {
        if (!instanceMap.containsKey(world)) {
            instanceMap.put(world, new LocalBlockUpdateNotifier(world));
        }
        return instanceMap.get(world);
    }

    public void registerSubscriberForUpdateNotifications(ILocalBlockUpdateSubscriber subscriber) {
        subscriberSet.add(subscriber);
    }

    public void removeSubscriberFromUpdateNotifications(ILocalBlockUpdateSubscriber subscriber) {
        subscriberSet.remove(subscriber);
    }

    // Called when a block updates - hook this via NeoForge events
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
}
