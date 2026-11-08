/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import java.util.function.DoubleSupplier;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvent;

/**
 * What a BuildCraft fluid declares about itself at registration, in the loader-neutral form the fluid registrar
 * ({@code BCFluidRegistrar}) turns into the loader's own fluid description (NeoForge: a {@code FluidType} plus the
 * {@code BaseFlowingFluid} flow parameters; Fabric: the fluid pair plus its {@code FluidVariantAttributeHandler}).
 *
 * <p>Units follow NeoForge's {@code FluidType}: density in kg/m³ (water 1000; zero or less is lighter than air),
 * viscosity in its arbitrary unit (water 1000, lava 6000), temperature in kelvin (water 300). {@link #motionScale} and
 * {@link #boatSpeed} are read on every use, so they may follow a config value.
 *
 * @param density             density; {@link #lighterThanAir()} when zero or negative
 * @param viscosity           viscosity (water 1000)
 * @param temperature         temperature in kelvin
 * @param canSwim             whether an entity can swim in it
 * @param canDrown            whether an entity can drown in it
 * @param canPushEntity       whether its current pushes entities
 * @param canExtinguish       whether it puts out burning entities
 * @param canConvertToSource  whether two sources make a third (water's infinite-source rule)
 * @param waterLike           whether entities in it count as in water (1.21.10+ on NeoForge; ignored on 1.21.1)
 * @param supportsBoating     whether a boat floats on it (NeoForge: {@code canBoatInFluid}; Fabric: whether its boat
 *                            mixin lets vanilla's water-tag boat tests count the fluid)
 * @param motionScale         the scalar applied to the flow vector carrying an entity along a current (water 0.014)
 * @param boatSpeed           the factor on a boat's top speed on it (water 1; see {@link BoatFluidMotion}), for a fluid
 *                            that {@link #supportsBoating}
 * @param bucketFill          the sound a bucket makes picking it up, or null for none
 * @param bucketEmpty         the sound a bucket makes pouring it out, or null for none
 * @param slopeFindDistance   how far the flowing form looks for a slope (water 4)
 * @param levelDecreasePerBlock how much the level drops per block flowed (water 1)
 * @param tickRate            ticks between flow updates (water 5)
 */
public record FluidProperties(int density, int viscosity, int temperature, boolean canSwim, boolean canDrown,
        boolean canPushEntity, boolean canExtinguish, boolean canConvertToSource, boolean waterLike,
        boolean supportsBoating, DoubleSupplier motionScale, DoubleSupplier boatSpeed,
        @Nullable SoundEvent bucketFill, @Nullable SoundEvent bucketEmpty,
        int slopeFindDistance, int levelDecreasePerBlock, int tickRate) {

    /** Whether the fluid rises rather than falls (NeoForge: {@code FluidType#isLighterThanAir}, density at most 0). */
    public boolean lighterThanAir() {
        return density <= 0;
    }

    /** A builder starting from NeoForge's {@code FluidType.Properties} and {@code BaseFlowingFluid.Properties}
     *  defaults. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link FluidProperties}; every setter returns the builder. */
    public static final class Builder {
        private int density = 1000;
        private int viscosity = 1000;
        private int temperature = 300;
        private boolean canSwim = true;
        private boolean canDrown = true;
        private boolean canPushEntity = true;
        private boolean canExtinguish = false;
        private boolean canConvertToSource = false;
        private boolean waterLike = false;
        private boolean supportsBoating = false;
        private DoubleSupplier motionScale = () -> 0.014D;
        private DoubleSupplier boatSpeed = () -> 1.0D;
        private SoundEvent bucketFill;
        private SoundEvent bucketEmpty;
        private int slopeFindDistance = 4;
        private int levelDecreasePerBlock = 1;
        private int tickRate = 5;

        private Builder() {}

        public Builder density(int density) {
            this.density = density;
            return this;
        }

        public Builder viscosity(int viscosity) {
            this.viscosity = viscosity;
            return this;
        }

        public Builder temperature(int temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder canSwim(boolean canSwim) {
            this.canSwim = canSwim;
            return this;
        }

        public Builder canDrown(boolean canDrown) {
            this.canDrown = canDrown;
            return this;
        }

        public Builder canPushEntity(boolean canPushEntity) {
            this.canPushEntity = canPushEntity;
            return this;
        }

        public Builder canExtinguish(boolean canExtinguish) {
            this.canExtinguish = canExtinguish;
            return this;
        }

        public Builder canConvertToSource(boolean canConvertToSource) {
            this.canConvertToSource = canConvertToSource;
            return this;
        }

        public Builder waterLike(boolean waterLike) {
            this.waterLike = waterLike;
            return this;
        }

        public Builder supportsBoating(boolean supportsBoating) {
            this.supportsBoating = supportsBoating;
            return this;
        }

        public Builder motionScale(DoubleSupplier motionScale) {
            this.motionScale = motionScale;
            return this;
        }

        public Builder boatSpeed(DoubleSupplier boatSpeed) {
            this.boatSpeed = boatSpeed;
            return this;
        }

        public Builder bucketSounds(@Nullable SoundEvent fill, @Nullable SoundEvent empty) {
            this.bucketFill = fill;
            this.bucketEmpty = empty;
            return this;
        }

        public Builder slopeFindDistance(int slopeFindDistance) {
            this.slopeFindDistance = slopeFindDistance;
            return this;
        }

        public Builder levelDecreasePerBlock(int levelDecreasePerBlock) {
            this.levelDecreasePerBlock = levelDecreasePerBlock;
            return this;
        }

        public Builder tickRate(int tickRate) {
            this.tickRate = tickRate;
            return this;
        }

        public FluidProperties build() {
            return new FluidProperties(density, viscosity, temperature, canSwim, canDrown, canPushEntity,
                    canExtinguish, canConvertToSource, waterLike, supportsBoating, motionScale, boatSpeed,
                    bucketFill, bucketEmpty, slopeFindDistance, levelDecreasePerBlock, tickRate);
        }
    }
}
