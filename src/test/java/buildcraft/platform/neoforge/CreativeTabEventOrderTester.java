/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.transport.BCTransportCreativeTabs;
import buildcraft.transport.BCTransportItems;

/**
 * BuildCraft's pipes and plugs tabs are filled by {@code BuildCreativeModeTabContentsEvent} listeners at {@code LOW}
 * priority on its mod bus, as they were before the platform layer: an addition made at the default priority sits
 * before every BuildCraft item. Filling them from the tab's own {@code displayItems} generator instead (which NeoForge
 * runs before it posts the event) put BuildCraft's items first, and every other listener's after them.
 *
 * <p>The additions here come from a listener on BuildCraft's own mod bus, the one place a priority can be compared
 * against BuildCraft's listeners directly.
 */
public class CreativeTabEventOrderTester extends VanillaSetupBaseTester {

    /** A tab's contents with a stick added by a default priority listener on BuildCraft's bus. */
    private static List<ItemStack> buildWithStickAddedAtDefaultPriority(CreativeModeTab tab,
            ResourceKey<CreativeModeTab> key) {
        IEventBus bus = NeoForgeModBus.get();
        Consumer<BuildCreativeModeTabContentsEvent> addStick = event -> {
            if (event.getTabKey().equals(key)) {
                event.accept(Items.STICK);
            }
        };
        bus.addListener(addStick);
        try {
            tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                    FeatureFlags.DEFAULT_FLAGS, false, vanillaLookup()));
            return new ArrayList<>(tab.getDisplayItems());
        } finally {
            bus.unregister(addStick);
        }
    }

    private static int firstIndexForItem(List<ItemStack> items, Item item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void pipesTabFollowsDefaultPriorityAdditions() {
        List<ItemStack> items = buildWithStickAddedAtDefaultPriority(
                BCTransportCreativeTabs.PIPES_TAB.get(), BCTransportCreativeTabs.PIPES_TAB_KEY);
        int stick = firstIndexForItem(items, Items.STICK);
        int structure = firstIndexForItem(items, BCTransportItems.PIPE_STRUCTURE.get());
        Assertions.assertTrue(stick >= 0 && structure >= 0,
                "precondition: both the added stick (" + stick + ") and BuildCraft's structure pipe (" + structure
                        + ") are on the pipes tab");
        Assertions.assertTrue(stick < structure,
                "an addition at the default priority comes before BuildCraft's low priority items (stick " + stick
                        + ", structure pipe " + structure + ")");
    }

    @Test
    public void plugsTabFollowsDefaultPriorityAdditionsBeforeTransportAndSiliconAlike() {
        List<ItemStack> items = buildWithStickAddedAtDefaultPriority(
                BCTransportCreativeTabs.PLUGS_TAB.get(), BCTransportCreativeTabs.PLUGS_TAB_KEY);
        int stick = firstIndexForItem(items, Items.STICK);
        int blocker = firstIndexForItem(items, BCTransportItems.PLUG_BLOCKER.get());
        int gate = firstIndexForItem(items, BCSiliconItems.PLUG_GATE.get());
        Assertions.assertTrue(stick >= 0 && blocker >= 0 && gate >= 0,
                "precondition: the added stick (" + stick + "), transport's blocker (" + blocker + ") and silicon's "
                        + "gate (" + gate + ") are all on the plugs tab");
        Assertions.assertTrue(stick < blocker && blocker < gate,
                "the addition at the default priority comes first, then transport's plugs, then silicon's (stick "
                        + stick + ", blocker " + blocker + ", gate " + gate + ")");
    }
}
