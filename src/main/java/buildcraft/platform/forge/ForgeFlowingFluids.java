/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.ForgeRegistries;

import buildcraft.platform.FluidBehaviour;
import buildcraft.platform.FluidFlow;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.ThickFluidMotion;
import buildcraft.platform.registry.BCFluidRegistrar;
import buildcraft.platform.registry.BCHolder;
import buildcraft.platform.registry.BCRegistrar;

/^*
 * The Forge side of {@link BCFluidRegistrar}: a {@link FluidType} built from the {@link FluidProperties} and registered
 * under the fluid's name, then the {@link ForgeFlowingFluid} pair, whose {@code tick}, {@code getSpread} and
 * {@code canBeReplacedWith} overrides hand over to the {@link FluidBehaviour}. The NeoForge twin is
 * {@code NeoForgeFlowingFluids}; this file names the 26.3 line only (the 1.21.1 Forge node, #209 F7, brings its own
 * {@code tick} and {@code getSpread} shapes).
 *
 * <p><b>Registration order.</b> The fluid type registry is Forge's own ({@code forge:fluid_type}, key
 * {@code ForgeRegistries.Keys.FLUID_TYPES}), and Forge fires its register event after every vanilla registry's, the
 * fluid registry included, where NeoForge registers the types first. Nothing here needs a type while the fluids
 * register: every reference between the types, the two fluids, the block and the bucket is a supplier that the first
 * use resolves, and a type's description id is read from its registry key on first ask. The registrar that
 * {@link BCRegistrar#create} returns for the type key must be a {@code DeferredRegister} over that key (or equivalent).
 *
 * <p><b>The water-like flag is a set of mixins.</b> Forge's fluid type has no equivalent of NeoForge's
 * {@code isWaterLike}, and BuildCraft's fluids are in the {@code minecraft:water} tag (data), which Forge's entity physics
 * still reads beside its per-type tracking. The flag is served by {@link ForgeFluidPhysics} and the mixins
 * {@code EntityMixin}, {@code EntityFluidInteractionMixin}, {@code FloatGoalMixin} and {@code SwimMixin} in
 * {@code platform.forge.mixin}; the rest of what NeoForge reads from the type (current, wading, swimming, drowning,
 * boating, item bobbing) Forge asks of {@link Type} itself, and thick-fluid wading is {@link Type#move}.
 ^/
public final class ForgeFlowingFluids implements BCFluidRegistrar {
    private final BCRegistrar<FluidType> types;
    private final BCRegistrar<Fluid> fluids;

    ForgeFlowingFluids(String namespace) {
        types = BCRegistrar.create(ForgeRegistries.Keys.FLUID_TYPES, namespace);
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
        // Forge has no per-boat tick event: the drag on a boat that sails a sticky fluid rides on the level's tick.
        ForgeBoatFluids.register();
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
                .supportsBoating(p.supportsBoating());
        // FluidProperties#waterLike (NeoForge's Properties.isWaterLike) has no Forge property: ForgeFluidPhysics and its
        // mixins read it off the declared properties (see the class comment).
        if (p.bucketFill() != null) {
            props.sound(SoundActions.BUCKET_FILL, p.bucketFill());
        }
        if (p.bucketEmpty() != null) {
            props.sound(SoundActions.BUCKET_EMPTY, p.bucketEmpty());
        }
        return props;
    }

    private static ForgeFlowingFluid.Properties flowProperties(Supplier<? extends FluidType> type,
            Supplier<? extends Fluid> source, Supplier<? extends Fluid> flowing, Supplier<? extends LiquidBlock> block,
            Supplier<? extends Item> bucket, FluidProperties p) {
        return new ForgeFlowingFluid.Properties(type, source, flowing)
                .block(block)
                .bucket(bucket)
                .slopeFindDistance(p.slopeFindDistance())
                .levelDecreasePerBlock(p.levelDecreasePerBlock())
                .tickRate(p.tickRate());
    }

    /^*
     * The fluid type: the properties, plus a motion scale read on every use (it may follow a config value). Whether a
     * boat floats on it is Forge's own {@code supportsBoating}, set from the properties (Forge's boat patches ask the
     * fluid type, so no boat mixin is needed to float, as on Fabric); how fast it sails is
     * {@link buildcraft.platform.BoatFluidMotion}'s.
     ^/
    public static final class Type extends FluidType {
        private final FluidProperties properties;
        private final DoubleSupplier motionScale;
        // The consumer Forge hands initializeClient, kept for the client slice. No initializer on purpose: FluidType's
        // constructor calls initializeClient before this class's own field initializers would run, and an initializer
        // (even "= null") would run after it and discard the consumer.
        private Consumer<IClientFluidTypeExtensions> clientExtensions;

        Type(FluidProperties properties) {
            super(typeProperties(properties));
            this.properties = properties;
            this.motionScale = properties.motionScale();
        }

        /^* What this type was built from, for {@link ForgeFluids#declared}. ^/
        FluidProperties properties() {
            return properties;
        }

        @Override
        public double motionScale(Entity entity) {
            return motionScale.getAsDouble();
        }

        /^*
         * A fluid that is not water-like wades as {@link ThickFluidMotion}. Forge's {@code LivingEntity.travelInFluid}
         * asks the fluid type to move a living entity that is in it (whatever the fluid's tags), and returning false
         * leaves vanilla's water or lava step; so, as NeoForge 26.1.2+, this answers true for a thick fluid.
         *
         * <p>The last parameter is named {@code gravity} by Forge but the vanilla patch passes the entity's old Y there
         * ({@code moveInFluid(fluidstate, input, oldY)}), so it is ignored: the gravity is vanilla's
         * {@code LivingEntity.getEffectiveGravity}, which is protected and is replayed here.
         ^/
        @Override
        public boolean move(FluidState state, LivingEntity entity, Vec3 movementVector, double ignoredOldY) {
            if (!ThickFluidMotion.isThick(properties)) {
                return false;
            }
            ThickFluidMotion.travel(entity, properties, movementVector, effectiveGravity(entity));
            return true;
        }

        /^* {@code LivingEntity.getEffectiveGravity}: the gravity, at most 0.01 while falling under slow falling. ^/
        private static double effectiveGravity(LivingEntity entity) {
            boolean falling = entity.getDeltaMovement().y <= 0.0D;
            return falling && entity.hasEffect(MobEffects.SLOW_FALLING)
                    ? Math.min(entity.getGravity(), 0.01D)
                    : entity.getGravity();
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            clientExtensions = consumer;
        }

        /^*
         * The hook for the client slice: gives this type its render extensions (textures, tint, fog), as NeoForge's
         * {@code RegisterClientExtensionsEvent.registerFluidType} does. Forge has no such event: the consumer exists only
         * on a client (FluidType's constructor asks for it there and nowhere else), so a server or a datagen run has
         * nothing to attach to. Call it from client code only.
         *
         * @return whether the extensions were attached (false off a client)
         ^/
        public boolean attachClientExtensions(IClientFluidTypeExtensions extensions) {
            if (clientExtensions == null) {
                return false;
            }
            clientExtensions.accept(extensions);
            return true;
        }
    }

    /^* The source form. Its body is the same as {@link Flowing}'s: the two extend different Forge classes. ^/
    public static final class Source extends ForgeFlowingFluid.Source implements FluidFlow {
        private final FluidBehaviour behaviour;

        Source(ForgeFlowingFluid.Properties properties, FluidBehaviour behaviour) {
            super(properties);
            this.behaviour = behaviour;
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
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn,
                Direction direction) {
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
            return getSpread((ServerLevel) level, pos, state);
            //?} else {
            /^return getSpread(level, pos, state);^/
            //?}
        }

        @Override
        public void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                FluidState target) {
            spreadTo(level, pos, state, direction, target);
        }

        @Override
        public FluidState newLiquidAt(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getNewLiquid((ServerLevel) level, pos, state);
            //?} else {
            /^return getNewLiquid(level, pos, state);^/
            //?}
        }
    }

    /^* The flowing form. Its body is the same as {@link Source}'s: the two extend different Forge classes. ^/
    public static final class Flowing extends ForgeFlowingFluid.Flowing implements FluidFlow {
        private final FluidBehaviour behaviour;

        Flowing(ForgeFlowingFluid.Properties properties, FluidBehaviour behaviour) {
            super(properties);
            this.behaviour = behaviour;
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
        protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn,
                Direction direction) {
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
            return getSpread((ServerLevel) level, pos, state);
            //?} else {
            /^return getSpread(level, pos, state);^/
            //?}
        }

        @Override
        public void spreadInto(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
                FluidState target) {
            spreadTo(level, pos, state, direction, target);
        }

        @Override
        public FluidState newLiquidAt(Level level, BlockPos pos, BlockState state) {
            //? if >=1.21.10 {
            return getNewLiquid((ServerLevel) level, pos, state);
            //?} else {
            /^return getNewLiquid(level, pos, state);^/
            //?}
        }
    }
}
*///?}
