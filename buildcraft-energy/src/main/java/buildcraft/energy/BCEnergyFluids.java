package buildcraft.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
 * Registers all BuildCraft Energy fluids at 3 temperature levels.
 *
 * <p>Port of the 1.12 data table from BCEnergyFluids.defineFluid():
 * <ul>
 *   <li>viscosity = baseViscosity × (4 − heat) / 4</li>
 *   <li>density   = baseDensity × (heat ≥ boilPoint ? −1 : 1)</li>
 *   <li>temp(K)   = 300 + 20 × heat</li>
 *   <li>gaseous   = density &lt; 0 (lighter than air → upside-down bucket)</li>
 * </ul>
 */
public class BCEnergyFluids {

    // ─── Deferred Registers ───────────────────────────────────────────

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, BCEnergy.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, BCEnergy.MODID);

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(BCEnergy.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(BCEnergy.MODID);

    // ─── Fluid entry record ───────────────────────────────────────────

    /** Holds all deferred holders for a single fluid variant. */
    public record FluidEntry(
            String name,
            int heat,
            int density,
            int viscosity,
            int temperature,
            boolean gaseous,
            int tintColor,
            DeferredHolder<FluidType, FluidType> fluidType,
            DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
            DeferredBlock<LiquidBlock> block,
            DeferredItem<BucketItem> bucket
    ) {}

    // ─── 1.12 fluid data table ────────────────────────────────────────
    //                          density, viscosity, boil, spread, tex_light,   tex_dark,  sticky, flammable
    private static final int[][] FLUID_DATA = {
        {  900,  2000,  3,  6, 0x50_50_50, 0x05_05_05,  1,  1 }, // 0: Crude Oil
        { 1200,  4000,  3,  4, 0x10_0F_10, 0x42_10_42,  1,  0 }, // 1: Residue
        {  850,  1800,  3,  6, 0xA0_8F_1F, 0x42_35_20,  1,  1 }, // 2: Heavy Oil
        {  950,  1600,  3,  5, 0x87_6E_77, 0x42_24_24,  1,  1 }, // 3: Dense Oil
        {  750,  1400,  2,  8, 0xE4_AF_78, 0xB4_7F_00,  0,  1 }, // 4: Distilled Oil
        {  600,   800,  2,  7, 0xFF_AF_3F, 0xE0_7F_00,  0,  1 }, // 5: Dense Fuel
        {  700,  1000,  2,  7, 0xF2_A7_00, 0xC4_87_00,  0,  1 }, // 6: Mixed Heavy Fuels
        {  400,   600,  1,  8, 0xFF_FF_30, 0xE4_CF_00,  0,  1 }, // 7: Light Fuel
        {  650,   900,  1,  9, 0xF6_D7_00, 0xC4_B7_00,  0,  1 }, // 8: Mixed Light Fuels
        {  300,   500,  0, 10, 0xFA_F6_30, 0xE0_D9_00,  0,  1 }, // 9: Gas Fuel
    };

    private static final String[] FLUID_NAMES = {
        "oil", "oil_residue", "oil_heavy", "oil_dense", "oil_distilled",
        "fuel_dense", "fuel_mixed_heavy", "fuel_light", "fuel_mixed_light", "fuel_gaseous"
    };

    // ─── All registered fluid entries ─────────────────────────────────

    private static final List<FluidEntry> ALL_ENTRIES = new ArrayList<>();
    public static final List<FluidEntry> ALL = Collections.unmodifiableList(ALL_ENTRIES);

    // Convenience accessors for crude oil (needed by world gen, spring blocks, etc.)
    public static FluidEntry OIL_COOL;

    // ─── Static initializer: register all fluids ──────────────────────

    static {
        for (int i = 0; i < FLUID_DATA.length; i++) {
            int[] data = FLUID_DATA[i];
            String baseName = FLUID_NAMES[i];

            int baseDensity    = data[0];
            int baseViscosity  = data[1];
            int boilPoint      = data[2];
            int texLight       = data[4];
            int texDark        = data[5];

            for (int heat = 0; heat < 3; heat++) {
                FluidEntry entry = registerFluidVariant(
                        baseName, heat, baseDensity, baseViscosity, boilPoint, texLight, texDark);
                ALL_ENTRIES.add(entry);

                // Store convenience reference for crude oil cool
                if (i == 0 && heat == 0) {
                    OIL_COOL = entry;
                }
            }
        }
    }

    // ─── Factory method ───────────────────────────────────────────────

    private static FluidEntry registerFluidVariant(
            String baseName, int heat,
            int baseDensity, int baseViscosity, int boilPoint,
            int texLight, int texDark) {

        // 1.12 formulas
        int viscosity   = baseViscosity * (4 - heat) / 4;
        int density     = baseDensity * (heat >= boilPoint ? -1 : 1);
        int temperature = 300 + 20 * heat;
        boolean gaseous = density < 0;

        // Tint: average of light and dark, slightly brighter per heat level
        int tintR = (((texLight >> 16) & 0xFF) + ((texDark >> 16) & 0xFF)) / 2 + heat * 0x10;
        int tintG = (((texLight >>  8) & 0xFF) + ((texDark >>  8) & 0xFF)) / 2 + heat * 0x10;
        int tintB = (((texLight      ) & 0xFF) + ((texDark      ) & 0xFF)) / 2 + heat * 0x10;
        tintR = Math.min(tintR, 0xFF);
        tintG = Math.min(tintG, 0xFF);
        tintB = Math.min(tintB, 0xFF);
        int tintColor = 0xFF000000 | (tintR << 16) | (tintG << 8) | tintB;

        // Registry name: "oil" for heat_0, "oil_heat_1" for heat_1, etc.
        String regName = baseName + (heat == 0 ? "" : "_heat_" + heat);

        // FluidType
        DeferredHolder<FluidType, FluidType> fluidType = FLUID_TYPES.register(regName,
                () -> new FluidType(FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(temperature)
                        .canExtinguish(false)
                        .canConvertToSource(false)
                        .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                        .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                ));

        // Source + Flowing (use Supplier indirection to avoid forward-reference issues)
        DeferredHolder<Fluid, BaseFlowingFluid.Source>[] sourceHolder = new DeferredHolder[1];
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing>[] flowingHolder = new DeferredHolder[1];
        DeferredBlock<LiquidBlock>[] blockHolder = new DeferredBlock[1];
        DeferredItem<BucketItem>[] bucketHolder = new DeferredItem[1];

        sourceHolder[0] = FLUIDS.register(regName,
                () -> new BaseFlowingFluid.Source(makeProps(
                        fluidType, sourceHolder[0], flowingHolder[0],
                        blockHolder[0], bucketHolder[0])));

        flowingHolder[0] = FLUIDS.register(regName + "_flowing",
                () -> new BaseFlowingFluid.Flowing(makeProps(
                        fluidType, sourceHolder[0], flowingHolder[0],
                        blockHolder[0], bucketHolder[0])));

        // Block
        MapColor mapColor = gaseous ? MapColor.NONE : MapColor.COLOR_BLACK;
        blockHolder[0] = BLOCKS.registerBlock(regName,
                props -> new LiquidBlock(sourceHolder[0].get(), props
                        .mapColor(mapColor)
                        .replaceable()
                        .strength(100.0F)
                        .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                        .noLootTable()
                        .liquid()
                        .lightLevel(s -> 0)
                ), BlockBehaviour.Properties.of());

        // Bucket
        bucketHolder[0] = ITEMS.registerItem(regName + "_bucket",
                props -> new BucketItem(sourceHolder[0].get(), props
                        .craftRemainder(Items.BUCKET)
                        .stacksTo(1)
                ), new Item.Properties());

        return new FluidEntry(
                regName, heat, density, viscosity, temperature, gaseous, tintColor,
                fluidType, sourceHolder[0], flowingHolder[0], blockHolder[0], bucketHolder[0]);
    }

    private static BaseFlowingFluid.Properties makeProps(
            DeferredHolder<FluidType, FluidType> type,
            DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing,
            DeferredBlock<LiquidBlock> block,
            DeferredItem<BucketItem> bucket) {
        return new BaseFlowingFluid.Properties(type, source, flowing)
                .block(block)
                .bucket(bucket)
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
