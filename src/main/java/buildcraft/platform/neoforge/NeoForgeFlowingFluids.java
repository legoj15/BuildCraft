/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.Collection;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.platform.FluidBehaviour;
import buildcraft.platform.FluidFlow;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.ThickFluidMotion;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;

/**
 * The NeoForge side of {@link BCFluidRegistrar}: a {@link FluidType} built from the {@link FluidProperties} and
 * registered under the fluid's name, then the {@link BaseFlowingFluid} pair, whose {@code tick}, {@code getSpread}
 * and {@code canBeReplacedWith} overrides hand over to the {@link FluidBehaviour}. These were
 * {@code BCEnergyFluids.BCCustomFluidType}, {@code BCCustomSourceFluid} and {@code BCCustomFlowingFluid}; the gameplay
 * in their overrides now lives in the behaviour, in common code.
 */
public final class NeoForgeFlowingFluids implements BCFluidRegistrar {
    private final BCRegistrar<FluidType> types;
    private final BCRegistrar<Fluid> fluids;

    NeoForgeFlowingFluids(String namespace) {
        types = BCRegistrar.create(NeoForgeRegistries.Keys.FLUID_TYPES, namespace);
        fluids = BCRegistrar.create(Registries.FLUID, namespace);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Entry register(String name, FluidProperties properties, FluidBehaviour behaviour,
            Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket) {
        BCHolder<FluidType, FluidType> type = types.register(name, () -> new Type(properties));
        // Supplier indirection: each fluid's properties name both holders, which exist once both are registered.
        BCHolder<Fluid, FlowingFluid>[] source = new BCHolder[1];
        BCHolder<Fluid, FlowingFluid>[] flowing = new BCHolder[1];
        source[0] = fluids.register(name, () -> new Source(
                flowProperties(type, source[0], flowing[0], block, bucket, properties), behaviour));
        flowing[0] = fluids.register(name + "_flowing", () -> new Flowing(
                flowProperties(type, source[0], flowing[0], block, bucket, properties), behaviour));
        return new Entry(source[0], flowing[0]);
    }

    @Override
    public Collection<BCHolder<Fluid, ? extends Fluid>> getEntries() {
        return fluids.getEntries();
    }

    @Override
    public void subscribe() {
        types.subscribe();
        fluids.subscribe();
    }

    private static FluidType.Properties typeProperties(FluidProperties p) {
        FluidType.Properties props = FluidType.Properties.create()
                .density(p.density())
                .viscosity(p.viscosity())
                .temperature(p.temperature())
                .canExtinguish(p.canExtinguish())
                .canConvertToSource(p.canConvertToSource())
                .canSwim(p.canSwim())
                .canPushEntity(p.canPushEntity())
                .canDrown(p.canDrown())
                //? if >=1.21.10 {
                .isWaterLike(p.waterLike())
                //?}
                ;
        if (p.bucketFill() != null) {
            props.sound(SoundActions.BUCKET_FILL, p.bucketFill());
        }
        if (p.bucketEmpty() != null) {
            props.sound(SoundActions.BUCKET_EMPTY, p.bucketEmpty());
        }
        return props;
    }

    private static BaseFlowingFluid.Properties flowProperties(Supplier<? extends FluidType> type,
            Supplier<? extends Fluid> source, Supplier<? extends Fluid> flowing, Supplier<? extends LiquidBlock> block,
            Supplier<? extends Item> bucket, FluidProperties p) {
        return new BaseFlowingFluid.Properties(type, source, flowing)
                .block(block)
                .bucket(bucket)
                .slopeFindDistance(p.slopeFindDistance())
                .levelDecreasePerBlock(p.levelDecreasePerBlock())
                .tickRate(p.tickRate());
    }

    /** The fluid type: the properties, plus a motion scale read on every use (it may follow a config value). */
    static final class Type extends FluidType {
        private final FluidProperties properties;
        private final DoubleSupplier motionScale;

        Type(FluidProperties properties) {
            super(typeProperties(properties));
            this.properties = properties;
            this.motionScale = properties.motionScale();
        }

        @Override
        public double motionScale(Entity entity) {
            return motionScale.getAsDouble();
        }

        //? if >=26.1.2 {
        /** A fluid that is not water-like wades as {@link ThickFluidMotion}: since 26.1.2 NeoForge's
         *  {@code travelInFluid} moves a modded type that implements no {@code move} neither as water nor as lava,
         *  which held an entity in crude oil in place. Before 26.1.2 it fell back to the water or lava step (and ran
         *  the lava step after a {@code move} that answered true), so those lines keep NeoForge's own. The directive
         *  picks nodes, not patches: the one 26.1 jar also runs on NeoForge 26.1.0 and 26.1.1, where this override is
         *  reached only if their {@code FluidType} dispatches this signature (the 26.1.0 beta's declares
         *  just the {@code FluidState}-first {@code move}, so there it is never called). */
        @Override
        public boolean move(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.phys.Vec3 movementVector,
                double gravity) {
            if (!ThickFluidMotion.isThick(properties)) {
                return false;
            }
            ThickFluidMotion.travel(entity, properties, movementVector, gravity);
            return true;
        }
        //?}
    }

    /** The source form. Its body is the same as {@link Flowing}'s: the two extend different NeoForge classes. */
    public static final class Source extends BaseFlowingFluid.Source implements FluidFlow {
        private final FluidBehaviour behaviour;

        Source(Properties properties, FluidBehaviour behaviour) {
            super(properties);
            this.behaviour = behaviour;
        }

        @Override
        //? if >=1.21.10 {
        public void tick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, state, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        //?} else {
        /*public void tick(Level level, BlockPos pos, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        *///?}

        @Override
        //? if >=1.21.10 {
        protected Map<Direction, FluidState> getSpread(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        //?} else {
        /*protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {*/
        //?}
            Map<Direction, FluidState> map = new java.util.EnumMap<>(super.getSpread(level, pos, state));
            behaviour.adjustSpread(this, level, pos, state, map);
            return map;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
            if (behaviour.refusesReplacement(state, level, pos, fluidIn, direction)) {
                return false;
            }
            return super.canBeReplacedWith(state, level, pos, fluidIn, direction);
        }

        @Override
        public FlowingFluid fluid() {
            return this;
        }

        @Override
        public int dropOffPerBlock(LevelReader level) {
            return getDropOff(level);
        }

        @Override
        public Map<Direction, FluidState> spreadTargets(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getSpread((net.minecraft.server.level.ServerLevel) level, pos, state);
            //?} else {
            /*return getSpread(level, pos, state);*/
            //?}
        }

        @Override
        public void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
            spreadTo(level, pos, state, direction, target);
        }

        @Override
        public FluidState newLiquidAt(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getNewLiquid((net.minecraft.server.level.ServerLevel) level, pos, state);
            //?} else {
            /*return getNewLiquid(level, pos, state);*/
            //?}
        }
    }

