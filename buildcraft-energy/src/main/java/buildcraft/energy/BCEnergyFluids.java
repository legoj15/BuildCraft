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
 * Registers crude oil at three temperature levels as NeoForge fluids.
 *
 * 1.12 crude oil properties:
 *   density=900, baseViscosity=2000, temperature=300K, sticky, flammable
 *   texLight=0x505050, texDark=0x050505
 *
 * Heat variants (from 1.12 BCEnergyFluids.defineFluid):
 *   heat_0 (Cool):    viscosity=2000, temp=300K, tint=0xFF2A2A2A
 *   heat_1 (Hot):     viscosity=1500, temp=320K, tint=0xFF3A3A3A (slightly lighter)
 *   heat_2 (Searing): viscosity=1000, temp=340K, tint=0xFF4A4A4A (lighter still)
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

    // ============================
    //   Oil (Cool) — heat_0
    // ============================
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

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OIL_SOURCE =
            FLUIDS.register("oil", () -> new BaseFlowingFluid.Source(oilProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> OIL_FLOWING =
            FLUIDS.register("oil_flowing", () -> new BaseFlowingFluid.Flowing(oilProperties()));

    public static final DeferredBlock<LiquidBlock> OIL_BLOCK = BLOCKS.registerBlock("oil",
            props -> new LiquidBlock(OIL_SOURCE.get(), props
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .strength(100.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .lightLevel(s -> 0)
            ), BlockBehaviour.Properties.of());

    public static final DeferredItem<BucketItem> OIL_BUCKET = ITEMS.registerItem("oil_bucket",
            props -> new BucketItem(OIL_SOURCE.get(), props
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)
            ), new Item.Properties());

    private static BaseFlowingFluid.Properties oilProperties() {
        return new BaseFlowingFluid.Properties(OIL_FLUID_TYPE, OIL_SOURCE, OIL_FLOWING)
                .block(OIL_BLOCK)
                .bucket(OIL_BUCKET)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(10);
    }

    // ============================
    //   Oil Heat 1 (Hot)
    // ============================
    public static final DeferredHolder<FluidType, FluidType> OIL_HEAT_1_FLUID_TYPE = FLUID_TYPES.register("oil_heat_1",
            () -> new FluidType(FluidType.Properties.create()
                    .density(900)
                    .viscosity(1500) // 2000 * 3/4
                    .temperature(320) // 300 + 20
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            ));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OIL_HEAT_1_SOURCE =
            FLUIDS.register("oil_heat_1", () -> new BaseFlowingFluid.Source(oilHeat1Properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> OIL_HEAT_1_FLOWING =
            FLUIDS.register("oil_heat_1_flowing", () -> new BaseFlowingFluid.Flowing(oilHeat1Properties()));

    public static final DeferredBlock<LiquidBlock> OIL_HEAT_1_BLOCK = BLOCKS.registerBlock("oil_heat_1",
            props -> new LiquidBlock(OIL_HEAT_1_SOURCE.get(), props
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .strength(100.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .lightLevel(s -> 0)
            ), BlockBehaviour.Properties.of());

    public static final DeferredItem<BucketItem> OIL_HEAT_1_BUCKET = ITEMS.registerItem("oil_heat_1_bucket",
            props -> new BucketItem(OIL_HEAT_1_SOURCE.get(), props
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)
            ), new Item.Properties());

    private static BaseFlowingFluid.Properties oilHeat1Properties() {
        return new BaseFlowingFluid.Properties(OIL_HEAT_1_FLUID_TYPE, OIL_HEAT_1_SOURCE, OIL_HEAT_1_FLOWING)
                .block(OIL_HEAT_1_BLOCK)
                .bucket(OIL_HEAT_1_BUCKET)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(10);
    }

    // ============================
    //   Oil Heat 2 (Searing)
    // ============================
    public static final DeferredHolder<FluidType, FluidType> OIL_HEAT_2_FLUID_TYPE = FLUID_TYPES.register("oil_heat_2",
            () -> new FluidType(FluidType.Properties.create()
                    .density(900)
                    .viscosity(1000) // 2000 * 2/4
                    .temperature(340) // 300 + 40
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
            ));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OIL_HEAT_2_SOURCE =
            FLUIDS.register("oil_heat_2", () -> new BaseFlowingFluid.Source(oilHeat2Properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> OIL_HEAT_2_FLOWING =
            FLUIDS.register("oil_heat_2_flowing", () -> new BaseFlowingFluid.Flowing(oilHeat2Properties()));

    public static final DeferredBlock<LiquidBlock> OIL_HEAT_2_BLOCK = BLOCKS.registerBlock("oil_heat_2",
            props -> new LiquidBlock(OIL_HEAT_2_SOURCE.get(), props
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .strength(100.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .lightLevel(s -> 0)
            ), BlockBehaviour.Properties.of());

    public static final DeferredItem<BucketItem> OIL_HEAT_2_BUCKET = ITEMS.registerItem("oil_heat_2_bucket",
            props -> new BucketItem(OIL_HEAT_2_SOURCE.get(), props
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1)
            ), new Item.Properties());

    private static BaseFlowingFluid.Properties oilHeat2Properties() {
        return new BaseFlowingFluid.Properties(OIL_HEAT_2_FLUID_TYPE, OIL_HEAT_2_SOURCE, OIL_HEAT_2_FLOWING)
                .block(OIL_HEAT_2_BLOCK)
                .bucket(OIL_HEAT_2_BUCKET)
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
