package buildcraft.energy;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.lib.misc.BlockEntityTypeUtilBC;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.block.BlockSpring;
import buildcraft.energy.tile.TileSpringOil;
import buildcraft.energy.tile.TileEngineStone_BC8;
import buildcraft.energy.tile.TileEngineIron_BC8;
import buildcraft.energy.tile.TileEngineFE;
import buildcraft.energy.tile.TileDynamoMJ;

public class BCEnergyBlockEntities {
    // All block entities under the unified namespace — no more cross-module hack
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCCore.MODID);

    public static final Supplier<BlockEntityType<TileSpringOil>> SPRING_OIL = BLOCK_ENTITIES.register(
            "spring_oil",
            () -> BlockEntityTypeUtilBC.create(TileSpringOil::new, BCCoreBlocks.SPRING_OIL.get()));

    public static final Supplier<BlockEntityType<TileEngineStone_BC8>> ENGINE_STONE = BLOCK_ENTITIES.register(
            "engine_stone",
            () -> BlockEntityTypeUtilBC.create(TileEngineStone_BC8::new, BCEnergyBlocks.ENGINE_STONE.get()));

    public static final Supplier<BlockEntityType<TileEngineIron_BC8>> ENGINE_IRON = BLOCK_ENTITIES.register(
            "engine_iron",
            () -> BlockEntityTypeUtilBC.create(TileEngineIron_BC8::new, BCEnergyBlocks.ENGINE_IRON.get()));

    public static final Supplier<BlockEntityType<TileEngineFE>> ENGINE_FE = BLOCK_ENTITIES.register(
            "engine_rf",
            () -> BlockEntityTypeUtilBC.create(TileEngineFE::new, BCEnergyBlocks.ENGINE_FE.get()));

    public static final Supplier<BlockEntityType<TileDynamoMJ>> DYNAMO_MJ = BLOCK_ENTITIES.register(
            "mj_dynamo",
            () -> BlockEntityTypeUtilBC.create(TileDynamoMJ::new, BCEnergyBlocks.DYNAMO_MJ.get()));

    public static void init(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        // Wire the core-side spring block to construct our energy-side oil tile.
        BlockSpring.oilTileFactory = TileSpringOil::new;
    }
}
