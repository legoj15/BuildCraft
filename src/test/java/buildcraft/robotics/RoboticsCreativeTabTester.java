/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.BCCoreCreativeTabs;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.transport.BCTransportCreativeTabs;
import buildcraft.transport.BCTransportItems;

/**
 * Pins the dedicated robotics creative tab: registration, title, icon, tab-row position, and the
 * 7.1.10 content layout (robots, then the robot station, then the boards), plus the removals that
 * move implies — robotics items off the main tab and the robot station off the pluggables tab.
 *
 * <p>Tab contents are rebuilt the way the game does it, {@link CreativeModeTab#buildContents}, which
 * also fires the tab-contents event — so the plugs-tab assertions exercise the very listener the
 * robot station used to ride on, not a mock. The liveness precondition (the blocker must be present)
 * guards against that event path being silently dead in the FML-JUnit environment, which would make
 * the "station is gone" assertion vacuously true.
 */
public class RoboticsCreativeTabTester extends VanillaSetupBaseTester {

    private static final Identifier ROBOTS_TAB_ID = Identifier.fromNamespaceAndPath(BCRobotics.MODID, "robots");

    // ── Registration, title, icon, position ────────────────────────────────

    @Test
    public void robotsTabIsRegisteredWithItsTitleKey() {
        CreativeModeTab tab = robotsTab();
        Object contents = tab.getDisplayName().getContents();
        Assertions.assertTrue(contents instanceof TranslatableContents translatable
                        && "itemGroup.buildcraft.robots".equals(translatable.getKey()),
                "the robots tab must be registered with the title key itemGroup.buildcraft.robots");
    }

    @Test
    public void robotsTabIconIsThePlainRedstoneBoard() {
        Assertions.assertSame(BCRoboticsItems.REDSTONE_BOARD.get(), robotsTab().getIconItem().getItem(),
                "the robots tab icon is the plain redstone board item, as in 7.1.x");
        // 7.1.x resolved its (bare) icon stack through the board registry, landing on the empty
        // board's clean chip. The raw id must say so — a data-less default instance instead falls
        // through the icon dispatch to the 1.12.2-line fallback PCB, which is the "wrong second
        // empty board" bug this pins shut.
        Assertions.assertEquals(liveEmptyBoard().getID(), ItemRobot.getBoardId(robotsTab().getIconItem()),
                "the tab icon stack must carry the empty board's id, not be data-less");
    }

    @Test
    public void robotsTabSitsAfterTheFacadesTab() {
        Assertions.assertTrue(robotsTab().tabsBefore
                        .contains(Identifier.fromNamespaceAndPath(BCRobotics.MODID, "facades")),
                "the robots tab must order itself after the facades tab (last in the mod's tab row)");
    }

    // ── Content ────────────────────────────────────────────────────────────

    @Test
    public void robotsTabCarriesTheFullRoboticsCatalogueInLegacyOrder() {
        CreativeModeTab tab = robotsTab();
        tab.buildContents(displayParameters());
        List<ItemStack> items = new ArrayList<>(tab.getDisplayItems());

        RedstoneBoardRobotNBT emptyBoard = liveEmptyBoard();
        List<RedstoneBoardRobotNBT> boards = liveRobotBoards();

        int zonePlanner = firstIndexForItem(items, BCRoboticsItems.ZONE_PLANNER.get());
        int firstRobot = firstIndexForItem(items, BCRoboticsItems.ROBOT.get());
        int robotStation = firstIndexForItem(items, BCRoboticsItems.ROBOT_STATION.get());
        int firstBoard = firstIndexForItem(items, BCRoboticsItems.REDSTONE_BOARD.get());

        Assertions.assertTrue(zonePlanner >= 0, "the zone planner sits on the robots tab");
        Assertions.assertTrue(firstRobot >= 0, "the robot chassis stacks sit on the robots tab");
        Assertions.assertTrue(robotStation >= 0, "the robot station sits on the robots tab (7.1.10 showed it only there)");
        Assertions.assertTrue(firstBoard >= 0, "the standalone board stacks sit on the robots tab");

        // 7.1.10's relative order: robots, then the station, then the boards — with the zone
        // planner (the one approved deviation) leading the tab.
        Assertions.assertTrue(zonePlanner < firstRobot, "the zone planner leads the tab");
        Assertions.assertTrue(firstRobot < robotStation, "every robot comes before the robot station");
        Assertions.assertTrue(robotStation < firstBoard, "the boards trail the robot station");

        // The empty-board chassis at flat charge, then each registered board's drained and full robot.
        Assertions.assertTrue(containsRobot(items, emptyBoard.getID(), 0),
                "the empty-board chassis is present at zero charge");
        for (RedstoneBoardRobotNBT board : boards) {
            Assertions.assertTrue(containsRobot(items, board.getID(), 0),
                    board.getID() + " has a drained robot on the tab");
            Assertions.assertTrue(containsRobot(items, board.getID(), IRobotAccess.MAX_POWER),
                    board.getID() + " has a fully charged robot on the tab");
        }

        // The standalone boards: the empty board, then one per registered robot board.
        Assertions.assertTrue(containsBoard(items, emptyBoard), "the empty board item is present");
        for (RedstoneBoardRobotNBT board : boards) {
            Assertions.assertTrue(containsBoard(items, board), board.getID() + " has a board item on the tab");
        }

        // Exactly the catalogue, no strays: one chassis plus drained/full per board, and the empty
        // board plus one board per board.
        Assertions.assertEquals(1 + 2 * boards.size(), countForItem(items, BCRoboticsItems.ROBOT.get()),
                "the robot entries are exactly the empty chassis + drained/full per registered board");
        Assertions.assertEquals(1 + boards.size(), countForItem(items, BCRoboticsItems.REDSTONE_BOARD.get()),
                "the board entries are exactly the empty board + one per registered board");
    }

