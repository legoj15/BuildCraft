/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import buildcraft.platform.FluidProperties;

/^*
 * The entity physics NeoForge reads from a {@code FluidType}, for BuildCraft's fluids on Fabric. Vanilla's entity
 * physics knows only the water and lava tags, and every BuildCraft fluid is in {@code minecraft:water} (the oil's
 * spread rules need that), so without this an entity treats them all as water. With it, as on NeoForge:
 * <ul>
 * <li>an entity is "in water" ({@code Entity.isInWater}, so swimming, bobbing, water movement, the splash and the
 * fall-distance reset) and its eyes are "in water" only in a fluid whose {@link FluidProperties#waterLike} is set
 * (NeoForge's {@code getIsWaterLike} checks in {@code Entity.baseTick} and {@code updateFluidInteraction});</li>
 * <li>a mob's {@code FloatGoal} keeps it afloat only in a fluid it can swim in ({@link FluidProperties#canSwim}),
 * deeper than its jump threshold (any other fluid is judged by the goal's own floatable tag, as vanilla does);</li>
 * <li>a dropped item bobs in any BuildCraft fluid deeper than a tenth of a block, water-like or not (NeoForge's
 * {@code FluidType.setItemMovement} in {@code ItemEntity.tick}; {@code ItemEntityMixin});</li>
 * <li>a boat floats on a BuildCraft fluid only where {@link FluidProperties#supportsBoating} is set, as NeoForge's
 * {@code canBoatInFluid} answers from the fluid type (every liquid, no gas; {@code AbstractBoatMixin}), and sails on a
 * sticky one at its {@link FluidProperties#boatSpeed} through {@link buildcraft.platform.BoatFluidMotion};</li>
 * <li>it drowns with its eyes in a fluid whose {@link FluidProperties#canDrown} is set ({@code onLivingBreathe});</li>
 * <li>a current pushes it only where {@link FluidProperties#canPushEntity} is set, scaled by that fluid's own
 * {@link FluidProperties#motionScale} rather than water's 0.014 (NeoForge's per-type current accumulators);</li>
 * <li>in a fluid that is not water-like, its fall distance is halved every tick (NeoForge's default
 * {@code fallDistanceModifier});</li>
 * <li>a living entity in a fluid that is not water-like (crude oil, residue) wades through it by
 * {@link buildcraft.platform.ThickFluidMotion}, the movement step the NeoForge fluid type's {@code FluidType.move}
 * takes, and a jumping one off the ground (or on the ground deeper than its jump threshold) rises by NeoForge's
 * {@code jumpInFluid} lift of {@link #FLUID_JUMP_SPEED} a tick, and a ridden {@code can_float_while_ridden} mount
 * then takes vanilla 26.3's {@code floatInLiquidWhileRidden} lift, as NeoForge 26.3 runs it after the type's
 * {@code move} ({@code LivingEntityMixin});</li>
 * <li>a rider is thrown off a {@code dismounts_underwater} mount only with its eyes in a water-tag fluid that is not
 * BuildCraft's (NeoForge's {@code canBeRiddenUnderFluidType}; {@code LivingEntityMixin}).</li>
 * </ul>
 * The rest of vanilla's tag queries ({@code getFluidHeight(WATER)}, {@code isEyeInFluid(WATER)} elsewhere) stay tag
 * based, as NeoForge's deprecated tag paths are. Extinguishing is the fluid's own {@code entityInside}. Vanilla 26.3
 * tracks each fluid apart, and the answers above read its trackers; 26.1 and 26.2 track one per tag (water, lava),
 * which cannot tell oil from water, so there they read {@link FluidTracking}, BuildCraft's per-fluid record of the
 * same scan.
 *^/
public final class FabricFluidPhysics {
    /^* NeoForge's default {@code FluidType.Properties.fallDistanceModifier}, which no BuildCraft fluid changes. *^/
    public static final float FALL_DISTANCE_MODIFIER = 0.5F;
    /^* NeoForge's {@code jumpInFluid} lift per tick for a fluid that is not water (no swim-speed attribute). *^/
    public static final double FLUID_JUMP_SPEED = 0.04F;

    private FabricFluidPhysics() {}

    /^* What {@code EntityFluidInteractionMixin} adds to an entity's {@code EntityFluidInteraction}. *^/
    public interface Interaction {
        /^* Whether the entity is in a water-like fluid (NeoForge: any tracked type that {@code getIsWaterLike}). *^/
        boolean buildcraft$inWaterLike();

        /^* Whether the entity's eyes are in a water-like fluid. *^/
        boolean buildcraft$eyeInWaterLike();

        /^* Whether the entity is deeper than {@code depth} in a fluid it can swim in (NeoForge's {@code FloatGoal}): a
         *  BuildCraft fluid by its own flag, any other by {@code floatable}, the tag the goal asks about. *^/
        boolean buildcraft$inSwimmableDeeperThan(double depth, TagKey<Fluid> floatable);

        /^* Whether the entity is in any BuildCraft fluid (either form of any of them). *^/
        boolean buildcraft$inBuildCraftFluid();

        /^* Whether the entity is in {@code fluid}'s kind (either form), NeoForge's {@code isInFluidType}. *^/
        boolean buildcraft$inKindOf(FluidProperties fluid);

        /^* Whether the entity's eyes are in a fluid a boat floats on (NeoForge's {@code canBoatInFluid}): a water-tag
         *  fluid that is not BuildCraft's, or a BuildCraft fluid that {@link FluidProperties#supportsBoating}. *^/
        boolean buildcraft$eyeInBoatableWater();

        /^* Whether the entity's eyes are in a water-tag fluid that is not BuildCraft's: the only kind that throws a rider
         *  off a {@code dismounts_underwater} mount (NeoForge's {@code canBeRiddenUnderFluidType}, true for every fluid
         *  type but water, whether or not boats float on it). *^/
        boolean buildcraft$eyeInForeignWater();

        /^* Whether the entity's eyes are in a fluid it can drown in. *^/
        boolean buildcraft$eyeInDrowningFluid();

        /^* The fall-distance factor for this tick: {@link #FALL_DISTANCE_MODIFIER} in a BuildCraft fluid that is not
         *  water-like, else 1. *^/
        float buildcraft$fallDistanceModifier();

        /^* Pushes the entity along the currents of the BuildCraft fluids it touched this tick, each by its own
         *  motion scale. *^/
        void buildcraft$applyCurrents(Entity entity);
    }

    /^* Whether a tracked fluid counts as water for the entity: its declared flag for a BuildCraft fluid, else the tag. *^/
    public static boolean isWaterLike(Holder<Fluid> fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.waterLike() : fluid.is(FluidTags.WATER);
    }

    /^* Whether an entity can swim in a tracked fluid: its declared flag for a BuildCraft fluid, else whether it is in
     *  {@code floatable}, the tag the asking goal reads (vanilla's own test, which a datapack may have widened). *^/
    public static boolean canSwimIn(Holder<Fluid> fluid, TagKey<Fluid> floatable) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.canSwim() : fluid.is(floatable);
    }

    /^* Whether a boat floats on a water-tag fluid: any fluid but a BuildCraft one that declares no boating (a gas). *^/
    public static boolean boatsFloatOn(Fluid fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid);
        return declared == null || declared.supportsBoating();
    }

    /^* Whether an entity can drown with its eyes in a tracked fluid: its declared flag, else the water tag. *^/
    public static boolean canDrownIn(Holder<Fluid> fluid) {
        FluidProperties declared = FabricFlowingFluids.declared(fluid.value());
        return declared != null ? declared.canDrown() : fluid.is(FluidTags.WATER);
    }

    /^*
     * The per-fluid side of vanilla's fluid scan where {@code EntityFluidInteraction} tracks per tag (26.1, 26.2): for
     * each fluid (each form apart) the scan reached this tick, its surface height above the entity's feet and whether
     * the entity's eyes are in it, which 26.3's per-fluid trackers hold. Entries are reused from tick to tick.
     *^/
    public static final class FluidTracking {
        private final List<Entry> entries = new ArrayList<>(2);
        private int count;
        /^* The entry the last block went to: the scan meets the same fluid block after block. *^/
        @Nullable
        private Entry last;

        public void reset() {
            count = 0;
            last = null;
        }

        /^* One scanned block of {@code fluid}, its surface {@code height} above the entity's feet. *^/
        public void record(Holder<Fluid> fluid, double height, boolean eyesInside) {
            Entry entry = last != null && last.fluid == fluid ? last : entryFor(fluid);
            entry.height = Math.max(entry.height, height);
            entry.eyesInside |= eyesInside;
            last = entry;
        }

        private Entry entryFor(Holder<Fluid> fluid) {
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                if (entry.fluid == fluid) {
                    return entry;
                }
            }
            if (count == entries.size()) {
                entries.add(new Entry());
            }
            Entry entry = entries.get(count++);
            entry.fluid = fluid;
            entry.height = 0.0;
            entry.eyesInside = false;
            return entry;
        }

        public int count() {
            return count;
        }

        public Holder<Fluid> fluid(int index) {
            return entries.get(index).fluid;
        }

        public double height(int index) {
            return entries.get(index).height;
        }

        public boolean eyesInside(int index) {
            return entries.get(index).eyesInside;
        }

        private static final class Entry {
            private Holder<Fluid> fluid;
            private double height;
            private boolean eyesInside;
        }
    }

    /^*
     * One BuildCraft fluid's current this tick, the same sum vanilla's {@code EntityFluidInteraction.CurrentAccumulator}
     * keeps for water: the flow of every touched block (scaled down while the fluid is under 0.4 deep), applied as
     * a push of the fluid's motion scale.
     *^/
    public static final class Current {
        private double height;
        private Vec3 accumulated = Vec3.ZERO;
        private int count;

        public void reset() {
            height = 0.0;
            accumulated = Vec3.ZERO;
            count = 0;
        }

        /^* Adds one block's flow, with the fluid's surface {@code depth} above the entity's feet. *^/
        public void accumulate(double depth, Vec3 flow) {
            height = Math.max(height, depth);
            accumulated = accumulated.add(height < 0.4 ? flow.scale(height) : flow);
            count++;
        }

        public void applyTo(Entity entity, double scale) {
            if (count == 0 || accumulated.lengthSqr() < 1.0E-5F) {
                return;
            }
            Vec3 impulse = entity instanceof Player ? accumulated.scale(1.0 / count) : accumulated.normalize();
            Vec3 movement = entity.getDeltaMovement();
            impulse = impulse.scale(scale);
            if (Math.abs(movement.x) < 0.003 && Math.abs(movement.z) < 0.003 && impulse.length() < 0.0045000000000000005) {
                impulse = impulse.normalize().scale(0.0045000000000000005);
            }
            entity.addDeltaMovement(impulse);
        }
    }
}
*///?}
