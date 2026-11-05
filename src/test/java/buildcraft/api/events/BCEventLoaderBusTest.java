/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.registry.EventBuildCraftReload;

import buildcraft.lib.script.ReloadableRegistryManager;

/**
 * BuildCraft's API events reach NeoForge-bus listeners on NeoForge, as they did before the platform layer moved them
 * onto {@link BCEventListeners}: an addon's {@code @SubscribeEvent} on {@link EventBuildCraftReload} or
 * {@link RobotEvent} registers, runs on every post (through the production posting paths), and its cancel is the one
 * BuildCraft reads, with NeoForge's own skip of later listeners that do not take cancelled events.
 */
public class BCEventLoaderBusTest {

    /** NeoForge-bus subscribers written the way an addon wrote them against the pre-platform API. */
    public static final class ReloadSubscriber {
        final List<String> reloadStages = new ArrayList<>();

        @SubscribeEvent
        public void beforeClear(EventBuildCraftReload.BeforeClear event) {
            reloadStages.add("BeforeClear");
        }

        @SubscribeEvent
        public void preLoad(EventBuildCraftReload.PreLoad event) {
            reloadStages.add("PreLoad");
        }

        @SubscribeEvent
        public void populateGson(EventBuildCraftReload.PopulateGson event) {
            Assertions.assertNotNull(event.gsonBuilder, "PopulateGson hands the builder the scripts load with");
            reloadStages.add("PopulateGson");
        }

        @SubscribeEvent
        public void postLoad(EventBuildCraftReload.PostLoad event) {
            reloadStages.add("PostLoad");
        }

        @SubscribeEvent
        public void finishLoad(EventBuildCraftReload.FinishLoad event) {
            reloadStages.add("FinishLoad");
        }
    }

    public static final class RobotSubscriber {
        final List<String> robotEvents = new ArrayList<>();
        boolean sawCancelledPlace;
        boolean skippedListenerRan;

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void vetoPlace(RobotEvent.Place event) {
            robotEvents.add("Place");
            event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void placeAfterVeto(RobotEvent.Place event) {
            skippedListenerRan = true;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void placeAfterVetoReceivingCancelled(RobotEvent.Place event) {
            sawCancelledPlace = event.isCanceled();
        }

        @SubscribeEvent
        public void interact(RobotEvent.Interact event) {
            robotEvents.add("Interact");
        }

        @SubscribeEvent
        public void dismantle(RobotEvent.Dismantle event) {
            robotEvents.add("Dismantle");
            event.setCanceled(true);
        }
    }

    @Test
    public void reloadStagesReachNeoForgeBusListeners() {
        ReloadSubscriber subscriber = new ReloadSubscriber();
        NeoForge.EVENT_BUS.register(subscriber);
        try {
            ReloadableRegistryManager.DATA_PACKS.reload(Collections.emptySet());
        } finally {
            NeoForge.EVENT_BUS.unregister(subscriber);
        }
        Assertions.assertEquals(List.of("BeforeClear", "PreLoad", "PopulateGson", "PostLoad", "FinishLoad"),
            subscriber.reloadStages);
    }

    @Test
    public void robotEventsReachNeoForgeBusListenersAndShareTheCancel() {
        RobotSubscriber subscriber = new RobotSubscriber();
        List<String> bcListOrder = new ArrayList<>();
        Consumer<RobotEvent.Place> bcListener = event -> bcListOrder.add(event.isCanceled() ? "cancelled" : "open");
        NeoForge.EVENT_BUS.register(subscriber);
        RobotEvent.Place.LISTENERS.addListener(bcListener);
        RobotEvent.Place place;
        RobotEvent.Interact interact;
        RobotEvent.Dismantle dismantle;
        try {
            // The calls ItemRobot and EntityRobot make.
            place = RobotEvent.Place.LISTENERS.post(new RobotEvent.Place(null, null));
            interact = RobotEvent.Interact.LISTENERS.post(new RobotEvent.Interact(null, null, null));
            dismantle = RobotEvent.Dismantle.LISTENERS.post(new RobotEvent.Dismantle(null, null));
        } finally {
            RobotEvent.Place.LISTENERS.removeListener(bcListener);
            NeoForge.EVENT_BUS.unregister(subscriber);
        }

        Assertions.assertEquals(List.of("Place", "Interact", "Dismantle"), subscriber.robotEvents);
        Assertions.assertEquals(List.of("open"), bcListOrder, "the BuildCraft list runs once, before the bus");
        Assertions.assertTrue(place.isCanceled(), "a NeoForge listener's veto is the one ItemRobot reads");
        Assertions.assertTrue(subscriber.sawCancelledPlace, "a receiveCanceled listener still runs and sees it");
        Assertions.assertFalse(subscriber.skippedListenerRan, "NeoForge skips later listeners after a cancel");
        Assertions.assertFalse(interact.isCanceled());
        Assertions.assertTrue(dismantle.isCanceled(), "a NeoForge listener's veto is the one EntityRobot reads");
    }
}