    @Test
    public void mainTabNoLongerCarriesRobotics() {
        CreativeModeTab main = BCCoreCreativeTabs.MAIN_TAB.get();
        main.buildContents(displayParameters());
        for (ItemStack stack : main.getDisplayItems()) {
            Item item = stack.getItem();
            Assertions.assertTrue(item != BCRoboticsItems.ZONE_PLANNER.get()
                            && item != BCRoboticsItems.ROBOT.get()
                            && item != BCRoboticsItems.REDSTONE_BOARD.get(),
                    "the main tab must no longer show robotics items (found " + stack + ")");
        }
    }

    @Test
    public void plugsTabKeepsItsPluggablesAndLosesTheRobotStation() {
        CreativeModeTab plugs = BCTransportCreativeTabs.PLUGS_TAB.get();
        plugs.buildContents(displayParameters());

        // Liveness: the plugs tab is fed entirely by the tab-contents event; if the blocker is
        // absent the build never reached those listeners and the assert below would be vacuous.
        Assertions.assertTrue(containsItem(plugs.getDisplayItems(), BCTransportItems.PLUG_BLOCKER.get()),
                "precondition: the event-fed plugs build is live");

        Assertions.assertFalse(containsItem(plugs.getDisplayItems(), BCRoboticsItems.ROBOT_STATION.get()),
                "the robot station moved off the pluggables tab");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static CreativeModeTab robotsTab() {
        // RegistryUtilBC hides the 1.21.1 Registry.get vs 1.21.10+ Registry.getValue lookup cliff.
        CreativeModeTab tab = RegistryUtilBC.getValue(BuiltInRegistries.CREATIVE_MODE_TAB, ROBOTS_TAB_ID);
        Assertions.assertNotNull(tab, "a CreativeModeTab must be registered under buildcraftunofficial:robots");
        return tab;
    }

    private static CreativeModeTab.ItemDisplayParameters displayParameters() {
        return new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, false, VanillaRegistries.createLookup());
    }

    private static RedstoneBoardRobotNBT liveEmptyBoard() {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        Assertions.assertNotNull(registry, "precondition: the live board registry is booted by the mod load");
        RedstoneBoardRobotNBT empty = registry.getEmptyRobotBoard();
        Assertions.assertNotNull(empty, "precondition: the empty robot board is seeded");
        return empty;
    }

    private static List<RedstoneBoardRobotNBT> liveRobotBoards() {
        List<RedstoneBoardRobotNBT> boards = new ArrayList<>();
        for (RedstoneBoardNBT<?> nbt : RedstoneBoardRegistry.instance.getAllBoardNBTs()) {
            if (nbt instanceof RedstoneBoardRobotNBT robot && robot != liveEmptyBoard()) {
                boards.add(robot);
            }
        }
        Assertions.assertFalse(boards.isEmpty(), "precondition: the Ph4/Ph5 boards are registered");
        return boards;
    }

    private static boolean containsItem(Iterable<ItemStack> items, Item item) {
        for (ItemStack stack : items) {
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static int firstIndexForItem(List<ItemStack> items, Item item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private static int countForItem(List<ItemStack> items, Item item) {
        int count = 0;
        for (ItemStack stack : items) {
            if (stack.getItem() == item) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsRobot(List<ItemStack> items, String boardId, long energy) {
        for (ItemStack stack : items) {
            if (stack.getItem() == BCRoboticsItems.ROBOT.get()
                    && boardId.equals(ItemRobot.getBoardId(stack))
                    && ItemRobot.getEnergy(stack) == energy) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBoard(List<ItemStack> items, RedstoneBoardNBT<?> board) {
        for (ItemStack stack : items) {
            if (stack.getItem() == BCRoboticsItems.REDSTONE_BOARD.get()
                    && ItemRedstoneBoard.getBoardNBT(stack) == board) {
                return true;
            }
        }
        return false;
    }
}
