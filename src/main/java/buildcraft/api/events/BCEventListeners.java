/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.events;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The listeners of one BuildCraft event type. Each event class holds its list as a {@code LISTENERS} constant, and a
 * listener subscribes with {@code SomeEvent.LISTENERS.addListener(handler)}, on any loader.
 *
 * <p>Listeners run in the order they were added, on the posting thread. For an {@link ICancellableBCEvent}, the
 * listeners after the one that cancels it are skipped (a loader bus skips them too, unless they ask for cancelled
 * events); the poster reads {@link ICancellableBCEvent#isCanceled()} afterwards. Adding or removing a listener while
 * an event is being posted is safe and takes effect from the next post.
 *
 * <p>A {@link BCEvent} then goes on to the loader's own bus where it has one: on NeoForge {@link #post} posts it on
 * {@code NeoForge.EVENT_BUS} after these listeners, with the cancel state they left, so NeoForge listeners on
 * BuildCraft's events (an addon's {@code @SubscribeEvent}) run as they always did.
 *
 * <p>This is deliberately one typed list per event, not an event bus: there is no dispatch by type, no priority and
 * no subscriber scanning.
 */
public final class BCEventListeners<E> {
    private final List<Consumer<? super E>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<? super E> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes {@code listener} (the same instance that was added); a listener never added is ignored. */
    public void removeListener(Consumer<? super E> listener) {
        listeners.remove(listener);
    }

    /** Hands {@code event} to every listener in order and returns it, so the caller can read what they changed. */
    public <T extends E> T post(T event) {
        for (Consumer<? super E> listener : listeners) {
            if (event instanceof ICancellableBCEvent cancellable && cancellable.isCanceled()) {
                break;
            }
            listener.accept(event);
        }
        if (event instanceof BCEvent bcEvent) {
            BCEvent.postOnLoaderBus(bcEvent);
        }
        return event;
    }
}
