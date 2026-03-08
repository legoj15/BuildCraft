package buildcraft.energy;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers crude oil as a NeoForge fluid.
 *
 * 1.12 properties (heat_0 crude oil):
 *   density=900, viscosity=2000, temperature=300K, sticky, flammable
 *   texLight=0x505050, texDark=0x050505
 */
public class BCEnergyFluids {

    // --- Deferred Registers ---
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, BCEnergy.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, BCEnergy.MODID);

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(BCEnergy.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(BCEnergy.MODID);

    // --- Oil FluidType ---
    public static final DeferredHolder<FluidType, FluidType> OIL_FLUID_TYPE = FLUID_TYPES.register("oil",
            () -> new FluidType(FluidType.Properties.create()
                    .density(900)
                    .viscosity(2000)
                    .temperature(300)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            ));

    // --- Oil Fluids (source + flowing) ---
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OIL_SOURCE =
            FLUIDS.register("oil", () -> new BaseFlowingFluid.Source(oilProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> OIL_FLOWING =
            FLUIDS.register("oil_flowing", () -> new BaseFlowingFluid.Flowing(oilProperties()));

    // --- Oil Block ---
    public static final DeferredBlock<LiquidBlock> OIL_BLOCK = BLOCKS.register("oil",
            () -> new LiquidBlock(OIL_SOURCE.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .strength(100.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .lightLevel(s -> 0)
            ));

    // --- Oil Bucket ---
    public static final DeferredItem<BucketItem> OIL_BUCKET = ITEMS.register("oil_bucket",
            () -> new BucketItem(OIL_SOURCE.get(),
                    new Item.Properties()
                            .craftRemainder(Items.BUCKET)
                            .stacksTo(1)
            ));

    /** Build properties lazily to avoid forward reference issues. */
    private static BaseFlowingFluid.Properties oilProperties() {
        return new BaseFlowingFluid.Properties(OIL_FLUID_TYPE, OIL_SOURCE, OIL_FLOWING)
                .block(OIL_BLOCK)
                .bucket(OIL_BUCKET)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(10);
    }

    /** Register all deferred registers on the mod event bus. */
    public static void init(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
