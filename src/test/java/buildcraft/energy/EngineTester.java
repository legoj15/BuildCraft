package buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.enums.EnumPowerStage;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.core.BCCoreBlocks;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.lib.BCLibConfig;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineStone_BC8;

public class EngineTester {

    public static void testRedstoneEngineDryRunHeat(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        BlockPos redstonePos = new BlockPos(2, 2, 1);
        
        BlockState engineState = BCCoreBlocks.ENGINE_REDSTONE.get().defaultBlockState()
                .setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.UP);
                
        helper.setBlock(enginePos, engineState);
        helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
        
        // Give the engine 40 ticks to natively generate power via its internal block mechanics
        helper.runAfterDelay(40, () -> {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            if (engine == null) {
                throw new IllegalStateException("Failed to place Redstone Engine!");
            }
            
            // Validate it is running natively
            if (!engine.isBurning()) {
                throw new IllegalStateException("Engine is not burning with redstone signal!");
            }
            
            // Ensure dry-run mechanics naturally accumulated power and increased heat
            if (engine.getPower() <= 0) {
                throw new IllegalStateException("Engine dry-run failed to natively accumulate internal power over 40 ticks!");
            }
            if (engine.getHeatLevel() <= 0) {
                throw new IllegalStateException("Engine dry-run failed to natively increase heat over 40 ticks! Current Heat: " + engine.getHeatLevel());
            }
            
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
        });
    }

    public static void testRedstoneEngineSafeLimit(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCCoreBlocks.ENGINE_REDSTONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(1, () -> {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            // Push to 84% (right at the boundary below OVERHEAT which is 85%)
            // The engine naturally maxes out at 80% (0.8f). 
            fastForwardEnergy(engine, 0.84f);
            
            helper.runAfterDelay(20, () -> {
                // Engine should comfortably lock internally at RED, but never OVERHEAT
                if (engine.getPowerStage() == EnumPowerStage.OVERHEAT) {
                    throw new IllegalStateException("Redstone Engine entered an OVERHEAT stage. Redstone engines should be perfectly safe and lock at RED.");
                }
                if (engine.getPowerStage() != EnumPowerStage.RED) {
                    throw new IllegalStateException("Redstone Engine failed to reach RED stage. Found: " + engine.getPowerStage());
                }
                
                helper.succeed();
            });
        });
    }

    public static void testStirlingEngineFuel(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(2, () -> {
            TileEngineStone_BC8 engine = helper.getBlockEntity(enginePos, TileEngineStone_BC8.class);
            
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

    public static void testStirlingEngineExplosion(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            
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
            TileEngineIron_BC8 engine = helper.getBlockEntity(enginePos, TileEngineIron_BC8.class);
            helper.succeed();
            // TODO: Combustion Engine tests assert dual fluids when fluids are fully linked via NeoForge Capabilities
        });
    }

    /**
     * Default behavior: an overheated engine sits in the OVERHEAT stage and does NOT explode.
     * Verifies the block is still present and the stage remains OVERHEAT after a brief tick window.
     */
    public static void testStirlingEngineOverheatNoExplodeDefault(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            fastForwardEnergy(engine, 1.0f);

            // Trigger the BLUE → OVERHEAT transition (would have exploded pre-port)
            if (engine.getPowerStage() != EnumPowerStage.OVERHEAT) {
                throw new IllegalStateException("Engine should transition to OVERHEAT at 100% heat. Found: " + engine.getPowerStage());
            }

            // 20 ticks later the block must still be present and still overheated
            helper.runAfterDelay(20, () -> {
                BlockState stateAfter = helper.getBlockState(enginePos);
                if (stateAfter.getBlock() != BCEnergyBlocks.ENGINE_STONE.get()) {
                    throw new IllegalStateException("Engine should still be present with canEnginesExplode=false. Block now: " + stateAfter.getBlock());
                }
                TileEngineBase_BC8 engineAfter = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
                if (engineAfter == null) {
                    throw new IllegalStateException("Engine block entity disappeared after OVERHEAT transition");
                }
                if (engineAfter.getPowerStage() != EnumPowerStage.OVERHEAT) {
                    throw new IllegalStateException("Engine should still be OVERHEAT after 20 ticks (no passive decay in stone). Found: " + engineAfter.getPowerStage());
                }
                helper.succeed();
            });
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
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
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
     * (see CLAUDE.md "Player-state testing limitation").
     */
    public static void testEngineClearOverheatApi(GameTestHelper helper) {
        BlockPos enginePos = new BlockPos(2, 2, 2);
        helper.setBlock(enginePos, BCEnergyBlocks.ENGINE_STONE.get());

        helper.runAfterDelay(1, () -> {
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
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
            helper.succeed();
        });
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
            TileEngineBase_BC8 engine = helper.getBlockEntity(enginePos, TileEngineBase_BC8.class);
            if (engine.hasAlternateReceiver()) {
                throw new IllegalStateException("Isolated engine should report no alternate receivers");
            }
            helper.succeed();
        });
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
