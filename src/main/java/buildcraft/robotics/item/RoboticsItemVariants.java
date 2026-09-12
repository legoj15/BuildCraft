/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import buildcraft.robotics.boards.BoardRobotBomberNBT;
import buildcraft.robotics.boards.BoardRobotBuilderNBT;
import buildcraft.robotics.boards.BoardRobotButcherNBT;
import buildcraft.robotics.boards.BoardRobotCarrierNBT;
import buildcraft.robotics.boards.BoardRobotDeliveryNBT;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.boards.BoardRobotFarmerNBT;
import buildcraft.robotics.boards.BoardRobotFluidCarrierNBT;
import buildcraft.robotics.boards.BoardRobotHarvesterNBT;
import buildcraft.robotics.boards.BoardRobotKnightNBT;
import buildcraft.robotics.boards.BoardRobotLeaveCutterNBT;
import buildcraft.robotics.boards.BoardRobotLumberjackNBT;
import buildcraft.robotics.boards.BoardRobotMinerNBT;
import buildcraft.robotics.boards.BoardRobotPickerNBT;
import buildcraft.robotics.boards.BoardRobotPlanterNBT;
import buildcraft.robotics.boards.BoardRobotPumpNBT;
import buildcraft.robotics.boards.BoardRobotShovelmanNBT;
import buildcraft.robotics.boards.BoardRobotStripesNBT;

/**
 * The single id-to-icon mapping behind every per-board item icon, on every node.
 *
 * <p>7.1.x drew each robot and board stack with the icon its board kind asked for, resolved client-side
 * from the stack's board id. Modern Minecraft cannot do that with a static model, so the port dispatches
 * on the board id instead, through two node-dependent mechanisms that must never disagree:
 * <ul>
 * <li><strong>1.21.10+ (items/*.json definitions)</strong> — the {@code minecraft:component}/{@code
 * custom_data} condition chains in {@code items/robot.json} and {@code items/redstone_board.json},
 * keyed on the same board ids this class maps;</li>
 * <li><strong>1.21.1 (classic model overrides)</strong> — the {@code buildcraftunofficial:board} item
 * property (registered by {@code RoboticsBoardItemProperties}, which exists on that node only) whose
 * value the {@code overrides} blocks in {@code models/item/robot.json} and
 * {@code models/item/redstone_board.json} threshold-match on.</li>
 * </ul>
 *
 * <p>This class is the shared source of truth all three JSON files are written against, and
 * {@code RoboticsItemIconCoverageTester} pins every one of them to it — a board registered without an
 * entry here, or a JSON file drifting from this table, fails the build rather than shipping a
 * mismatched icon.
 *
 * <p>Robot icons reuse the entity skins ({@code textures/item/robot_<suffix>.png}, byte-copies of the
 * {@code textures/entity/} skins so they land on the stitched item atlas); board icons are the 7.1.x
 * tier colours, so kinds sharing a tier deliberately share an icon, exactly as in 7.1.x.
 */
public final class RoboticsItemVariants {

    private RoboticsItemVariants() {}

    /** The model name of the bare robot chassis (the empty board's icon and the unknown-id fallback). */
    public static final String ROBOT_BASE_MODEL = "robot";

    /** The model name of the base board model in {@code models/item/redstone_board.json} — the
     *  parent the tier overrides hang off. Never selected at runtime: 1.7.10 resolved unknown and
     *  data-less stacks to the empty board, and both dispatch mechanisms mirror that, so the only
     *  reachable board looks are the five tier chips. */
    public static final String BOARD_FALLBACK_MODEL = "redstone_board";

    /** Board tier colours, ascending. Index 0 ({@code clean}) is the empty board — and also what
     *  unknown ids and data-less stacks read as, mirroring 1.7.10's registry fallback. {@code yellow}
     *  is 7.1.x's fifth colour, worn by the Ph9 stripes and builder boards. */
    public static final String[] BOARD_TIER_ORDER = { "clean", "green", "blue", "red", "yellow" };

    /** The property both items are matched on in 1.21.1 model overrides. */
    public static final String BOARD_PROPERTY = "buildcraftunofficial:board";

    /** Board id &rarr; robot icon suffix; {@code ""} is the bare chassis. Iteration order IS the
     *  1.21.1 property value ({@link #robotProperty}). */
    private static final Map<String, String> ROBOT_VARIANTS;

    /** Board id &rarr; 7.1.x tier colour. Same keys as {@link #ROBOT_VARIANTS}. */
    private static final Map<String, String> BOARD_TIERS;

