/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.EnumMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
//? if >=1.21.11 {
import net.minecraft.util.Util;
//?} else {
/^import net.minecraft.Util;
^///?}
import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
//?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

import buildcraft.platform.FluidBehaviour;
import buildcraft.platform.FluidFlow;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;

/^*
 * {@link BCFluidRegistrar} on Fabric: a vanilla {@link FlowingFluid} pair written to match NeoForge's
 * {@code BaseFlowingFluid} (same flow parameters, block drops, bucket and pickup sound, water's replace rule), with the
 * {@link FluidBehaviour} hooked into {@code tick}, {@code getSpread} and {@code canBeReplacedWith} as on NeoForge, and
 * a {@link FluidVariantAttributeHandler} carrying the name, temperature, viscosity, gas flag and bucket sounds.
 *
 * <p>Fabric has no fluid type, so the entity physics NeoForge reads from it (the water-like flag, drowning, the
 * current's push and {@code motionScale}, the fall-distance damping) come from the declared properties through
 * {@link FabricFluidPhysics} and its mixins; extinguishing is {@link Base#entityInside}. BuildCraft's own reads
 * ({@link FabricFluids#viscosity} and friends) answer from the declared properties too.
 ^/
public final class FabricFlowingFluids implements BCFluidRegistrar {
    /^* The properties every BuildCraft fluid was registered with, both forms, for {@link FabricFluids}'s reads. ^/
    static final Map<Fluid, FluidProperties> DECLARED = new ConcurrentHashMap<>();

    private final BCRegistrar<Fluid> fluids;

    FabricFlowingFluids(String namespace) {
        fluids = BCRegistrar.create(Registries.FLUID, namespace);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Entry register(String name, FluidProperties properties, FluidBehaviour behaviour,
            Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket) {
        // Fabric registers at once: each fluid names the other through the holder array, read only once in use.
        BCHolder<Fluid, FlowingFluid>[] source = new BCHolder[1];
        BCHolder<Fluid, FlowingFluid>[] flowing = new BCHolder[1];
        Supplier<Fluid> sourceRef = () -> source[0].get();
        Supplier<Fluid> flowingRef = () -> flowing[0].get();
        source[0] = fluids.register(name,
                () -> new Source(properties, behaviour, sourceRef, flowingRef, block, bucket));
        flowing[0] = fluids.register(name + "_flowing",
                () -> new Flowing(properties, behaviour, sourceRef, flowingRef, block, bucket));
        // Named by the key NeoForge's FluidType uses (the heat-tier lang strings), not Fabric's block-name default.
        FluidVariantAttributeHandler attributes = new Attributes(properties,
                Util.makeDescriptionId("fluid_type", source[0].id()));
        for (BCHolder<Fluid, FlowingFluid> fluid : new BCHolder[] {source[0], flowing[0]}) {
            DECLARED.put(fluid.get(), properties);
            FluidVariantAttributes.register(fluid.get(), attributes);
        }
        return new Entry(source[0], flowing[0]);
    }

    /^* The properties a BuildCraft fluid (either form) was registered with, or null for any other fluid. ^/
    @Nullable
    public static FluidProperties declared(Fluid fluid) {
        return fluid instanceof Base base ? base.properties : null;
    }

    @Override
    public Collection<BCHolder<Fluid, ? extends Fluid>> getEntries() {
        return fluids.getEntries();
    }

    @Override
    public void subscribe() {
        // Entries are registered at their register call.
    }

    /^*
     * The fluid-variant attributes of one BuildCraft fluid, from its declared properties and the translation key of its
     * name ({@code fluid_type.<namespace>.<name>}, shared by the source and flowing forms).
     ^/
    private record Attributes(FluidProperties properties, String descriptionId)
            implements FluidVariantAttributeHandler {
        @Override
        public Component getName(FluidVariant variant) {
            // Fabric's default is the fluid's legacy block name, which every heat tier shares ("Oil").
            return Component.translatable(descriptionId);
        }

        @Override
        public Optional<SoundEvent> getFillSound(FluidVariant variant) {
            return Optional.ofNullable(properties.bucketFill());
        }

        @Override
        public Optional<SoundEvent> getEmptySound(FluidVariant variant) {
            return Optional.ofNullable(properties.bucketEmpty());
        }

        @Override
        public int getTemperature(FluidVariant variant) {
            return properties.temperature();
        }

        @Override
        public int getViscosity(FluidVariant variant, Level level) {
            // Fabric's viscosity unit is NeoForge's (FluidConstants.WATER_VISCOSITY is 1000).
            return properties.viscosity();
        }

        @Override
        public boolean isLighterThanAir(FluidVariant variant) {
            return properties.lighterThanAir();
        }
    }

    /^* The body both forms share: NeoForge's {@code BaseFlowingFluid}, plus the behaviour hooks. ^/
    abstract static class Base extends FlowingFluid implements FluidFlow {
        private final FluidProperties properties;
        private final FluidBehaviour behaviour;
        private final Supplier<Fluid> source;
        private final Supplier<Fluid> flowing;
        private final Supplier<? extends LiquidBlock> block;
        private final Supplier<? extends Item> bucket;

        Base(FluidProperties properties, FluidBehaviour behaviour, Supplier<Fluid> source, Supplier<Fluid> flowing,
                Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket) {
            this.properties = properties;
            this.behaviour = behaviour;
            this.source = source;
            this.flowing = flowing;
            this.block = block;
            this.bucket = bucket;
        }

        @Override
        public Fluid getFlowing() {
            return flowing.get();
        }

        @Override
        public Fluid getSource() {
            return source.get();
        }

        @Override
        //? if >=1.21.10 {
        protected boolean canConvertToSource(ServerLevel level) {
        //?} else {
        /^protected boolean canConvertToSource(Level level) {^/
        //?}
            return properties.canConvertToSource();
        }

        @Override
        protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            Block.dropResources(state, level, pos, blockEntity);
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return properties.slopeFindDistance();
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return properties.levelDecreasePerBlock();
        }

        @Override
        public Item getBucket() {
            Item item = bucket.get();
            return item != null ? item : Items.AIR;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn,
                Direction direction) {
            if (behaviour.refusesReplacement(state, level, pos, fluidIn, direction)) {
                return false;
            }
            return direction == Direction.DOWN && !isSame(fluidIn);
        }

        @Override
        public int getTickDelay(LevelReader level) {
            return properties.tickRate();
        }

        //? if >=1.21.10 {
        @Override
        protected void entityInside(Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects) {
            // NeoForge's Fluid.entityInside, with the type's canExtinguish (water's own override is WaterFluid's).
            if (properties.canExtinguish()) {
                effects.apply(InsideBlockEffectType.EXTINGUISH);
            }
        }
        //?}
        // 1.21.1 has no Fluid.entityInside: NeoForge reads canExtinguish in the entity's fluid tick instead, and no
        // BuildCraft fluid sets it, so nothing is lost there.

        @Override
        protected float getExplosionResistance() {
            // NeoForge's BaseFlowingFluid.Properties default.
            return 1.0F;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState state) {
            return block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
        }

        @Override
        public boolean isSame(Fluid fluid) {
            return fluid == source.get() || fluid == flowing.get();
        }

        @Override
        public Optional<SoundEvent> getPickupSound() {
            return Optional.ofNullable(properties.bucketFill());
        }

        @Override
        //? if >=1.21.10 {
        public void tick(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, state, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        //?} else {
        /^public void tick(Level level, BlockPos pos, FluidState fluidState) {
            if (behaviour.beforeTick(this, level, pos, fluidState)) {
                return;
            }
            super.tick(level, pos, fluidState);
            behaviour.afterTick(this, level, pos, fluidState);
        }
        ^///?}

        @Override
        //? if >=1.21.10 {
        protected Map<Direction, FluidState> getSpread(ServerLevel level, BlockPos pos, BlockState state) {
        //?} else {
        /^protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {^/
        //?}
            Map<Direction, FluidState> map = new EnumMap<>(super.getSpread(level, pos, state));
            behaviour.adjustSpread(this, level, pos, state, map);
            return map;
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
            return getSpread((ServerLevel) level, pos, state);
            //?} else {
            /^return getSpread(level, pos, state);
            ^///?}
        }

        @Override
        public void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                FluidState target) {
            spreadTo(level, pos, state, direction, target);
        }

        @Override
        public FluidState newLiquidAt(Level level, BlockPos pos, BlockState state) {
            return getNewLiquid((ServerLevel) level, pos, state);
        }
    }

    /^* The source form. ^/
    static final class Source extends Base {
        Source(FluidProperties properties, FluidBehaviour behaviour, Supplier<Fluid> source, Supplier<Fluid> flowing,
                Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket) {
            super(properties, behaviour, source, flowing, block, bucket);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    /^* The flowing form. ^/
    static final class Flowing extends Base {
        Flowing(FluidProperties properties, FluidBehaviour behaviour, Supplier<Fluid> source, Supplier<Fluid> flowing,
                Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket) {
            super(properties, behaviour, source, flowing, block, bucket);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
*///?}