    /** The flowing form. Its body is the same as {@link Source}'s: the two extend different NeoForge classes. */
    public static final class Flowing extends BaseFlowingFluid.Flowing implements FluidFlow {
        private final FluidBehaviour behaviour;

        Flowing(Properties properties, FluidBehaviour behaviour) {
            super(properties);
            this.behaviour = behaviour;
        }

        @Override
        //? if >=1.21.10 {
        public void tick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, state, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        //?} else {
        /*public void tick(Level level, BlockPos pos, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        *///?}

        @Override
        //? if >=1.21.10 {
        protected Map<Direction, FluidState> getSpread(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        //?} else {
        /*protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {*/
        //?}
            Map<Direction, FluidState> map = new java.util.EnumMap<>(super.getSpread(level, pos, state));
            behaviour.adjustSpread(this, level, pos, state, map);
            return map;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
            if (behaviour.refusesReplacement(state, level, pos, fluidIn, direction)) {
                return false;
            }
            return super.canBeReplacedWith(state, level, pos, fluidIn, direction);
        }

        @Override
        public FlowingFluid fluid() {
            return this;
        }

        @Override
        public int dropOffPerBlock(LevelReader level) {
            return getDropOff(level);
        }

        @Override
        public Map<Direction, FluidState> spreadTargets(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getSpread((net.minecraft.server.level.ServerLevel) level, pos, state);
            //?} else {
            /*return getSpread(level, pos, state);*/
            //?}
        }

        @Override
        public void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
            spreadTo(level, pos, state, direction, target);
        }

        @Override
        public FluidState newLiquidAt(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getNewLiquid((net.minecraft.server.level.ServerLevel) level, pos, state);
            //?} else {
            /*return getNewLiquid(level, pos, state);*/
            //?}
        }
    }
}
//?}