    static {
        Map<String, String> robots = new LinkedHashMap<>();
        robots.put(BoardRobotEmptyNBT.ID, "");
        robots.put(BoardRobotPickerNBT.ID, "picker");
        robots.put(BoardRobotCarrierNBT.ID, "carrier");
        robots.put(BoardRobotFluidCarrierNBT.ID, "fluid_carrier");
        robots.put(BoardRobotLumberjackNBT.ID, "lumberjack");
        robots.put(BoardRobotHarvesterNBT.ID, "harvester");
        robots.put(BoardRobotMinerNBT.ID, "miner");
        robots.put(BoardRobotPlanterNBT.ID, "planter");
        robots.put(BoardRobotFarmerNBT.ID, "farmer");
        robots.put(BoardRobotPumpNBT.ID, "pump");
        robots.put(BoardRobotButcherNBT.ID, "butcher");
        robots.put(BoardRobotKnightNBT.ID, "knight");
        robots.put(BoardRobotDeliveryNBT.ID, "delivery");
        robots.put(BoardRobotLeaveCutterNBT.ID, "leave_cutter");
        robots.put(BoardRobotShovelmanNBT.ID, "shovelman");
        robots.put(BoardRobotBomberNBT.ID, "bomber");
        robots.put(BoardRobotStripesNBT.ID, "stripes");
        robots.put(BoardRobotBuilderNBT.ID, "builder");

        Map<String, String> boards = new LinkedHashMap<>();
        boards.put(BoardRobotEmptyNBT.ID, "clean");
        boards.put(BoardRobotPickerNBT.ID, "green");
        boards.put(BoardRobotCarrierNBT.ID, "green");
        boards.put(BoardRobotFluidCarrierNBT.ID, "green");
        // 7.1.x's blue tier: 32000 RF boards.
        boards.put(BoardRobotLumberjackNBT.ID, "blue");
        boards.put(BoardRobotHarvesterNBT.ID, "blue");
        boards.put(BoardRobotMinerNBT.ID, "blue");
        boards.put(BoardRobotPlanterNBT.ID, "blue");
        boards.put(BoardRobotFarmerNBT.ID, "blue");
        boards.put(BoardRobotPumpNBT.ID, "blue");
        boards.put(BoardRobotButcherNBT.ID, "blue");
        boards.put(BoardRobotLeaveCutterNBT.ID, "blue");
        boards.put(BoardRobotShovelmanNBT.ID, "blue");
        boards.put(BoardRobotKnightNBT.ID, "red");
        boards.put(BoardRobotBomberNBT.ID, "red");
        // 7.1.x gave the delivery board the GREEN chip even at its 128000 RF price — kept verbatim.
        boards.put(BoardRobotDeliveryNBT.ID, "green");
        // 7.1.x's yellow tier: the stripes board at 128000 RF and the builder at 512000 RF — the
        // only two boards that ever wore it.
        boards.put(BoardRobotStripesNBT.ID, "yellow");
        boards.put(BoardRobotBuilderNBT.ID, "yellow");

        ROBOT_VARIANTS = Collections.unmodifiableMap(robots);
        BOARD_TIERS = Collections.unmodifiableMap(boards);
    }

    /** Every board id with an icon mapping, in property-value order (index 0 = the empty board). */
    public static Map<String, String> robotVariants() {
        return ROBOT_VARIANTS;
    }

    /** Board id &rarr; tier colour ({@code clean}/{@code green}/{@code blue}/{@code red}). */
    public static Map<String, String> boardTiers() {
        return BOARD_TIERS;
    }

    /** The robot icon suffix for a board id ({@code ""} = bare chassis, also for unknown ids). */
    public static String robotModelSuffix(String boardId) {
        return ROBOT_VARIANTS.getOrDefault(boardId, "");
    }

    /** The board tier colour for a board id, or null for an unknown id (which falls back to the
     *  bare flat-red board, as 7.1.x's spare "unknown" texture implied). */
    public static String boardTier(String boardId) {
        return BOARD_TIERS.get(boardId);
    }

    /** The {@code buildcraftunofficial:board} value for a robot stack: the board's index in
     *  {@link #robotVariants()} — 0.0 for the empty chassis and for any unknown id. */
    public static float robotProperty(String boardId) {
        int i = 0;
        for (String id : ROBOT_VARIANTS.keySet()) {
            if (id.equals(boardId)) {
                return (float) i;
            }
            i++;
        }
        return 0.0F;
    }

    /** The {@code buildcraftunofficial:board} value for a board stack: 1 + the tier's index in
     *  {@link #BOARD_TIER_ORDER}. Unknown ids and data-less stacks read as {@code clean} — 1.7.10
     *  resolved every board icon through the registry, whose fallback IS the empty board, so no
     *  stack ever had a look of its own besides the programmed kinds. */
    public static float boardProperty(String boardId) {
        String tier = BOARD_TIERS.getOrDefault(boardId, "clean");
        for (int i = 0; i < BOARD_TIER_ORDER.length; i++) {
            if (BOARD_TIER_ORDER[i].equals(tier)) {
                return (float) (i + 1);
            }
        }
        return 1.0F;
    }
}
