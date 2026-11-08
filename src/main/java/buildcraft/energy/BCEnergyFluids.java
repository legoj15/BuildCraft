/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.energy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import buildcraft.energy.block.BCFluidBlock;
import buildcraft.platform.FluidBehaviour;
import buildcraft.platform.FluidFlow;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCBlockHolder;
import buildcraft.platform.registry.BCItemHolder;

/**
 * Registers all BuildCraft Energy fluids at 3 temperature levels.
 *
 * <p>Port of the 1.12 data table from BCEnergyFluids.defineFluid():
 * <ul>
 *   <li>viscosity = baseViscosity × (5 − heat) / 5</li>
 *   <li>density   = baseDensity × (heat ≥ boilPoint ? −1 : 1)</li>
 *   <li>temp(K)   = 300 + 20 × heat</li>
 *   <li>gaseous   = density &lt; 0 (lighter than air → upside-down bucket)</li>
 * </ul>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class BCEnergyFluids {

    // ─── Deferred Registers ───────────────────────────────────────────

    /** The fluids, and on NeoForge their fluid types (registered under the same names). */
    public static final BCFluidRegistrar FLUIDS = BCFluidRegistrar.create(BCEnergy.MODID);

    public static final BCRegistrar.Blocks BLOCKS =
            BCRegistrar.createBlocks(BCEnergy.MODID);

    public static final BCRegistrar.Items ITEMS =
            BCRegistrar.createItems(BCEnergy.MODID);

    // ─── Fluid entry record ───────────────────────────────────────────

    /** Holds all deferred holders for a single fluid variant. */
    public record FluidEntry(
            String name,
            String baseName,
            int heat,
            int density,
            int viscosity,
            int temperature,
            boolean gaseous,
            FluidVolatility.Band volatility,
            int tintColor,
            int texLight,
            int texDark,
            BCHolder<Fluid, FlowingFluid> source,
            BCHolder<Fluid, FlowingFluid> flowing,
            BCBlockHolder<LiquidBlock> block,
            BCItemHolder<BucketItem> bucket
    ) {}

    // ─── 1.12 fluid data table ────────────────────────────────────────
    //                          density, viscosity, boil, spread, tex_light,   tex_dark,  sticky, flammable
    // flammable (col 7) is 0 only for residue: it decides FluidVolatility.Band.INERT. sticky (col 6) is still unread.
    private static final int[][] FLUID_DATA = {
        { 4000,  4000,  3,  6, 0x50_50_50, 0x05_05_05,  1,  1 }, // 0: Crude Oil
        { 6000,  8000,  3,  4, 0x10_0F_10, 0x42_10_42,  1,  0 }, // 1: Residue
        { 4000,  4000,  3,  6, 0xA0_8F_1F, 0x42_35_20,  1,  1 }, // 2: Heavy Oil
        { 5000,  5000,  3,  5, 0x87_6E_77, 0x42_24_24,  1,  1 }, // 3: Dense Oil
        { 3000,  3500,  2,  8, 0xE4_AF_78, 0xB4_7F_00,  0,  1 }, // 4: Distilled Oil
        { 2000,  5000,  2,  7, 0xFF_AF_3F, 0xE0_7F_00,  0,  1 }, // 5: Dense Fuel
        { 1200,   700,  2,  7, 0xF2_A7_00, 0xC4_87_00,  0,  1 }, // 6: Mixed Heavy Fuels
        { 1000,   900,  1,  8, 0xFF_FF_30, 0xE4_CF_00,  0,  1 }, // 7: Light Fuel
        {  800,   700,  1,  9, 0xF6_D7_00, 0xC4_B7_00,  0,  1 }, // 8: Mixed Light Fuels
        {  300,   600,  0, 10, 0xFA_F6_30, 0xE0_D9_00,  0,  1 }, // 9: Gas Fuel
    };

    private static final String[] FLUID_NAMES = {
        "oil", "oil_residue", "oil_heavy", "oil_dense", "oil_distilled",
        "fuel_dense", "fuel_mixed_heavy", "fuel_light", "fuel_mixed_light", "fuel_gaseous"
    };

    /**
     * Canonical ordering of the ten oil/fuel base names. Used as the index space for
     * per-player production tracking (refine_and_redefine advancement) so a fixed-size
     * {@code int[10]} matches the array order. Each entry has three heat-tier variants
     * registered under {@link #ALL}; this list collapses those to the base name.
     */
    public static final List<String> BASE_NAMES = List.of(FLUID_NAMES);

    // ─── All registered fluid entries ─────────────────────────────────

    private static final List<FluidEntry> ALL_ENTRIES = new ArrayList<>();
    public static final List<FluidEntry> ALL = Collections.unmodifiableList(ALL_ENTRIES);

    // Convenience accessors for crude oil (needed by world gen, spring blocks, etc.)
    public static FluidEntry OIL_COOL;
    /** Searing crude oil: the only crude that generates in the Nether (see {@code OilGenerator#crudeOilFor}). */
    public static FluidEntry OIL_SEARING;

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

            int baseSpread = data[3];
            boolean flammable = data[7] != 0;

            for (int heat = 0; heat < 3; heat++) {
                FluidEntry entry = registerFluidVariant(
                        baseName, heat, baseDensity, baseViscosity, boilPoint, baseSpread, texLight, texDark,
                        flammable);
                ALL_ENTRIES.add(entry);

                // Store convenience reference for crude oil cool
                if (i == 0 && heat == 0) {
                    OIL_COOL = entry;
                }
                // ...and for crude oil searing (heat 2)
                if (i == 0 && heat == 2) {
                    OIL_SEARING = entry;
                }
            }
        }
    }

    // ─── Heat tier lookup ─────────────────────────────────────────────

    /**
     * Returns the heat tier of a BuildCraft energy fluid: 0 = cool, 1 = hot, 2 = searing.
     * Recognises both the source and flowing form of every registered variant.
     *
     * @return the heat tier, or {@code -1} if {@code fluid} is not a BuildCraft energy fluid.
     */
    public static int getHeat(Fluid fluid) {
        if (fluid == null) {
            return -1;
        }
        for (FluidEntry entry : ALL_ENTRIES) {
            if (entry.source().get() == fluid || entry.flowing().get() == fluid) {
                return entry.heat();
            }
        }
        return -1;
    }

    /**
     * Returns the base name (heat-agnostic) of a BuildCraft energy fluid — one of
     * the ten entries in {@link #BASE_NAMES}. Source and flowing forms both resolve
     * to the same base name. Returns {@code null} for fluids that are not BuildCraft
     * energy fluids (water, lava, any other mod's fluids).
     */
    public static String getBaseName(Fluid fluid) {
        if (fluid == null) {
            return null;
        }
        for (FluidEntry entry : ALL_ENTRIES) {
            if (entry.source().get() == fluid || entry.flowing().get() == fluid) {
                return entry.baseName();
            }
        }
        return null;
    }

    /**
     * Dense oils sink through water by deleting the plain water block directly beneath them. Only an actual
     * water fluid block may be displaced: a waterlogged block (a pipe, a marker, stairs, a chest...) reports a
     * water fluid state too, and deleting it destroyed the block itself — contents and all. Such a block
     * refuses oil anyway, so the oil simply rests on top of it.
     */
    static void displaceWaterBelow(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState stateBelow = level.getBlockState(below);
        if (stateBelow.getBlock() instanceof LiquidBlock && stateBelow.getFluidState().is(FluidTags.WATER)) {
            level.setBlockAndUpdate(below, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }

    // ─── Factory method ───────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static FluidEntry registerFluidVariant(
            String baseName, int heat,
            int baseDensity, int baseViscosity, int boilPoint,
            int baseSpread, int texLight, int texDark, boolean flammable) {

        // 1.12 formulas
        int viscosity   = baseViscosity * (5 - heat) / 5;
        int density     = baseDensity * (heat >= boilPoint ? -1 : 1);
        int temperature = 300 + 20 * heat;
        boolean gaseous = density < 0;
        FluidVolatility.Band volatility = FluidVolatility.derive(flammable, boilPoint, heat, gaseous);
        boolean dense = isDenseBase(baseName);

        // 1.12 quanta formula: baseSpread + heat bonus
        int quanta = baseSpread + (baseSpread > 6 ? heat : heat / 2);
        int[] flow = flowParameters(viscosity, quanta);

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

        // Crude oil and residue are too thick to swim in or to count as water (no swim event, riptide, conduit,
        // water damage, underwater music...); every other BC fluid stays water-like.
        boolean crude = baseName.equals("oil");
        boolean waterLike = !crude && !baseName.equals("oil_residue");

        // Source + Flowing (block and bucket through Supplier indirection: they are registered below)
        BCBlockHolder<LiquidBlock>[] blockHolder = new BCBlockHolder[1];
        BCItemHolder<BucketItem>[] bucketHolder = new BCItemHolder[1];

        BCFluidRegistrar.Entry fluids = FLUIDS.register(regName, FluidProperties.builder()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(temperature)
                        .canExtinguish(false)
                        .canConvertToSource(false)
                        .canSwim(waterLike)
                        .canPushEntity(true)
                        .canDrown(true)
                        .waterLike(waterLike)
                        // A gas rises, so it cannot hold a boat up; every liquid does, whatever its heat.
                        .supportsBoating(!gaseous)
                        .motionScale(BCEnergyFluids::motionScale)
                        .boatSpeed(BCEnergyFluids::boatSpeed)
                        .bucketSounds(SoundEvents.BUCKET_FILL, SoundEvents.BUCKET_EMPTY)
                        .slopeFindDistance(flow[0])
                        .levelDecreasePerBlock(flow[1])
                        .tickRate(flow[2])
                        .build(),
                new OilBehaviour(volatility, dense), () -> blockHolder[0].get(), () -> bucketHolder[0].get());
        BCHolder<Fluid, FlowingFluid> source = fluids.source();

        // Block
        MapColor mapColor = gaseous ? MapColor.NONE : MapColor.COLOR_BLACK;
        blockHolder[0] = BLOCKS.registerBlock(regName,
                props -> new BCFluidBlock(source.get(), props
                        .mapColor(mapColor)
                        .replaceable()
                        .strength(100.0F)
                        //? if >=26.3 {
                        // 26.3 renamed PushReaction.DESTROY to POPPED; the piston resolver treats it identically.
                        .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
                        //?} else {
                        /*.pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                        *///?}
                        .noLootTable()
                        .liquid()
                        .lightLevel(s -> 0)
                , heat, volatility, !crude), () -> BlockBehaviour.Properties.of());

        // Bucket
        bucketHolder[0] = ITEMS.registerItem(regName + "_bucket",
                props -> new BucketItem(source.get(), props
                        .craftRemainder(Items.BUCKET)
                        .stacksTo(1)
                ), () -> new Item.Properties());

        return new FluidEntry(
                regName, baseName, heat, density, viscosity, temperature, gaseous, volatility, tintColor,
                texLight, texDark,
                source, fluids.flowing(), blockHolder[0], bucketHolder[0]);
    }

    /** The dense oils sink through water ({@link #displaceWaterBelow}) instead of floating on it: heavy oil, dense oil
     *  and residue, at every heat. Decided once per variant at registration. */
    private static boolean isDenseBase(String baseName) {
        return baseName.equals("oil_heavy") || baseName.equals("oil_dense") || baseName.equals("oil_residue");
    }

    /** The flow parameters {slopeFindDistance, levelDecreasePerBlock, tickRate} of a fluid of this viscosity and
     *  1.12 quanta. */
    private static int[] flowParameters(int viscosity, int quanta) {

        // Derive tick rate from viscosity, matching Forge 1.12.2 BlockFluidBase formula:
        // tickRate = fluid.viscosity / 200
        int tickRate = Math.max(1, viscosity / 200);

        // Map 1.12 quanta (spread distance) to NeoForge flow parameters.
        // quanta ≤ 6: short-range (like lava), quanta ≥ 7: longer-range (like water)
        int slopeFindDistance;
        int levelDecreasePerBlock;
        if (quanta <= 5) {
            slopeFindDistance = 2;
            levelDecreasePerBlock = 2;
        } else if (quanta <= 6) {
            slopeFindDistance = 3;
            levelDecreasePerBlock = 1;
        } else if (quanta <= 8) {
            slopeFindDistance = 4;
            levelDecreasePerBlock = 1;
        } else {
            slopeFindDistance = 5;
            levelDecreasePerBlock = 1;
        }

        return new int[] {slopeFindDistance, levelDecreasePerBlock, tickRate};
    }

    /** Register all deferred registers on the mod event bus. */
    public static void init() {
        FLUIDS.subscribe();
        BLOCKS.subscribe();
        ITEMS.subscribe();
    }

    /**
     * The flow-push scalar of every energy fluid: the per-tick scalar applied to the fluid FLOW velocity that carries
     * entities along a current, NOT friction. Vanilla water is 0.014; the old 0.8/0.5 here were ~40-57x too strong and
     * launched entities downstream in flowing fluid. Oil stays a touch weaker than water (denser/sluggish) but in the
     * correct range. Read on every use, so the config may change it at runtime.
     */
    private static double motionScale() {
        if (BCEnergyConfig.oilIsSticky != null && BCEnergyConfig.oilIsSticky.get()) {
            return 0.007D; // sticky: sluggish, ~half of water's current
        }
        return 0.012D; // dense oil: a slightly weaker current than water (0.014)
    }

    /**
     * The factor on a boat's top speed on any energy fluid (water 1): the sticky option halves it, as it halves the
     * current ({@link #motionScale}). Read on every use, so the config may change it at runtime; the common
     * {@code BoatFluidMotion} turns it into a drag on both loaders.
     */
    private static double boatSpeed() {
        if (BCEnergyConfig.oilIsSticky != null && BCEnergyConfig.oilIsSticky.get()) {
            return 0.5D; // sticky: a boat sails at half the speed it does on water
        }
        return 1.0D;
    }

    // ─── Fluid behaviour ──────────────────────────────────────────────

    /**
     * What every energy fluid adds to plain flowing: an explosive gas source detonates near ignition, the dense oils
     * sink through water ({@link #displaceWaterBelow}), the rest spread across a water surface instead of stopping at
     * it, and a source is never washed away by water.
     */
    static final class OilBehaviour implements FluidBehaviour {
        private final FluidVolatility.Band volatility;
        private final boolean dense;

        OilBehaviour(FluidVolatility.Band volatility, boolean dense) {
            this.volatility = volatility;
            this.dense = dense;
        }

        @Override
        public boolean beforeTick(FluidFlow flow, Level level, BlockPos pos, FluidState fluidState) {
            // Only a source detonates (FluidVolatility.igniteExposedGas): flowing gas burns or is cleared by a
            // neighbouring blast.
            if (fluidState.isSource() && FluidVolatility.igniteExposedGas(level, pos, fluidState, volatility)) {
                return true;
            }
            if (dense) {
                displaceWaterBelow(level, pos);
            }
            return false;
        }

        @Override
        public void afterTick(FluidFlow flow, Level level, BlockPos pos, FluidState fluidState) {
            if (dense || fluidState.isSource()) {
                return;
            }
            FlowingFluid self = flow.fluid();
            FluidState belowFluid = level.getFluidState(pos.below());
            if (!belowFluid.is(FluidTags.WATER) || belowFluid.getType().isSame(self)) {
                return;
            }
            BlockState currentState = level.getBlockState(pos);
            FluidState currentFluid = currentState.getFluidState();
            if (currentFluid.isEmpty() || !currentFluid.getType().isSame(self)) {
                return;
            }
            int neighborAmount = currentFluid.getAmount() - flow.dropOffPerBlock(level);
            if (currentFluid.getValue(FlowingFluid.FALLING)) {
                neighborAmount = 7;
            }
            if (neighborAmount > 0) {
                Map<Direction, FluidState> map = flow.spreadTargets(level, pos, currentState);
                for (Map.Entry<Direction, FluidState> entry : map.entrySet()) {
                    Direction dir = entry.getKey();
                    BlockPos targetPos = pos.relative(dir);
                    flow.spreadInto(level, targetPos, level.getBlockState(targetPos), dir, entry.getValue());
                }
            }
        }

        @Override
        public void adjustSpread(FluidFlow flow, Level level, BlockPos pos, BlockState state,
                Map<Direction, FluidState> map) {
            FluidState belowFluid = level.getFluidState(pos.below());
            if (dense || !belowFluid.is(FluidTags.WATER)) {
                return;
            }
            // Only the flowing form leaves a fluid that rests on its own family to vanilla's slope choice (the "layer
            // cake" guard). The source form always forced the spread there, as the old BCCustomSourceFluid did (every
            // BC fluid is in minecraft:water, so a source on its own source or falling column passes the test above),
            // and the form is the instance's, not the state's: a caller may hand the flowing form a source's state.
            boolean sourceForm = flow.fluid().defaultFluidState().isSource();
            if (!sourceForm && belowFluid.getType().isSame(flow.fluid())) {
                return;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (!map.containsKey(dir)) {
                    BlockPos targetPos = pos.relative(dir);
                    BlockState targetState = level.getBlockState(targetPos);
                    if (targetState.isAir() || targetState.canBeReplaced()) {
                        FluidState targetFluidState = targetState.getFluidState();
                        // A same-fluid neighbour needs no skip here: its canBeReplacedWith refuses any
                        // horizontal replacement (BaseFlowingFluid: only DOWN, and never the same family).
                        FluidState newFluid = flow.newLiquidAt(level, targetPos, targetState);
                        if (!newFluid.isEmpty()
                                && targetFluidState.canBeReplacedWith(level, targetPos, newFluid.getType(), dir)) {
                            map.put(dir, newFluid);
                        }
                    }
                }
            }
        }

        @Override
        public boolean refusesReplacement(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn,
                Direction direction) {
            // Water never washes a source away; the flowing form keeps the default rule.
            return state.isSource() && fluidIn.defaultFluidState().is(FluidTags.WATER);
        }
    }
}
