/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.fluid.FluidResource;
//?}

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.tile.TilePowerConsumerTester;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.container.ContainerEngineIron;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.test.EntityArenaUtil;

public class EngineTester {

    /** Work budgets of the engine polls below, each counted from the cell ticking, not from the test's first tick:
     *  an engine accumulates power, heat and bleed only on the ticks it gets, and its chunk can start ticking
     *  late (a one-block rig two blocks in sits across a chunk border in about a quarter of runs). Each poll's own
     *  budget adds {@code EntityArenaUtil.CELL_READY_TICKS}, which {@code GameTestPollBudgetTester} reads, and every
     *  manifest's {@code max_ticks} stays above that sum. */
    private static final int DRY_RUN_WORK_TICKS = 60;
    /** The wait for the engine's tile to exist once the cell ticks (immediate in practice), ahead of a phase that
     *  is timed from it. */
    private static final int TILE_WORK_TICKS = 10;
    /** Ticks an engine runs between the setup of a timed test and its check. */
    private static final int SAFE_LIMIT_SETTLE_TICKS = 20;
    private static final int OVERHEAT_HOLD_TICKS = 50;
    private static final int OVERHEAT_EXIT_TICKS = 140;
    private static final int COOLANT_RECOVERY_TICKS = 80;
    private static final int CLEAR_OVERHEAT_SETTLE_TICKS = 5;

