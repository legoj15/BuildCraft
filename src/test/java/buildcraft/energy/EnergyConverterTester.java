package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjRfConversion;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.tile.TileDynamoMJ;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.lib.BCLibConfig;

/**
 * GameTest suite for the MJ Dynamo and FE Engine.
 *
 * Tests:
 *  - Upgrade slots accept only valid gears (iron/gold)
 *  - Upgrade slots enforce a max stack size of 1 per slot (4 slots = 4 gears max)
 *  - Upgrades actually increase the MJ intake / FE output rate (Dynamo)
 *  - Upgrades actually increase the FE consumption / MJ output rate (FE Engine)
 */
public class EnergyConverterTester {

    // ─── Dynamo MJ: Upgrade slot filtering ───

    /**
     * Verify that the Dynamo's 4 upgrade slots accept only valid gear items
     * (iron gear, gold gear) and reject non-gear items. Also verifies that
     * each slot enforces a max stack size of 1.
     */
    public static void testDynamoUpgradeSlotFiltering(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.DYNAMO_MJ.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));

        helper.runAfterDelay(2, () -> {
            TileDynamoMJ dynamo = helper.getBlockEntity(pos, TileDynamoMJ.class);
            if (dynamo == null) {
                throw new IllegalStateException("Failed to place MJ Dynamo!");
            }

            // Valid gear items
            ItemStack ironGear = new ItemStack(BCCoreItems.GEAR_IRON.get());
            ItemStack goldGear = new ItemStack(BCCoreItems.GEAR_GOLD.get());

            // Invalid items
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            ItemStack woodGear = new ItemStack(BCCoreItems.GEAR_WOOD.get());
            ItemStack stoneGear = new ItemStack(BCCoreItems.GEAR_STONE.get());
            ItemStack diamondGear = new ItemStack(BCCoreItems.GEAR_DIAMOND.get());

            // --- Test: valid gears are accepted ---
            if (!dynamo.upgrades.canSet(0, ironGear)) {
                throw new IllegalStateException("Dynamo upgrade slot should accept iron gear!");
            }
            if (!dynamo.upgrades.canSet(1, goldGear)) {
                throw new IllegalStateException("Dynamo upgrade slot should accept gold gear!");
            }

            // --- Test: invalid items are rejected ---
            if (dynamo.upgrades.canSet(0, diamond)) {
                throw new IllegalStateException("Dynamo upgrade slot should NOT accept diamonds!");
            }
            if (dynamo.upgrades.canSet(0, woodGear)) {
                throw new IllegalStateException("Dynamo upgrade slot should NOT accept wood gears!");
            }
            if (dynamo.upgrades.canSet(0, stoneGear)) {
                throw new IllegalStateException("Dynamo upgrade slot should NOT accept stone gears!");
            }
            if (dynamo.upgrades.canSet(0, diamondGear)) {
                throw new IllegalStateException("Dynamo upgrade slot should NOT accept diamond gears!");
            }

            // --- Test: max stack size of 1 per slot ---
            // Insert 1 iron gear into slot 0
            ItemStack remaining = dynamo.upgrades.insertItem(0, ironGear.copy(), false);
            if (!dynamo.upgrades.getStackInSlot(0).is(BCCoreItems.GEAR_IRON.get())) {
                throw new IllegalStateException("Failed to insert iron gear into slot 0!");
            }
            if (dynamo.upgrades.getStackInSlot(0).getCount() != 1) {
                throw new IllegalStateException("Slot 0 should contain exactly 1 gear, found: " +
                        dynamo.upgrades.getStackInSlot(0).getCount());
            }

            // Try to insert a second iron gear into the same slot — should be fully rejected
            ItemStack secondGear = new ItemStack(BCCoreItems.GEAR_IRON.get());
            ItemStack leftover = dynamo.upgrades.insertItem(0, secondGear.copy(), false);
            if (dynamo.upgrades.getStackInSlot(0).getCount() != 1) {
                throw new IllegalStateException("Slot 0 should still contain exactly 1 gear after second insert, found: " +
                        dynamo.upgrades.getStackInSlot(0).getCount());
            }
            if (leftover.isEmpty()) {
                throw new IllegalStateException("Second gear insertion should have been fully rejected (leftover should not be empty)!");
            }

            // Fill all 4 slots with gears
            dynamo.upgrades.setStackInSlot(1, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
            dynamo.upgrades.setStackInSlot(2, new ItemStack(BCCoreItems.GEAR_IRON.get()));
            dynamo.upgrades.setStackInSlot(3, new ItemStack(BCCoreItems.GEAR_GOLD.get()));

            // Verify all 4 slots are occupied with exactly 1 each
            int totalGears = 0;
            for (int slot = 0; slot < 4; slot++) {
                ItemStack s = dynamo.upgrades.getStackInSlot(slot);
                if (!s.isEmpty()) {
                    if (s.getCount() != 1) {
                        throw new IllegalStateException("Slot " + slot + " has count " + s.getCount() + ", expected 1!");
                    }
                    totalGears++;
                }
            }
            if (totalGears != 4) {
                throw new IllegalStateException("Expected 4 total gears across all slots, found: " + totalGears);
            }

            helper.succeed();
        });
    }

    // ─── FE Engine: Upgrade slot filtering ───

    /**
     * Same validation as the Dynamo but for the FE Engine.
     */
    public static void testEngineFeUpgradeSlotFiltering(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.ENGINE_FE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));

        helper.runAfterDelay(2, () -> {
            TileEngineFE engine = helper.getBlockEntity(pos, TileEngineFE.class);
            if (engine == null) {
                throw new IllegalStateException("Failed to place FE Engine!");
            }

            ItemStack ironGear = new ItemStack(BCCoreItems.GEAR_IRON.get());
            ItemStack goldGear = new ItemStack(BCCoreItems.GEAR_GOLD.get());
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            ItemStack woodGear = new ItemStack(BCCoreItems.GEAR_WOOD.get());

            // Valid gears accepted
            if (!engine.upgrades.canSet(0, ironGear)) {
                throw new IllegalStateException("FE Engine upgrade slot should accept iron gear!");
            }
            if (!engine.upgrades.canSet(1, goldGear)) {
                throw new IllegalStateException("FE Engine upgrade slot should accept gold gear!");
            }

            // Invalid items rejected
            if (engine.upgrades.canSet(0, diamond)) {
                throw new IllegalStateException("FE Engine upgrade slot should NOT accept diamonds!");
            }
            if (engine.upgrades.canSet(0, woodGear)) {
                throw new IllegalStateException("FE Engine upgrade slot should NOT accept wood gears!");
            }

            // Max stack size of 1
            ItemStack leftover = engine.upgrades.insertItem(0, ironGear.copy(), false);
            if (engine.upgrades.getStackInSlot(0).getCount() != 1) {
                throw new IllegalStateException("Slot 0 should contain exactly 1 gear!");
            }

            ItemStack secondGear = new ItemStack(BCCoreItems.GEAR_IRON.get());
            ItemStack leftover2 = engine.upgrades.insertItem(0, secondGear.copy(), false);
            if (engine.upgrades.getStackInSlot(0).getCount() != 1) {
                throw new IllegalStateException("Slot 0 should still contain exactly 1 gear after second insert!");
            }
            if (leftover2.isEmpty()) {
                throw new IllegalStateException("Second gear should have been rejected!");
            }

            helper.succeed();
        });
    }

    // ─── Dynamo MJ: Upgrades increase conversion rate ───

    /**
     * Tests that upgrades actually increase the rate at which the Dynamo
     * consumes MJ and produces FE. The base rate is 4 MJ/t; iron gear
     * adds 2 MJ/t and gold gear adds 3 MJ/t.
     */
    public static void testDynamoUpgradeEffectiveness(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.DYNAMO_MJ.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));
        // Provide redstone signal
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            TileDynamoMJ dynamo = helper.getBlockEntity(pos, TileDynamoMJ.class);
            if (dynamo == null) {
                throw new IllegalStateException("Failed to place MJ Dynamo!");
            }

            // Baseline: no upgrades
            long baseMjPerTick = dynamo.getMjPerTick();
            long expectedBase = MjAPI.MJ * 4;
            if (baseMjPerTick != expectedBase) {
                throw new IllegalStateException(String.format(
                        "Base MJ/t should be %d, got %d", expectedBase, baseMjPerTick));
            }

            int baseFeRate = dynamo.getFeProductionRate(baseMjPerTick);

            // Add 1 iron gear (+2 MJ/t)
            dynamo.upgrades.setStackInSlot(0, new ItemStack(BCCoreItems.GEAR_IRON.get()));
            long withIronMjPerTick = dynamo.getMjPerTick();
            long expectedIron = MjAPI.MJ * 6; // 4 + 2
            if (withIronMjPerTick != expectedIron) {
                throw new IllegalStateException(String.format(
                        "MJ/t with 1 iron gear should be %d, got %d", expectedIron, withIronMjPerTick));
            }

            int ironFeRate = dynamo.getFeProductionRate(withIronMjPerTick);
            if (ironFeRate <= baseFeRate) {
                throw new IllegalStateException(String.format(
                        "FE rate with iron gear (%d) should be greater than base rate (%d)!",
                        ironFeRate, baseFeRate));
            }

            // Add 1 gold gear (+3 MJ/t)
            dynamo.upgrades.setStackInSlot(1, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
            long withBothMjPerTick = dynamo.getMjPerTick();
            long expectedBoth = MjAPI.MJ * 9; // 4 + 2 + 3
            if (withBothMjPerTick != expectedBoth) {
                throw new IllegalStateException(String.format(
                        "MJ/t with iron+gold gears should be %d, got %d", expectedBoth, withBothMjPerTick));
            }

            int bothFeRate = dynamo.getFeProductionRate(withBothMjPerTick);
            if (bothFeRate <= ironFeRate) {
                throw new IllegalStateException(String.format(
                        "FE rate with iron+gold gears (%d) should be greater than iron-only rate (%d)!",
                        bothFeRate, ironFeRate));
            }

            // Fill all 4 slots: 2 iron + 2 gold = 4 + 4 + 6 = 14 MJ/t
            dynamo.upgrades.setStackInSlot(2, new ItemStack(BCCoreItems.GEAR_IRON.get()));
            dynamo.upgrades.setStackInSlot(3, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
            long fullMjPerTick = dynamo.getMjPerTick();
            long expectedFull = MjAPI.MJ * (4 + 2 + 3 + 2 + 3); // 14 MJ/t
            if (fullMjPerTick != expectedFull) {
                throw new IllegalStateException(String.format(
                        "MJ/t with all 4 gears should be %d, got %d", expectedFull, fullMjPerTick));
            }

            int fullFeRate = dynamo.getFeProductionRate(fullMjPerTick);
            if (fullFeRate <= bothFeRate) {
                throw new IllegalStateException(String.format(
                        "FE rate with 4 gears (%d) should be greater than 2 gears rate (%d)!",
                        fullFeRate, bothFeRate));
            }

            // Verify actual conversion by injecting MJ and simulating tick
            dynamo.getMjBattery().addPower(TileDynamoMJ.MAX_MJ, false);
            dynamo.setCurrentFe(0);

            // Record before-state
            long mjBefore = dynamo.getMjBattery().getStored();
            int feBefore = dynamo.getCurrentFe();

            // Run one engine update cycle
            TileDynamoMJ.serverTick(dynamo.getLevel(), pos, dynamo.getBlockState(), dynamo);

            long mjAfter = dynamo.getMjBattery().getStored();
            int feAfter = dynamo.getCurrentFe();

            long mjConsumed = mjBefore - mjAfter;
            int feProduced = feAfter - feBefore;

            if (mjConsumed <= 0) {
                throw new IllegalStateException("Dynamo with full upgrades should consume MJ, but consumed: " + mjConsumed);
            }
            if (feProduced <= 0) {
                throw new IllegalStateException("Dynamo with full upgrades should produce FE, but produced: " + feProduced);
            }

            // With 14 MJ/t, the FE output should be significantly higher than the base 4 MJ/t
            // Reset and test without upgrades
            for (int i = 0; i < 4; i++) dynamo.upgrades.setStackInSlot(i, ItemStack.EMPTY);
            dynamo.getMjBattery().addPower(TileDynamoMJ.MAX_MJ, false);
            dynamo.setCurrentFe(0);

            long baseMjBefore = dynamo.getMjBattery().getStored();
            TileDynamoMJ.serverTick(dynamo.getLevel(), pos, dynamo.getBlockState(), dynamo);
            long baseMjConsumed = baseMjBefore - dynamo.getMjBattery().getStored();
            int baseFeProduced = dynamo.getCurrentFe();

            if (mjConsumed <= baseMjConsumed) {
                throw new IllegalStateException(String.format(
                        "Upgraded MJ consumption (%d) should exceed base consumption (%d)!",
                        mjConsumed, baseMjConsumed));
            }
            if (feProduced <= baseFeProduced) {
                throw new IllegalStateException(String.format(
                        "Upgraded FE production (%d) should exceed base production (%d)!",
                        feProduced, baseFeProduced));
            }

            helper.succeed();
        });
    }

    // ─── FE Engine: Upgrades increase conversion rate ───

    /**
     * Tests that upgrades actually increase the rate at which the FE Engine
     * consumes FE and produces MJ. The base rate is 4 MJ/t; iron gear
     * adds 2 MJ/t and gold gear adds 3 MJ/t.
     */
    public static void testEngineFeUpgradeEffectiveness(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.ENGINE_FE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            TileEngineFE engine = helper.getBlockEntity(pos, TileEngineFE.class);
            if (engine == null) {
                throw new IllegalStateException("Failed to place FE Engine!");
            }

            // Baseline: no upgrades
            long baseMjPerTick = engine.getMjPerTick();
            long expectedBase = MjAPI.MJ * 4;
            if (baseMjPerTick != expectedBase) {
                throw new IllegalStateException(String.format(
                        "Base MJ/t should be %d, got %d", expectedBase, baseMjPerTick));
            }

            int baseFeConsumption = engine.getFeConsumptionRate();

            // Add 1 iron gear (+2 MJ/t)
            engine.upgrades.setStackInSlot(0, new ItemStack(BCCoreItems.GEAR_IRON.get()));
            long withIronMjPerTick = engine.getMjPerTick();
            long expectedIron = MjAPI.MJ * 6;
            if (withIronMjPerTick != expectedIron) {
                throw new IllegalStateException(String.format(
                        "MJ/t with 1 iron gear should be %d, got %d", expectedIron, withIronMjPerTick));
            }

            int ironFeConsumption = engine.getFeConsumptionRate();
            if (ironFeConsumption <= baseFeConsumption) {
                throw new IllegalStateException(String.format(
                        "FE consumption with iron gear (%d) should be greater than base (%d)!",
                        ironFeConsumption, baseFeConsumption));
            }

            // Add 1 gold gear (+3 MJ/t)
            engine.upgrades.setStackInSlot(1, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
            long withBothMjPerTick = engine.getMjPerTick();
            long expectedBoth = MjAPI.MJ * 9;
            if (withBothMjPerTick != expectedBoth) {
                throw new IllegalStateException(String.format(
                        "MJ/t with iron+gold gears should be %d, got %d", expectedBoth, withBothMjPerTick));
            }

            int bothFeConsumption = engine.getFeConsumptionRate();
            if (bothFeConsumption <= ironFeConsumption) {
                throw new IllegalStateException(String.format(
                        "FE consumption with iron+gold (%d) should be greater than iron-only (%d)!",
                        bothFeConsumption, ironFeConsumption));
            }

            // Fill all 4 slots
            engine.upgrades.setStackInSlot(2, new ItemStack(BCCoreItems.GEAR_IRON.get()));
            engine.upgrades.setStackInSlot(3, new ItemStack(BCCoreItems.GEAR_GOLD.get()));
            long fullMjPerTick = engine.getMjPerTick();
            long expectedFull = MjAPI.MJ * 14;
            if (fullMjPerTick != expectedFull) {
                throw new IllegalStateException(String.format(
                        "MJ/t with all 4 gears should be %d, got %d", expectedFull, fullMjPerTick));
            }

            int fullFeConsumption = engine.getFeConsumptionRate();
            if (fullFeConsumption <= bothFeConsumption) {
                throw new IllegalStateException(String.format(
                        "FE consumption with 4 gears (%d) should exceed 2 gears (%d)!",
                        fullFeConsumption, bothFeConsumption));
            }

            // Verify actual conversion by stuffing FE and simulating tick
            engine.setCurrentFe(TileEngineFE.MAX_FE);

            // Force redstone state
            try {
                java.lang.reflect.Field rsField = buildcraft.lib.engine.TileEngineBase_BC8.class
                        .getDeclaredField("isRedstonePowered");
                rsField.setAccessible(true);
                rsField.set(engine, true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set redstone state", e);
            }

            int feBefore = engine.getCurrentFe();
            long powerBefore = engine.getPower();

            // One engine tick with full upgrades
            TileEngineFE.serverTick(engine.getLevel(), pos, engine.getBlockState(), engine);

            int feAfter = engine.getCurrentFe();
            long powerAfter = engine.getPower();

            int feConsumed = feBefore - feAfter;
            long mjProduced = powerAfter - powerBefore;

            if (feConsumed <= 0) {
                throw new IllegalStateException("FE Engine with full upgrades should consume FE, but consumed: " + feConsumed);
            }
            if (mjProduced <= 0) {
                throw new IllegalStateException("FE Engine with full upgrades should produce MJ, but produced: " + mjProduced);
            }

            // Now test base rate (no upgrades)
            for (int i = 0; i < 4; i++) engine.upgrades.setStackInSlot(i, ItemStack.EMPTY);
            engine.setCurrentFe(TileEngineFE.MAX_FE);

            // Reset power to 0 to avoid max-power cap interfering
            try {
                java.lang.reflect.Field powerField = buildcraft.lib.engine.TileEngineBase_BC8.class
                        .getDeclaredField("power");
                powerField.setAccessible(true);
                powerField.set(engine, 0L);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset power", e);
            }

            int baseFeBefore = engine.getCurrentFe();
            long basePowerBefore = engine.getPower();
            TileEngineFE.serverTick(engine.getLevel(), pos, engine.getBlockState(), engine);
            int baseFeConsumed = baseFeBefore - engine.getCurrentFe();
            long baseMjProduced = engine.getPower() - basePowerBefore;

            if (feConsumed <= baseFeConsumed) {
                throw new IllegalStateException(String.format(
                        "Upgraded FE consumption (%d) should exceed base consumption (%d)!",
                        feConsumed, baseFeConsumed));
            }
            if (mjProduced <= baseMjProduced) {
                throw new IllegalStateException(String.format(
                        "Upgraded MJ production (%d) should exceed base production (%d)!",
                        mjProduced, baseMjProduced));
            }

            helper.succeed();
        });
    }

    public static void testDynamoUpgradeGUIInsertion(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.DYNAMO_MJ.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));

        helper.runAfterDelay(2, () -> {
            TileDynamoMJ dynamo = helper.getBlockEntity(pos, TileDynamoMJ.class);
            if (dynamo == null) {
                throw new IllegalStateException("Failed to place MJ Dynamo!");
            }

            net.minecraft.world.entity.player.Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            buildcraft.energy.container.ContainerDynamoMJ container = new buildcraft.energy.container.ContainerDynamoMJ(0, mockPlayer.getInventory(), dynamo);
            
            // Player inventory starts at slot 4 in the container. Let's put 4 gears there.
            int playerSlot = 4;
            ItemStack fourGears = new ItemStack(BCCoreItems.GEAR_IRON.get(), 4);
            container.getSlot(playerSlot).set(fourGears);

            // Shift click (quick move) the stack
            container.quickMoveStack(mockPlayer, playerSlot);

            // Verify no upgrade slot has more than 1 gear
            int inserted = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack inSlot = container.getSlot(i).getItem();
                inserted += inSlot.getCount();
                if (inSlot.getCount() > 1) {
                    throw new IllegalStateException("Dynamo Slot " + i + " contains > 1 gear! Found: " + inSlot.getCount() + ". Shift-click bypassing max stack limits.");
                }
            }
            
            if (inserted == 0) {
                throw new IllegalStateException("quickMoveStack didn't move any gears!");
            } else if (inserted < fourGears.getCount()) {
                throw new IllegalStateException("quickMoveStack didn't move ALL gears! Only moved " + inserted);
            }

            helper.succeed();
        });
    }

    public static void testEngineFeUpgradeGUIInsertion(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, BCEnergyBlocks.ENGINE_FE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP));

        helper.runAfterDelay(2, () -> {
            TileEngineFE engine = helper.getBlockEntity(pos, TileEngineFE.class);
            if (engine == null) {
                throw new IllegalStateException("Failed to place FE Engine!");
            }

            net.minecraft.world.entity.player.Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            buildcraft.energy.container.ContainerEngineFE container = new buildcraft.energy.container.ContainerEngineFE(0, mockPlayer.getInventory(), engine);

            int playerSlot = 4;
            ItemStack fourGears = new ItemStack(BCCoreItems.GEAR_GOLD.get(), 4);
            container.getSlot(playerSlot).set(fourGears);

            // Shift click
            container.quickMoveStack(mockPlayer, playerSlot);

            int inserted = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack inSlot = container.getSlot(i).getItem();
                inserted += inSlot.getCount();
                if (inSlot.getCount() > 1) {
                    throw new IllegalStateException("FE Engine Slot " + i + " contains > 1 gear! Found: " + inSlot.getCount() + ". Shift-click bypassing limits.");
                }
            }
            
            if (inserted == 0) {
                throw new IllegalStateException("quickMoveStack didn't move any gears!");
            } else if (inserted < fourGears.getCount()) {
                throw new IllegalStateException("quickMoveStack didn't move ALL gears! Only moved " + inserted);
            }

            helper.succeed();
        });
    }
}
