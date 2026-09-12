/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.boards;

import java.util.Date;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.BlueprintBuilder;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileBuilder;
import buildcraft.core.BCCoreStatements;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.robotics.BCRoboticsEntities;
import buildcraft.robotics.BCRoboticsStatements;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.ai.AIRobotGotoBlock;
import buildcraft.robotics.ai.AIRobotGotoStationAndLoad;
import buildcraft.robotics.ai.AIRobotRecharge;
import buildcraft.robotics.entity.EntityRobot;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.tile.TilePipeHolder;

import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.TriggerWrapper;

/** Ph9's Builder-board tests. The rewritten board supplies Builder MACHINES (7.1.x fed construction
 *  markers, which no longer exist): find the closest needy one inside {@code MAX_RANGE_SQ}, fetch its
 *  first missing stack, fly there, insert into its resource inventory.
 *
 *  <p>Two tests: a synchronous state-machine walk (which delegate AI each situation starts — the
 *  sleep-when-nothing-needs, fetch-when-empty, energy-gate-when-low, deliver-when-carrying decisions),
 *  and the full autonomous E2E through the live tick loop (chest → wooden supply pipe → station →
 *  builder board robot → Builder machine's resource grid). The blueprint is built programmatically —
 *  one stone cell — exactly the way the Architect Table's scanner would have captured it. */
public class BuilderBoardTester {

    private static final long SEEDED_CHARGE = 5000L * MjAPI.MJ;

    // ---------- blueprint fixture ----------

    /** A one-cell stone blueprint: the palette entry a real scan would produce for the stone the arena
     *  places, data pointing at it, registered in the level's snapshot store, and the used-blueprint
     *  item stack that hands it to a Builder machine. */
    private static ItemStack makeSingleStoneBlueprint(ServerLevel level, BlockPos samplePos) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        ISchematicBlock schematic = SchematicBlockManager.getSchematicBlock(
                new SchematicBlockContext(level, samplePos, samplePos, stone, stone.getBlock()));
        if (schematic == null) {
            throw new IllegalStateException("the default schematic must resolve for plain stone");
        }

        Blueprint blueprint = new Blueprint();
        blueprint.size = new BlockPos(1, 1, 1);
        blueprint.facing = Direction.EAST;
        blueprint.offset = BlockPos.ZERO;
        blueprint.palette.add(schematic);
        blueprint.data = new int[] { 0 };
        blueprint.computeKey();
        GlobalSavedDataSnapshots.get(level).addSnapshot(blueprint);

