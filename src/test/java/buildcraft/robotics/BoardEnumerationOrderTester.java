/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.robotics.item.ItemRobot;

/** Pins the board ENUMERATION order to what 1.7.10's "BuildCraft Robots" tab actually showed.
 *
 * <p>7.1.x registered its boards in cost tiers but stored them in a {@code HashMap}, so the tab enumerated
 * the JVM's bucket order — an accident, but the order players saw, and the order this port reproduces. The
 * order below was verified slot-by-slot against a live 1.7.10 client (2026-09-12: the lumberjack/planter
 * lead, the knight + leaf-cutter pairs, the carrier/picker/tank browns, the pump's hazard stripes, the
 * builder's gold chassis, delivery's flowers, then miner and bomber). The registry is insertion-ordered
 * ({@code LinkedHashMap}) and {@code BCRobotics} registers in exactly this sequence; both must stay in
 * step, and any future board joins this list at the position agreed for tab parity. */
public class BoardEnumerationOrderTester extends VanillaSetupBaseTester {

    private static final List<String> PINNED_1_7_10_ORDER = List.of(
            "buildcraftunofficial:boardRobotLumberjack",
            "buildcraftunofficial:boardRobotPlanter",
            "buildcraftunofficial:boardRobotButcher",
            "buildcraftunofficial:boardRobotShovelman",
            "buildcraftunofficial:boardRobotKnight",
            "buildcraftunofficial:boardRobotLeaveCutter",
            "buildcraftunofficial:boardRobotHarvester",
            "buildcraftunofficial:boardRobotStripes",
            "buildcraftunofficial:boardRobotCarrier",
            "buildcraftunofficial:boardRobotPicker",
            "buildcraftunofficial:boardRobotFluidCarrier",
            "buildcraftunofficial:boardRobotPump",
            "buildcraftunofficial:boardRobotBuilder",
            "buildcraftunofficial:boardRobotFarmer",
            "buildcraftunofficial:boardRobotDelivery",
            "buildcraftunofficial:boardRobotMiner",
            "buildcraftunofficial:boardRobotBomber");

    @Test
    public void theRegistryEnumeratesInThePinned1710Order() {
        Assertions.assertEquals(PINNED_1_7_10_ORDER, liveRobotBoardIds(),
                "the board registry must enumerate in 1.7.10's tab order (see BCRobotics' registration block)");
    }

    @Test
    public void theRobotsTabRowsFollowThePinnedOrder() {
        CreativeModeTab tab = RegistryUtilBC.getValue(BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(BCRobotics.MODID, "robots"));
        Assertions.assertNotNull(tab, "precondition: the robots tab is registered");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, false, VanillaRegistries.createLookup()));

        // The tab shows the empty chassis once, then a drained+full pair per board; collapse each pair and
        // keep first-seen order. Only ROBOT stacks count — the tab leads with machines and trails with the
        // standalone boards, which have their own sections.
        String emptyId = RedstoneBoardRegistry.instance.getEmptyRobotBoard().getID();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.getItem() != BCRoboticsItems.ROBOT.get()) {
                continue;
            }
            String id = ItemRobot.getBoardId(stack);
            if (id == null || emptyId.equals(id)) {
                Assertions.assertTrue(seen.isEmpty(),
                        "the empty-board chassis must lead the robot rows, not sit between them");
                continue;
            }
            seen.putIfAbsent(id, true);
        }

        Assertions.assertEquals(PINNED_1_7_10_ORDER, List.copyOf(seen.keySet()),
                "the robots tab's robot rows must follow the pinned 1.7.10 order");
    }

    private static List<String> liveRobotBoardIds() {
        RedstoneBoardRobotNBT empty = RedstoneBoardRegistry.instance.getEmptyRobotBoard();
        Assertions.assertNotNull(empty, "precondition: the empty robot board is seeded");
        List<String> ids = new ArrayList<>();
        for (RedstoneBoardNBT<?> nbt : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (nbt instanceof RedstoneBoardRobotNBT robot && robot != empty) {
                ids.add(robot.getID());
            }
        }
        return ids;
    }
}
