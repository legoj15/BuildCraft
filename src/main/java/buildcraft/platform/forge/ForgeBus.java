/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;

//? if >=1.21.10 {
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import net.minecraftforge.eventbus.api.listener.EventListener;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.internal.Event;
//?} else {
/^import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
^///?}

import buildcraft.platform.event.HookOrder;

/^*
 * The one place BuildCraft's Forge code names an event bus. Forge has two generations of it and BuildCraft builds on both:
 * EventBus 7 (Forge for 1.21.6 and later: every event has its own bus, reached by its class, a cancellable event's
 * listener cancels by answering true, the mod bus is a {@code BusGroup}) and EventBus 6 (Forge 52, the 1.21.1 line: one
 * game bus, {@code MinecraftForge.EVENT_BUS}, one mod bus from the loading context, {@code setCanceled}, priorities as an
 * enum). Every listener and post in {@code buildcraft.platform.forge} goes through these methods, so a file's only
 * version arms are about the event classes themselves (records with accessors on EventBus 7, classes with getters and
 * public fields before), never about the bus.
 *
 * <ul>
 * <li>{@link #listen} is a plain listener. It never sees a cancelled event (EventBus 7 ends the chain at a cancel; EventBus 6
 * skips a listener that does not ask to receive cancelled events).</li>
 * <li>{@link #refuse} is a listener that may cancel: it answers true to cancel.</li>
 * <li>{@link #monitor} sees every event, cancelled or not, after every other listener, and cannot cancel (EventBus 7's
 * monitor stage; EventBus 6's {@code MONITOR} priority with cancelled events received).</li>
 * <li>{@link #post} posts on the game bus and answers whether the event ended cancelled.</li>
 * <li>{@link #listenMod} and {@link #register} are the mod bus ({@link ForgeModBus} holds it).</li>
 * </ul>
 *
 * <p>On EventBus 7 an event's bus is found by its class ({@code EventBus.create(type)} answers the bus the event's own
 * static {@code BUS} field holds: Forge's {@code BusGroupImpl#getOrCreateEventBus}), so the class is the one handle both
 * generations share. A subclass event (the multi-block placement) has a bus of its own there and is listened for apart.
 *^/
public final class ForgeBus {

    private ForgeBus() {}

    /^* A listener that can be taken off again ({@link #listenRemovable}). *^/
    public interface Registration {
        void remove();
    }

    //? if >=1.21.10 {
    /^* The EventBus 7 priority a {@link HookOrder} names (NeoForge: {@code HIGH}, {@code NORMAL}, {@code LOWEST}). *^/
    private static byte priority(HookOrder order) {
        return switch (order) {
            case EARLY -> Priority.HIGH;
            case DEFAULT -> Priority.NORMAL;
            case LATE -> Priority.LOWEST;
        };
    }

    public static <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        EventBus.create(type).addListener(listener);
    }

    public static <E extends Event> void listen(Class<E> type, HookOrder order, Consumer<E> listener) {
        EventBus.create(type).addListener(priority(order), listener);
    }

    /^*
     * A creative-tab contents listener after the others (NeoForge's {@code EventPriority.LOW}). The event is a game-bus
     * event on EventBus 7 and a mod-bus one on EventBus 6, so it has a method of its own.
     *^/
    public static void listenCreativeTabsLow(Consumer<BuildCreativeModeTabContentsEvent> listener) {
        BuildCreativeModeTabContentsEvent.BUS.addListener(Priority.LOW, listener);
    }

    public static <E extends Event> Registration listenRemovable(Class<E> type, Consumer<E> listener) {
        EventBus<E> bus = EventBus.create(type);
        EventListener registered = bus.addListener(listener);
        return () -> bus.removeListener(registered);
    }

    public static <E extends Event & Cancellable> void refuse(Class<E> type, Predicate<E> refuser) {
        CancellableEventBus.create(type).addListener(refuser);
    }

    public static <E extends Event & Cancellable> void refuse(Class<E> type, HookOrder order, Predicate<E> refuser) {
        CancellableEventBus.create(type).addListener(priority(order), refuser);
    }

    public static <E extends Event & Cancellable> Registration refuseRemovable(Class<E> type, Predicate<E> refuser) {
        CancellableEventBus<E> bus = CancellableEventBus.create(type);
        EventListener registered = bus.addListener(refuser);
        return () -> bus.removeListener(registered);
    }

    public static <E extends Event & Cancellable> void monitor(Class<E> type, Monitor<E> listener) {
        CancellableEventBus.create(type).addListener(listener::accept);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Event> boolean post(E event) {
        return EventBus.create((Class<E>) event.getClass()).post(event);
    }

    public static <E extends Event> void listenMod(Class<E> type, Consumer<E> listener) {
        EventBus.create(ForgeModBus.group(), type).addListener(listener);
    }

    public static void register(DeferredRegister<?> register) {
        register.register(ForgeModBus.group());
    }
    //?} else {
    /^private static EventPriority priority(HookOrder order) {
        return switch (order) {
            case EARLY -> EventPriority.HIGH;
            case DEFAULT -> EventPriority.NORMAL;
            case LATE -> EventPriority.LOWEST;
        };
    }

    public static <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
    }

    public static <E extends Event> void listen(Class<E> type, HookOrder order, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(priority(order), false, type, listener);
    }

    public static void listenCreativeTabsLow(Consumer<BuildCreativeModeTabContentsEvent> listener) {
        ForgeModBus.bus().addListener(EventPriority.LOW, false, BuildCreativeModeTabContentsEvent.class, listener);
    }

    // EventBus 6 keys a lambda listener by the Consumer object itself, so unregistering that object takes it off.
    public static <E extends Event> Registration listenRemovable(Class<E> type, Consumer<E> listener) {
        Consumer<E> registered = listener::accept;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, registered);
        return () -> MinecraftForge.EVENT_BUS.unregister(registered);
    }

    public static <E extends Event> void refuse(Class<E> type, Predicate<E> refuser) {
        refuse(type, HookOrder.DEFAULT, refuser);
    }

    public static <E extends Event> void refuse(Class<E> type, HookOrder order, Predicate<E> refuser) {
        MinecraftForge.EVENT_BUS.addListener(priority(order), false, type, event -> {
            if (refuser.test(event)) {
                event.setCanceled(true);
            }
        });
    }

    public static <E extends Event> Registration refuseRemovable(Class<E> type, Predicate<E> refuser) {
        Consumer<E> registered = event -> {
            if (refuser.test(event)) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, registered);
        return () -> MinecraftForge.EVENT_BUS.unregister(registered);
    }

    public static <E extends Event> void monitor(Class<E> type, Monitor<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.MONITOR, true, type,
                event -> listener.accept(event, event.isCanceled()));
    }

    public static <E extends Event> boolean post(E event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static <E extends Event> void listenMod(Class<E> type, Consumer<E> listener) {
        ForgeModBus.bus().addListener(EventPriority.NORMAL, false, type, listener);
    }

    public static void register(DeferredRegister<?> register) {
        register.register(ForgeModBus.bus());
    }

    // EventBus 6's capability attachment is one generic event, told apart by the attached object's class (Forge for 26.x
    // has a subclass per kind instead): a listener for one kind is a generic listener on that class.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <F> void attachCapabilities(Class<F> kind, Consumer<AttachCapabilitiesEvent<F>> listener) {
        MinecraftForge.EVENT_BUS.addGenericListener(kind, EventPriority.NORMAL, false,
                (Class<AttachCapabilitiesEvent<F>>) (Class) AttachCapabilitiesEvent.class, listener);
    }
    ^///?}

    /^* A monitor: the event, and whether it ended cancelled. *^/
    @FunctionalInterface
    public interface Monitor<E> {
        void accept(E event, boolean cancelled);
    }
}
*///?}