        return BCBuildersItems.BLUEPRINT_USED.get().createUsedStack(
                new Snapshot.Header(blueprint.key, new UUID(0, 0), new Date(0), "ph9-test"));
    }

    /** Places a Builder machine facing EAST — so its single build cell (the position in front, where
     *  {@code FACING.getOpposite()} points) is one block WEST of the machine — and optionally loads the
     *  given snapshot item. */
    private static TileBuilder placeBuilder(GameTestHelper helper, BlockPos relPos, ItemStack snapshot) {
        helper.setBlock(relPos, BCBuildersBlocks.BUILDER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        //? if >=1.21.10 {
        TileBuilder tile = helper.getBlockEntity(relPos, TileBuilder.class);
        //?} else {
        /*TileBuilder tile = helper.getBlockEntity(relPos);*/
        //?}
        if (snapshot != null) {
            tile.setSnapshot(snapshot);
            // TWO manual ticks: the first runs the check sweep that classifies the lone cell TO_PLACE;
            // BlueprintBuilder.tick assembles remainingRequiredItems from the PREVIOUS tick's sweep
            // results, so the list only becomes visible on the second — that ordering is the machine's
            // own, not the test's.
            tile.tick();
            tile.tick();
        }
        return tile;
    }

    private static EntityRobot unaddedRobot(GameTestHelper helper, BlockPos relPos) {
        EntityRobot robot = new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(relPos));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        return robot;
    }

    // ---------- the state machine ----------

    /** Each situation's delegate AI, hand-driven exactly as {@code AIRobotMain} would call
     *  {@code board.update()}:
     *  <ul>
     *  <li>a snapshot-less Builder reports no demand ({@code needsItems == false} — asserted against the
     *      TILE rather than through a board update, because the shared game-test level may hold other
     *      tests' needy builders and the board searches every loaded one);</li>
     *  <li>a needy Builder and an empty hold → fetch the missing stack ({@code AIRobotGotoStationAndLoad});</li>
     *  <li>cargo aboard but the battery at/below {@code SAFETY_POWER} → the energy gate → recharge;</li>
     *  <li>cargo aboard and charge healthy → the delivery flight ({@code AIRobotGotoBlock}).</li>
     *  </ul>
     *  Phases 1–3 are race-safe: whichever needy builder the search finds, the delegate type is the
     *  same. Plus the {@code MAX_RANGE_SQ} pin: 7.1.x's registration verbatim. */
    public static void builderBoardStateMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos idleBuilderRel = new BlockPos(1, 2, 2);
        BlockPos needyBuilderRel = new BlockPos(4, 2, 2);

        // Phase 0 — a snapshot-less machine reports no demand at all.
        TileBuilder idleBuilder = placeBuilder(helper, idleBuilderRel, null);
        helper.assertTrue(!BoardRobotBuilder.needsItems(idleBuilder),
                "a snapshot-less builder needs nothing — the board must skip it");

        // Phase 1 — a needy machine: the empty robot starts a fetch.
        TileBuilder needyBuilder = placeBuilder(helper, needyBuilderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(2, 1, 2))));
        helper.assertTrue(BoardRobotBuilder.needsItems(needyBuilder),
                "a builder whose blueprint misses its stone reports demand");
        helper.assertTrue(needyBuilder.getBuilder() instanceof BlueprintBuilder,
                "sanity: the machine resolves a blueprint builder");

        EntityRobot robot = unaddedRobot(helper, new BlockPos(3, 2, 2));
        BoardRobotBuilder board = new BoardRobotBuilder(robot);
        board.update();
        AIRobot delegate = board.getDelegateAI();
        helper.assertTrue(delegate instanceof AIRobotGotoStationAndLoad,
                "an empty robot fetches the missing stone, got " + delegate);

        // Phase 2 — cargo aboard, battery drained to the safety floor: the energy gate sends it home.
        robot.setInventoryStack(0, new ItemStack(Items.STONE));
        robot.getBattery().extractPower(SEEDED_CHARGE - IRobotAccess.SAFETY_POWER,
                SEEDED_CHARGE - IRobotAccess.SAFETY_POWER);
        helper.assertTrue(robot.getPower() == IRobotAccess.SAFETY_POWER,
                "sanity: the battery sits exactly at the safety floor");
        board.update();
        delegate = board.getDelegateAI();
        // The recharge chain unwinds SYNCHRONOUSLY here (AIRobotSearchAndGotoStation finds no power
        // station in an empty arena and terminates inside start()), leaving no delegate — in play the
        // main loop would re-run the board next tick. The pin is that the energy gate HELD: the
        // low-battery robot did not start the delivery flight.
        helper.assertTrue(delegate == null || delegate instanceof AIRobotRecharge,
                "a loaded robot at/below SAFETY_POWER recharges (unwound with no power station), "
                        + "never flies, got " + delegate);

        // Phase 3 — battery healthy again: the delivery flight.
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        board.update();
        delegate = board.getDelegateAI();
        helper.assertTrue(delegate instanceof AIRobotGotoBlock,
                "a loaded, charged robot flies the delivery leg, got " + delegate);

        // The 7.1.x search radius, pinned: three 64-block radii squared.
        helper.assertTrue(BoardRobotBuilder.MAX_RANGE_SQ == 3 * 64 * 64,
                "MAX_RANGE_SQ is 7.1.x's registration verbatim");

        helper.succeed();
    }

    // ---------- the E2E ----------

    /** The full autonomous loop: a builder-board robot, a wooden supply pipe over a stone chest with an
     *  always-on Provide-Items gate, and a one-stone blueprint loaded in a Builder machine. The robot
     *  must fetch the stone and hand it to the machine's resource grid. */
    public static void builderBoardSuppliesBuilderMachine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos chestRel = new BlockPos(2, 1, 2);
        BlockPos robotRel = new BlockPos(2, 4, 2);
        BlockPos builderRel = new BlockPos(4, 2, 2);
        EntityArenaUtil.forceLoadEntityArena(helper, robotRel);

        helper.setBlock(pipeRel, BCTransportBlocks.PIPE_HOLDER.get());
        //? if >=1.21.10 {
        TilePipeHolder pipe = helper.getBlockEntity(pipeRel, TilePipeHolder.class);
        //?} else {
        /*TilePipeHolder pipe = helper.getBlockEntity(pipeRel);*/
        //?}
        pipe.onPlacedBy(null, new ItemStack(BCTransportItems.PIPE_WOOD_ITEM.get()));
        pipe.replacePluggable(Direction.UP,
                new RobotStationPluggable(buildcraft.robotics.BCRoboticsPlugs.robotStation, pipe, Direction.UP));
        PluggableGate gate = new PluggableGate(BCSiliconPlugs.gate, pipe, Direction.SOUTH,
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK,
                        EnumGateModifier.NO_MODIFIER));
        gate.logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic.statements[0].action.set(ActionWrapper.wrap(
                BCRoboticsStatements.ACTION_STATION_PROVIDE_ITEMS, null));
        pipe.replacePluggable(Direction.SOUTH, gate);
        gate.logic.resolveActions();

        helper.setBlock(chestRel, Blocks.CHEST);
        //? if >=1.21.10 {
        ChestBlockEntity chest = helper.getBlockEntity(chestRel, ChestBlockEntity.class);
        //?} else {
        /*ChestBlockEntity chest = helper.getBlockEntity(chestRel);*/
        //?}
        chest.setItem(0, new ItemStack(Items.STONE, 8));

        TileBuilder builder = placeBuilder(helper, builderRel,
                makeSingleStoneBlueprint(level, helper.absolutePos(new BlockPos(3, 1, 2))));

        EntityRobot robot = new EntityRobot(helper.getLevel(), BoardRobotBuilderNBT.INSTANCE);
        Vec3 pos = Vec3.atCenterOf(helper.absolutePos(robotRel));
        robot.setPos(pos.x, pos.y, pos.z);
        robot.getBattery().addPower(SEEDED_CHARGE, false);
        level.addFreshEntity(robot);

        EntityArenaUtil.tickUntil(helper, 400, () -> {
            for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
                if (!builder.getResource(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }, () -> {
                },
                "the builder-board robot must fetch the stone and deliver it to the machine's grid");

        for (int slot = 0; slot < TileBuilder.RESOURCE_SLOTS; slot++) {
            ItemStack stack = builder.getResource(slot);
            helper.assertTrue(stack.isEmpty() || stack.is(Items.STONE),
                    "the machine received the blueprint's stone, got " + stack);
        }
        robot.discard();
        helper.succeed();
    }
}