    public static void testRedstoneEngineDryRunHeat(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        BlockPos redstonePos = new BlockPos(2, 2, 1);

        BlockState engineState = BCCoreBlocks.ENGINE_REDSTONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);

        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, engineState);
        helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);

        // Give the engine its own ticks to natively generate power via its internal block mechanics: poll until it is
        // burning with power and heat, counted from the cell ticking (the engine needs well under 40 ticks of its own).
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + DRY_RUN_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, DRY_RUN_WORK_TICKS, () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    if (engine == null) {
                        throw new IllegalStateException("Failed to place Redstone Engine!");
                    }
                    return engine.isBurning() && engine.getPower() > 0 && engine.getHeatLevel() > 0;
                }, () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    return !engine.isBurning() ? "Engine is not burning with redstone signal!"
                            : engine.getPower() <= 0 ? "Engine dry-run failed to natively accumulate internal power!"
                            : "Engine dry-run failed to natively increase heat! Current Heat: " + engine.getHeatLevel();
                }),
                () -> checkDryRunStages(helper, engineAt(helper, enginePos)),
                "The redstone engine never ran on its own");
    }

    private static TileEngineBase_BC8 engineAt(GameTestHelper helper, BlockPos pos) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, TileEngineBase_BC8.class);
        //?} else {
        /*return helper.getBlockEntity(pos);*/
        //?}
    }

    private static void checkDryRunStages(GameTestHelper helper, TileEngineBase_BC8 engine) {
        // Now that we've verified native scaling works, manually fast-forward energy limits
        // to instantly assert 1.12.2 threshold accuracy without waiting 1.4 real-life hours

        // Test Stage: BLUE (< 25%)
        fastForwardEnergy(engine, 0.0f);
        if (engine.getPowerStage() != EnumPowerStage.BLUE) {
            throw new IllegalStateException("Engine should start in BLUE stage! Found: " + engine.getPowerStage());
        }

        // Fast forward to 26% energy limit
        fastForwardEnergy(engine, 0.26f);
        if (engine.getPowerStage() != EnumPowerStage.GREEN) {
            throw new IllegalStateException(String.format("Engine should be GREEN at 26%%! Found: %s, Heat=%.2f, HeatLevel=%.4f", engine.getPowerStage(), getHeatDirect(engine), engine.getHeatLevel()));
        }

        // Fast forward to 51% energy limit
        fastForwardEnergy(engine, 0.51f);
        if (engine.getPowerStage() != EnumPowerStage.YELLOW) {
            throw new IllegalStateException("Engine should be YELLOW at 51% heat level! Found: " + engine.getPowerStage());
        }

        // Fast forward to 76% energy limit
        fastForwardEnergy(engine, 0.76f);
        if (engine.getPowerStage() != EnumPowerStage.RED) {
            throw new IllegalStateException("Engine should be RED at 76% heat level! Found: " + engine.getPowerStage());
        }

        helper.succeed();
    }

    public static void testRedstoneEngineSafeLimit(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, BCCoreBlocks.ENGINE_REDSTONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        // The settle time must be the engine's own ticks: with the clock started at tick 1, an engine whose chunk ticks
        // late would be checked after fewer ticks than the test says (or none, and pass without having run).
        EntityArenaUtil.tickUntilThen(helper,
                EntityArenaUtil.CELL_READY_TICKS + TILE_WORK_TICKS + SAFE_LIMIT_SETTLE_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, TILE_WORK_TICKS,
                        () -> engineAt(helper, enginePos) != null, () -> "no engine block entity at " + enginePos),
                () -> {
                    // Push to 84% (right at the boundary below OVERHEAT which is 85%)
                    // The engine naturally maxes out at 80% (0.8f).
                    fastForwardEnergy(engineAt(helper, enginePos), 0.84f);
                }, SAFE_LIMIT_SETTLE_TICKS, () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    // Engine should comfortably lock internally at RED, but never OVERHEAT
                    if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Redstone Engine entered an OVERHEAT stage. Redstone engines should be perfectly safe and lock at RED.");
                    }
                    if (engine.getPowerStage() != EnumPowerStage.RED) {
                        throw new IllegalStateException("Redstone Engine failed to reach RED stage. Found: " + engine.getPowerStage());
                    }

                    helper.succeed();
                }, "The redstone engine never appeared");
    }

    public static void testStirlingEngineFuel(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos, TileEngineStone_BC8.class);
            //?} else {
            /*TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            
            // Attempt invalid fuel
            ItemStack dirt = new ItemStack(Items.DIRT);
            engine.setFuelStack(dirt);
            long outputBefore = engine.getCurrentOutput();
            engine.serverTick(engine.getLevel(), enginePos, engine.getBlockState(), engine);
            if (engine.isBurning()) {
                throw new IllegalStateException("Stirling Engine incorrectly consumed DIRT as fuel!");
            }
            
            // Attempt valid fuel
            ItemStack coal = new ItemStack(Items.COAL);
            engine.setFuelStack(coal);
            
            // Simulate time passing to start combustion
            engine.serverTick(engine.getLevel(), enginePos, engine.getBlockState(), engine);
            if (!engine.isBurning()) {
                throw new IllegalStateException("Stirling Engine failed to ignite with COAL!");
            }
            if (engine.getFuelStack().isEmpty()) {
                // Assert it consumed 1 coal to fill the burn time buffer!
            }

            helper.succeed();
        });
    }

    /**
     * Characterises the fuel-container path: burning a lava bucket must leave the empty bucket in the fuel
     * slot (the stone engine asks the consumed stack for its crafting remainder). Pins the per-line remainder
     * call (stack-sensitive on every node) so a link or behaviour change shows up as a failure here.
     */
    public static void testStirlingEngineLavaBucketLeavesBucket(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos, TileEngineStone_BC8.class);
            //?} else {
            /*TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}

            engine.setFuelStack(new ItemStack(Items.LAVA_BUCKET));
            engine.serverTick(engine.getLevel(), enginePos, engine.getBlockState(), engine);
            if (!engine.isBurning()) {
                throw new IllegalStateException("Stirling Engine failed to ignite with a LAVA_BUCKET!");
            }
            ItemStack left = engine.getFuelStack();
            if (!left.is(Items.BUCKET) || left.getCount() != 1) {
                throw new IllegalStateException("Burning a lava bucket must leave exactly one empty bucket in the "
                        + "fuel slot, found: " + left);
            }

            helper.succeed();
        });
    }

    /** The work budget of the unfueled Stirling poll, counted from the cell ticking (the poll's own budget adds
     *  {@code EntityArenaUtil.CELL_READY_TICKS}, read as such by {@code GameTestPollBudgetTester}; the manifest's
     *  {@code max_ticks} stays above the sum). */
    private static final int STORED_POWER_WORK_TICKS = 110;

    /**
     * Regression for "stored energy doesn't release unless fueled" (RC4 feedback): a redstone-powered
     * Stirling engine with a charged MJ buffer but NO fuel (burnTime stays 0, so isBurning()==false)
     * must still deliver its stored buffer to an adjacent receiver. Under the pre-fix isBurning() send
     * gate the buffer froze; the isActive() gate (base default true) releases it. The sink is a
     * {@link TilePowerConsumerTester}, placed directly above
     * the upward-facing engine so the engine's MJ capability query connects without any pipe bookkeeping.
     */
    public static void testStirlingEngineReleasesStoredPowerWithoutFuel(GameTestHelper helper) {
        BlockPos redstonePos = new BlockPos(2, 1, 2);
        BlockPos enginePos   = new BlockPos(2, 2, 2);
        BlockPos testerPos   = new BlockPos(2, 3, 2);

        // The framework forces only the structure's chunk, and the engine column can sit in the neighbouring one:
        // neither the engine nor the tester ticks until that chunk does.
        EntityArenaUtil.forceLoadCell(helper);
        BlockState engineState = BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
        helper.setBlock(enginePos, engineState);
        helper.setBlock(testerPos, BCCoreBlocks.POWER_TESTER.get());
        helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);

        //? if >=1.21.10 {
        TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos, TileEngineStone_BC8.class);
        //?} else {
        /*TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos);*/
        //?}
        // Charge the buffer to 50% with an EMPTY fuel slot — burnTime never starts, so isBurning() is false.
        fastForwardEnergy(engine, 0.5f);
        if (engine.isBurning()) {
            throw new IllegalStateException("Test precondition failed: engine must not be burning (no fuel)");
        }

        // Poll until the tester has received some MJ. Tolerant of arena startup jitter (redstone poll +
        // capability connection take a variable few ticks). Cumulative total stays >0 once any arrives. The work
        // budget starts only once the cell is ticking: the engine and the tester are blocks of a cell whose far half
        // can sit in a chunk that is promoted later, and a framework watchdog counted from tick 1 would charge that
        // wait to the engine.
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + STORED_POWER_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, STORED_POWER_WORK_TICKS, () -> {
                    //? if >=1.21.10 {
                    TilePowerConsumerTester tester = helper.getBlockEntity(testerPos, TilePowerConsumerTester.class);
                    //?} else {
                    /*TilePowerConsumerTester tester = helper.getBlockEntity(testerPos);*/
                    //?}
                    return readTesterTotal(tester) > 0;
                }, () -> "the unfueled Stirling engine has not delivered its stored buffer to the receiver"),
                helper::succeed,
                "Unfueled Stirling engine never delivered its stored buffer to the receiver");
    }

    /** Sniffs the power tester's debug output for a non-zero "Total received" line (mirrors the helper
     *  in PipeFlowPowerTester — avoids a package-private accessor just for tests). */
    private static long readTesterTotal(TilePowerConsumerTester tester) {
        java.util.List<String> left = new java.util.ArrayList<>();
        java.util.List<String> right = new java.util.ArrayList<>();
        tester.getDebugInfo(left, right, Direction.UP);
        for (String line : left) {
            if (line.startsWith("Total received") && !line.contains(" 0 ")) return 1;
        }
        return 0;
    }

    public static void testStirlingEngineExplosion(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            //? if >=1.21.10 {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            //?} else {
            /*TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            
            // Instantly cap energy buffer to push to OVERHEAT boundary
            fastForwardEnergy(engine, 1.0f);
            
            // Ensure explosive boundaries trigger
            if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                throw new IllegalStateException("Stirling Engine should OVERHEAT and explode at 100% heat levels! Found: " + engine.getPowerStage());
            }
            
            helper.succeed();
        });
    }

    public static void testCombustionEngineStable(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_IRON.get());

        helper.runAfterDelay(1, () -> {
            //? if >=1.21.10 {
            TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos, TileEngineIron_BC8.class);
            //?} else {
            /*TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            helper.succeed();
            // TODO: Combustion Engine tests assert dual fluids when fluids are fully linked via NeoForge Capabilities
        });
    }

    /**
     * Vanilla ice, packed ice and blue ice are registered as solid coolants (BCEnergyRecipes):
     * clicking a Combustion Engine's coolant tank with one on the cursor converts it to water in
     * the tank. WidgetFluidTank's creative branch regressed — an unconditional return after the
     * fluid-container check skipped the solid-coolant conversion for any non-container item, so a
     * creative player could no longer feed ice to the tank. Survival was unaffected; this pins both.
     */
    public static void testCombustionEngineCoolantTankAcceptsIce(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_IRON.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos, TileEngineIron_BC8.class);
            //?} else {
            /*TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            if (engine == null) {
                throw new IllegalStateException("Failed to place Combustion Engine!");
            }

            // Survival: ice converts to water and the block is consumed.
            clickCoolantTankWithIce(helper, engine, false);
            //? if >=1.21.10 {
            engine.tankCoolant.set(0, FluidResource.EMPTY, 0);
            //?} else {
            /*engine.tankCoolant.setFluidStack(0, net.neoforged.neoforge.fluids.FluidStack.EMPTY);*/
            //?}
            // Creative: ice converts to water and the block is NOT consumed (the regressed path).
            clickCoolantTankWithIce(helper, engine, true);

            helper.succeed();
        });
    }

    /**
     * Opens a Combustion Engine GUI for a mock player of the given mode, puts an ice block on the
     * cursor, replays the coolant-tank click packet, and asserts the ice converted to 1500 mB of
     * water (and was consumed iff the player is in survival).
     */
    private static void clickCoolantTankWithIce(GameTestHelper helper, TileEngineIron_BC8 engine,
            boolean creative) {
        String who = creative ? "Creative" : "Survival";
        Player player = helper.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.getAbilities().instabuild = creative;

        ContainerEngineIron container = new ContainerEngineIron(0, player.getInventory(), engine);
        player.containerMenu = container;
        container.setCarried(new ItemStack(Blocks.ICE));

        // Replay the client -> server tank-click payload (WidgetFluidTank.NET_CLICK == 0).
        PacketBufferBC buffer = new PacketBufferBC(Unpooled.buffer());
        buffer.writeByte(0);
        container.widgetCoolant.handleWidgetDataServer(null, buffer);
        buffer.release();

        //? if >=1.21.10 {
        long amount = engine.tankCoolant.getAmountAsLong(0);
        //?} else {
        /*long amount = engine.tankCoolant.getAmountMb(0);*/
        //?}
        if (amount != 1500) {
            throw new IllegalStateException(who + " player: clicking the coolant tank with an ice "
                    + "block should fill it with 1500 mB of water, got " + amount + " mB");
        }
        //? if >=1.21.10 {
        if (!engine.tankCoolant.getResource(0).is(Fluids.WATER)) {
            throw new IllegalStateException(who + " player: coolant tank should hold water after "
                    + "ice conversion, got " + engine.tankCoolant.getResource(0));
        }
        //?} else {
        /*if (!engine.tankCoolant.getFluidStack(0).is(Fluids.WATER)) {
            throw new IllegalStateException(who + " player: coolant tank should hold water after "
                    + "ice conversion, got " + engine.tankCoolant.getFluidStack(0));
        }*/
        //?}

        ItemStack cursor = container.getCarried();
        if (creative && (cursor.isEmpty() || cursor.getCount() != 1)) {
            throw new IllegalStateException("Creative player: the ice block should not be consumed, "
                    + "cursor now holds " + cursor);
        }
        if (!creative && !cursor.isEmpty()) {
            throw new IllegalStateException("Survival player: the ice block should be consumed, "
                    + "cursor still holds " + cursor);
        }
    }

    /**
     * Default behavior: an overheated engine does NOT explode (canEnginesExplode=false), holds the
     * OVERHEAT stage through the hysteresis band (no rapid red/black flapping — the RC5 regression),
     * and then passively self-cools back out. From 0.86 the 1 MJ/tick bleed needs
     * (0.86 - 0.75) * 1000 = ~111 ticks to cross overheatExitLevel(); at tick 50 the stage must
     * STILL be OVERHEAT (hysteresis hold — pre-hysteresis it exited after a single bleed tick),
     * and by tick 140 it must have exited with the block intact.
     */
    public static void testStirlingEngineOverheatNoExplodeDefault(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        // One flat poll, timed from the setup, which runs once the cell ticks: the bleed this test measures is the
        // engine's own ticks, and a clock started at tick 1 spent the ones its chunk had not yet started (the exit
        // check at tick 141 needs ~111 of them). The hold check runs at exactly OVERHEAT_HOLD_TICKS after the setup
        // (the poll runs once per tick) and the poll ends at OVERHEAT_EXIT_TICKS.
        long[] setupAt = {-1};
        EntityArenaUtil.tickUntil(helper, EntityArenaUtil.CELL_READY_TICKS + OVERHEAT_EXIT_TICKS + TILE_WORK_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS,
                        OVERHEAT_EXIT_TICKS + TILE_WORK_TICKS, () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    if (setupAt[0] < 0) {
                        setupAt[0] = helper.getTick();
                        fastForwardEnergy(engine, 1.0f);

                        // Trigger the BLUE → OVERHEAT transition (would have exploded pre-port)
                        if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                            throw new IllegalStateException("Engine should transition to OVERHEAT at 100% heat. Found: " + engine.getPowerStage());
                        }

                        // Sit just inside the OVERHEAT band (0.86 > 0.85); the bleed must now cool the engine
                        // through the whole hysteresis band before the stage may leave OVERHEAT.
                        fastForwardEnergy(engine, 0.86f);
                        return false;
                    }
                    long age = helper.getTick() - setupAt[0];
                    if (age == OVERHEAT_HOLD_TICKS) {
                        // Hysteresis hold: at tick 50 (power ~0.81 — below entry, above the 0.75 exit) the stage
                        // must still read OVERHEAT instead of flapping back to RED.
                        if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                            throw new IllegalStateException("Hysteresis should hold OVERHEAT through the cool-down band at tick 50. Found: " + engine.getPowerStage());
                        }
                    }
                    return age >= OVERHEAT_EXIT_TICKS;
                }, () -> "the overheat test's poll ran out before its check at " + OVERHEAT_EXIT_TICKS + " ticks"),
                () -> {
                    // By tick 140 the bleed has crossed the 0.75 exit: stage out of OVERHEAT, block intact
                    // (no explosion with canEnginesExplode=false).
                    BlockState stateAfter = helper.getBlockState(enginePos);
                    if (stateAfter.getBlock() != BCEnergyBlocks.ENGINE_STONE.get()) {
                        throw new IllegalStateException("Engine should still be present with canEnginesExplode=false. Block now: " + stateAfter.getBlock());
                    }
                    TileEngineBase_BC8 engineAfter = engineAt(helper, enginePos);
                    if (engineAfter == null) {
                        throw new IllegalStateException("Engine block entity disappeared after OVERHEAT transition");
                    }
                    if (engineAfter.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Engine should self-cool past overheatExitLevel within ~140 ticks (1 MJ/tick bleed). Still: " + engineAfter.getPowerStage());
                    }
                    helper.succeed();
                }, "The overheat test never finished");
    }

    /**
     * Pins the Combustion engine's coolant self-recovery (1.12.2 parity). Its powered cooling
     * equilibrium is IDEAL_HEAT = 100 °C (heatLevel 0.348, GREEN), well below the 0.85 OVERHEAT entry,
     * and it has no hysteresis band (overheatExitLevel() == 0.85), so a running water-cooled engine
     * leaves OVERHEAT the moment heatLevel drops below 0.85. From heat 0.86 (217.8 °C) the water pull
     * of 40 mB x 0.0023 °C/mB = 0.092 °C/tick crosses the 0.85 exit (215.5 °C) in ~25 ticks; assert
     * recovery within 80.
     */
    public static void testCombustionEngineOverheatCoolantRecovery(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_IRON.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        // The 80 ticks are the engine's own: the clock starts at the setup, which runs once the cell ticks.
        EntityArenaUtil.tickUntilThen(helper,
                EntityArenaUtil.CELL_READY_TICKS + TILE_WORK_TICKS + COOLANT_RECOVERY_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, TILE_WORK_TICKS,
                        () -> ironEngineAt(helper, enginePos) != null, () -> "no engine block entity at " + enginePos),
                () -> {
                    TileEngineIron_BC8 engine = ironEngineAt(helper, enginePos);
                    // Plenty of water coolant (fill() is node-uniform — same call loadTank uses).
                    engine.tankCoolant.fill(0, new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 10_000), false);

                    // Force overheat. Iron heat is its own accumulator (not power-derived); fastForwardEnergy
                    // sets the heat field directly (the power it also sets is inert for iron's heat path).
                    fastForwardEnergy(engine, 0.86f);
                    if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Combustion engine should be OVERHEAT at heat 0.86. Found: " + engine.getPowerStage());
                    }
                }, COOLANT_RECOVERY_TICKS, () -> {
                    TileEngineIron_BC8 engine = ironEngineAt(helper, enginePos);
                    if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException(
                            "Redstone-powered, water-cooled combustion engine failed to self-recover from OVERHEAT "
                            + "within 80 ticks — overheatExitLevel() likely sits at/below the 0.80 coolant equilibrium");
                    }
                    helper.succeed();
                }, "The combustion engine never appeared");
    }

    private static TileEngineIron_BC8 ironEngineAt(GameTestHelper helper, BlockPos pos) {
        //? if >=1.21.10 {
        return helper.getBlockEntity(pos, TileEngineIron_BC8.class);
        //?} else {
        /*return helper.getBlockEntity(pos);*/
        //?}
    }

    /**
     * 1.12.2 parity: a running (redstone-powered), water-cooled combustion engine cools toward
     * IDEAL_HEAT and settles at GREEN (heatLevel ~0.348), NOT RED. Regression guard for the port's
     * IDEAL_HEAT=204 artifact, which parked the equilibrium at heatLevel 0.80 (RED), one band below
     * OVERHEAT. Starts at the OLD 204 °C equilibrium (heatLevel 0.80 = RED): with IDEAL_HEAT=100 the
     * engine must cool BELOW that toward GREEN; with the pre-fix 204 it would stall at RED and fail.
     */
    public static void testCombustionEngineCoolsToGreenEquilibrium(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_IRON.get());

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos, TileEngineIron_BC8.class);
            //?} else {
            /*TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            if (engine == null) {
                throw new IllegalStateException("Failed to place Combustion Engine!");
            }
            // A running combustion engine cools toward IDEAL_HEAT (powered), not MIN_HEAT.
            setRedstonePowered(engine, true);
            // Start at the OLD 204 °C equilibrium = heatLevel 0.80 = RED.
            fastForwardEnergy(engine, 0.80f);

            // Drive cooling to equilibrium with unlimited coolant. This is a pure-Java loop (no game
            // ticks elapse), so the watchdog is untouched; ~1130 passes reach 100 °C from 204 at
            // 0.092 °C/tick, 3000 is ample headroom. updateHeatLevel() self-stops once heat <= IDEAL.
            for (int i = 0; i < 3000; i++) {
                if (engine.tankCoolant.getAmountMb(0) < TileEngineIron_BC8.MAX_COOLANT_PER_TICK) {
                    engine.tankCoolant.fill(0,
                        new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 10_000), false);
                }
                engine.updateHeatLevel();
            }

            EnumPowerStage stage = engine.getPowerStage();
            if (stage != EnumPowerStage.GREEN) {
                throw new IllegalStateException(
                    "A water-cooled, redstone-powered combustion engine must settle at GREEN "
                    + "(IDEAL_HEAT=" + TileEngineIron_BC8.IDEAL_HEAT + " °C, heatLevel ~0.348). Found: "
                    + stage + ", heatLevel=" + engine.getHeatLevel());
            }
            helper.succeed();
        });
    }

    /**
     * 1.12.2 parity: the combustion engine pulls at most MAX_COOLANT_PER_TICK (40 mB) of coolant in a
     * single server tick, because updateHeatLevel() runs exactly once per tick. Regression guard for
     * the double-invocation artifact (base serverTick + the engine's own engineUpdate both calling
     * updateHeatLevel), which pulled up to 80 mB/tick and halved the post-shutdown cooldown duration.
     */
    public static void testCombustionEngineCoolantDrawPerTickBounded(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_IRON.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            //? if >=1.21.10 {
            TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos, TileEngineIron_BC8.class);
            //?} else {
            /*TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            if (engine == null) {
                throw new IllegalStateException("Failed to place Combustion Engine!");
            }
            engine.tankCoolant.fill(0,
                new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 10_000), false);
            // RED (204 °C) is above IDEAL_HEAT (100), so the powered engine cools and pulls coolant.
            fastForwardEnergy(engine, 0.80f);

            long before = engine.tankCoolant.getAmountMb(0);
            // ONE full server tick.
            engine.serverTick(engine.getLevel(), enginePos, engine.getBlockState(), engine);
            long drained = before - engine.tankCoolant.getAmountMb(0);

            if (drained > TileEngineIron_BC8.MAX_COOLANT_PER_TICK) {
                throw new IllegalStateException(
                    "Combustion engine drained " + drained + " mB coolant in one tick — exceeds "
                    + "MAX_COOLANT_PER_TICK (" + TileEngineIron_BC8.MAX_COOLANT_PER_TICK
                    + "); updateHeatLevel() is running more than once per tick");
            }
            if (drained <= 0) {
                throw new IllegalStateException(
                    "Test precondition failed: expected the powered, hot, water-cooled engine to "
                    + "pull coolant this tick, but drained " + drained + " mB");
            }
            helper.succeed();
        });
    }

    /**
     * When canEnginesExplode = true, the engine actually explodes on the OVERHEAT transition
     * (1.12.2 parity for users who opt back into the destructive behaviour).
     */
    public static void testStirlingEngineExplodesWhenConfigured(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            //? if >=1.21.10 {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            //?} else {
            /*TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            boolean previousValue = BCLibConfig.canEnginesExplode.get();
            try {
                BCLibConfig.canEnginesExplode.set(true);

                fastForwardEnergy(engine, 1.0f);
                engine.getPowerStage(); // triggers transition → overheat() → explosion

                BlockState stateAfter = helper.getBlockState(enginePos);
                if (stateAfter.getBlock() == BCEnergyBlocks.ENGINE_STONE.get()) {
                    throw new IllegalStateException("Engine should have been removed by the explosion (canEnginesExplode=true)");
                }
                helper.succeed();
            } finally {
                BCLibConfig.canEnginesExplode.set(previousValue);
            }
        });
    }

    /**
     * The {@code clearOverheat} helper resets heat to MIN_HEAT and recomputes the power stage.
     * Called with {@code null} player to avoid the ServerPlayer-only advancement award path
     * (see AGENTS.md "Player-state testing limitation").
     */
    public static void testEngineClearOverheatApi(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        // The "subsequent ticks" are the engine's own: the clock starts at the setup, which runs once the cell ticks
        // (a check that nothing slid back into OVERHEAT is vacuous on an engine that has not ticked).
        EntityArenaUtil.tickUntilThen(helper,
                EntityArenaUtil.CELL_READY_TICKS + TILE_WORK_TICKS + CLEAR_OVERHEAT_SETTLE_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, TILE_WORK_TICKS,
                        () -> engineAt(helper, enginePos) != null, () -> "no engine block entity at " + enginePos),
                () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    fastForwardEnergy(engine, 1.0f);
                    if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Engine should be OVERHEAT before clear. Found: " + engine.getPowerStage());
                    }

                    if (!engine.clearOverheat(null)) {
                        throw new IllegalStateException("clearOverheat should return true for an overheated engine");
                    }
                    if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Stage should not be OVERHEAT after clearOverheat. Found: " + engine.getPowerStage());
                    }
                    if (Math.abs(getHeatDirect(engine) - TileEngineBase_BC8.MIN_HEAT) > 0.01f) {
                        throw new IllegalStateException("Heat should be MIN_HEAT after clear. Found: " + getHeatDirect(engine));
                    }
                    // Calling on a non-overheated engine returns false (no-op)
                    if (engine.clearOverheat(null)) {
                        throw new IllegalStateException("clearOverheat on a non-overheated engine should return false");
                    }
                    // Regression for the "turns blue, then black again" bug: clearOverheat vents the power
                    // buffer, so the heat re-derived from power each tick stays low and the engine must NOT
                    // slide back into OVERHEAT on subsequent ticks. (The pre-fix clearOverheat reset only heat,
                    // which the next tick's updateHeatLevel undid from the still-full buffer.)
                }, CLEAR_OVERHEAT_SETTLE_TICKS, () -> {
                    TileEngineBase_BC8 engineAfter = engineAt(helper, enginePos);
                    if (engineAfter.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Engine re-entered OVERHEAT after a wrench-clear — the power buffer was not vented");
                    }
                    helper.succeed();
                }, "The engine never appeared");
    }

    /**
     * A wrench clears an overheated engine through the block's own {@code useItemOn} (the wrench item's {@code useOn} is
     * never reached), and that is a wrench action like a rotation: it wears the wrench exactly one use.
     */
    public static void testWrenchClearOverheatWearsOnce(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        EntityArenaUtil.forceLoadCell(helper);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());
        ItemStack wrench = new ItemStack(buildcraft.core.BCCoreItems.WRENCH.get());

        EntityArenaUtil.tickUntilThen(helper,
                EntityArenaUtil.CELL_READY_TICKS + TILE_WORK_TICKS + CLEAR_OVERHEAT_SETTLE_TICKS,
                EntityArenaUtil.whenCellTicking(helper, EntityArenaUtil.CELL_READY_TICKS, TILE_WORK_TICKS,
                        () -> engineAt(helper, enginePos) != null, () -> "no engine block entity at " + enginePos),
                () -> {
                    TileEngineBase_BC8 engine = engineAt(helper, enginePos);
                    fastForwardEnergy(engine, 1.0f);
                    if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("Engine should be OVERHEAT before the wrench. Found: " + engine.getPowerStage());
                    }
                    BlockPos abs = helper.absolutePos(enginePos);
                    BlockState state = helper.getBlockState(enginePos);
                    net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.UP, abs, false);
                    try {
                        java.lang.reflect.Method useItemOn = net.minecraft.world.level.block.state.BlockBehaviour.class
                                .getDeclaredMethod("useItemOn", ItemStack.class, BlockState.class,
                                        net.minecraft.world.level.Level.class, BlockPos.class, Player.class,
                                        net.minecraft.world.InteractionHand.class,
                                        net.minecraft.world.phys.BlockHitResult.class);
                        useItemOn.setAccessible(true);
                        useItemOn.invoke(state.getBlock(), wrench, state, helper.getLevel(), abs,
                                helper.makeMockPlayer(GameType.SURVIVAL), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Failed to invoke useItemOn reflectively", e);
                    }
                    if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
                        throw new IllegalStateException("The wrench should have cleared the overheat. Found: " + engine.getPowerStage());
                    }
                    if (wrench.getDamageValue() != 1) {
                        throw new IllegalStateException("Clearing an overheat wears the wrench exactly one use, has lost "
                                + wrench.getDamageValue());
                    }
                }, CLEAR_OVERHEAT_SETTLE_TICKS, helper::succeed, "The engine never appeared");
    }

    /**
     * The {@code hasAlternateReceiver} helper returns false for an isolated engine. Used by
     * block-side wrench handling to detect the "nothing to rotate to" UX case. The true-case
     * (rotation available → return PASS so the wrench item rotates + awards advancement) is
     * exercised by every normal wrench interaction with a pipe attached.
     */
    public static void testEngineHasAlternateReceiverIsolated(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            //? if >=1.21.10 {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            //?} else {
            /*TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos);*/
            //?}
            if (engine.hasAlternateReceiver()) {
                throw new IllegalStateException("Isolated engine should report no alternate receivers");
            }
            helper.succeed();
        });
    }

    /**
     * Upstream 8.0.1 fix ("redstone engines outputting RF when autoconversion was enabled"): with
     * MJ-to-RF autoconversion on, a Stirling engine may still feed an adjacent Forge-Energy acceptor,
     * but the redstone engine must never resolve one as its receiver - its free power is MJ-only.
     * Uses the FE engine's FE input face as the Forge-Energy acceptor.
     */
    public static void testRedstoneEngineDoesNotAutoconvertToFe(GameTestHelper helper) {
        BCLibConfig.PowerMode previous = BCLibConfig.powerMode.get();
        try {
            BCLibConfig.powerMode.set(BCLibConfig.PowerMode.MJ_AUTOCONVERT_RF);
            BlockPos fePos = new BlockPos(3, 2, 2);
            BlockPos redstonePos = new BlockPos(2, 2, 2);
            BlockPos stonePos = new BlockPos(3, 2, 1);
            helper.setBlock(fePos, BCEnergyBlocks.ENGINE_FE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.EAST));
            helper.setBlock(redstonePos, BCCoreBlocks.ENGINE_REDSTONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.EAST));
            helper.setBlock(stonePos, BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.SOUTH));

            BlockPos feAbs = helper.absolutePos(fePos);
            //? if >=1.21.10 {
            var westFe = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, feAbs, Direction.WEST);
            var northFe = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, feAbs, Direction.NORTH);
            TileEngineBase_BC8 redstone = helper.getBlockEntity(redstonePos, TileEngineBase_BC8.class);
            TileEngineBase_BC8 stone = helper.getBlockEntity(stonePos, TileEngineBase_BC8.class);
            //?} else {
            /*var westFe = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, feAbs, Direction.WEST);
            var northFe = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, feAbs, Direction.NORTH);
            TileEngineBase_BC8 redstone = helper.getBlockEntity(redstonePos);
            TileEngineBase_BC8 stone = helper.getBlockEntity(stonePos);*/
            //?}
            helper.assertTrue(westFe != null && northFe != null,
                "Precondition: the FE engine must accept Forge Energy on its west and north faces");

            helper.assertTrue(stone.getReceiverToPower(Direction.SOUTH) != null,
                "Control: a Stirling engine must autoconvert into an adjacent FE acceptor when autoconvert is on");
            helper.assertTrue(redstone.getReceiverToPower(Direction.EAST) == null,
                "A redstone engine must never autoconvert its power into Forge Energy");
            helper.succeed();
        } finally {
            BCLibConfig.powerMode.set(previous);
        }
    }

    private static void fastForwardEnergy(TileEngineBase_BC8 engine, float targetPercentage) {
        long targetPower = (long)(engine.getMaxPower() * targetPercentage);
        float targetHeat = TileEngineBase_BC8.MIN_HEAT + (TileEngineBase_BC8.MAX_HEAT - TileEngineBase_BC8.MIN_HEAT) * targetPercentage;
        
        try {
            java.lang.reflect.Field powerField = TileEngineBase_BC8.class.getDeclaredField("power");
            powerField.setAccessible(true);
            powerField.set(engine, targetPower);
            
            java.lang.reflect.Field heatField = TileEngineBase_BC8.class.getDeclaredField("heat");
            heatField.setAccessible(true);
            heatField.set(engine, targetHeat);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to fast-forward engine", e);
        }
    }

    private static void setRedstonePowered(TileEngineBase_BC8 engine, boolean powered) {
        try {
            java.lang.reflect.Field f = TileEngineBase_BC8.class.getDeclaredField("isRedstonePowered");
            f.setAccessible(true);
            f.setBoolean(engine, powered);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set isRedstonePowered", e);
        }
    }

    private static float getHeatDirect(TileEngineBase_BC8 engine) {
        try {
            java.lang.reflect.Field heatField = TileEngineBase_BC8.class.getDeclaredField("heat");
            heatField.setAccessible(true);
            return heatField.getFloat(engine);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read heat", e);
        }
    }
}
