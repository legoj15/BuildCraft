/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.robotics.item.ItemRedstoneBoard;
import buildcraft.robotics.item.ItemRobot;
import buildcraft.silicon.BCSiliconCreativeTabs;

public class BCRoboticsCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BCRobotics.MODID);

    public static final ResourceKey<CreativeModeTab> ROBOTS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath(BCRobotics.MODID, "robots"));

    // 7.1.10's "BuildCraft Robots" tab held exactly robots, the robot station and the redstone
    // boards, in that order; the zone planner stayed on the main tab there by constructor accident
    // and was moved here deliberately. Icon is the plain board item, as upstream's was.
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ROBOTS_TAB =
            CREATIVE_MODE_TABS.register("robots", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.buildcraft.robots"))
                            .icon(() -> BCRoboticsItems.REDSTONE_BOARD.get().getDefaultInstance())
                            .withTabsBefore(BCSiliconCreativeTabs.FACADE_TAB_KEY)
                            .displayItems((parameters, output) -> {
                                // Zone Planner (deliberate deviation from 7.1.10)
                                output.accept(BCRoboticsItems.ZONE_PLANNER.get());
                                // Robots: the empty-board chassis at flat charge, then a drained and
                                // a full stack for every other registered board. When nothing has
                                // registered an empty board (a partially-initialised or stripped-down
                                // build) fall back to the bare stack, which reads as "empty board, no
                                // charge" anyway.
                                RedstoneBoardRegistry boardRegistry = RedstoneBoardRegistry.instance;
                                RedstoneBoardRobotNBT emptyBoard =
                                        boardRegistry == null ? null : boardRegistry.getEmptyRobotBoard();
                                if (emptyBoard == null) {
                                    output.accept(BCRoboticsItems.ROBOT.get());
                                } else {
                                    output.accept(ItemRobot.createRobotStack(emptyBoard.getID(), 0));
                                    for (RedstoneBoardNBT<?> boardNBT : boardRegistry.getAllBoardNBTs()) {
                                        if (boardNBT instanceof RedstoneBoardRobotNBT robotBoard
                                                && robotBoard != emptyBoard) {
                                            output.accept(ItemRobot.createRobotStack(robotBoard.getID(), 0));
                                            output.accept(ItemRobot.createRobotStack(robotBoard.getID(),
                                                    EntityRobotBase.MAX_POWER));
                                        }
                                    }
                                }
                                // Docking Station — 7.1.10 showed it only on this tab, never Pluggables
                                output.accept(BCRoboticsItems.ROBOT_STATION.get());
                                // Redstone Boards: the empty board, then each board as its own item
                                // (what the Ph7 programming table will consume). Skipped entirely in
                                // the bare-stack fallback above, matching the old main-tab loop.
                                if (emptyBoard != null) {
                                    output.accept(ItemRedstoneBoard.createStack(emptyBoard));
                                    for (RedstoneBoardNBT<?> boardNBT : boardRegistry.getAllBoardNBTs()) {
                                        if (boardNBT instanceof RedstoneBoardRobotNBT robotBoard
                                                && robotBoard != emptyBoard) {
                                            output.accept(ItemRedstoneBoard.createStack(robotBoard));
                                        }
                                    }
                                }
                            })
                            .build());

    public static void init(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
