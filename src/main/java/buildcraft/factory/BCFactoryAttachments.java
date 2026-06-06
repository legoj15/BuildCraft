/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.factory;

import java.util.function.Supplier;

//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
//? if >=1.21.10 {
import net.neoforged.neoforge.common.util.ValueIOSerializable;
//?}
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import buildcraft.energy.BCEnergyFluids;

public class BCFactoryAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BCFactory.MODID);

    /** Per-player cumulative production of every BuildCraft oil and fuel, keyed by
     *  {@link BCEnergyFluids#BASE_NAMES} index. Each counter is clamped at one tank's
     *  worth (16,000 mB) — once all ten are full the {@code refine_and_redefine}
     *  advancement fires. Persisted across save/load and copied on respawn so death
     *  does not reset progress. */
    public static final Supplier<AttachmentType<OilAndFuelProduction>> OIL_AND_FUEL_PRODUCTION =
        ATTACHMENTS.register("oil_and_fuel_production",
            () -> AttachmentType.serializable(OilAndFuelProduction::new).copyOnDeath().build());

    public static void init(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /**
     * Counters for the {@code refine_and_redefine} advancement, one per entry in
     * {@link BCEnergyFluids#BASE_NAMES}. Each counter accumulates mB produced (or
     * pumped, in crude oil's case) of any heat tier of that base name and saturates
     * at {@link #PER_FLUID_TARGET}. The advancement is granted when every counter
     * is saturated.
     */
    //? if >=1.21.10 {
    public static final class OilAndFuelProduction implements ValueIOSerializable {
    //?} else {
    /*public static final class OilAndFuelProduction implements net.neoforged.neoforge.common.util.INBTSerializable<net.minecraft.nbt.CompoundTag> {*/
    //?}
        public static final int PER_FLUID_TARGET = 16_000;

        /** Parallel array to {@link BCEnergyFluids#BASE_NAMES}; values in [0, PER_FLUID_TARGET]. */
        private final int[] amounts = new int[BCEnergyFluids.BASE_NAMES.size()];

        /**
         * Adds {@code mb} to the counter for {@code baseName}, clamped at the per-fluid
         * target. Returns {@code baseName} iff this call pushed that counter from
         * below-target to saturated — the rising edge that should award the matching
         * criterion on the advancement (one criterion per base name lets the
         * advancement screen show "x/10" progress, as colorful_electrician does for
         * dye colours). Returns {@code null} for unknown base names (other-mod fluids,
         * vanilla water/lava that should never feed this tracker) and for no-op calls
         * (already saturated, or mb &le; 0).
         */
        public String recordProduction(String baseName, int mb) {
            if (baseName == null || mb <= 0) return null;
            int index = BCEnergyFluids.BASE_NAMES.indexOf(baseName);
            if (index < 0) return null;
            if (amounts[index] >= PER_FLUID_TARGET) return null;
            amounts[index] = Math.min(PER_FLUID_TARGET, amounts[index] + mb);
            return amounts[index] >= PER_FLUID_TARGET ? baseName : null;
        }

        /** True when every base name has reached {@link #PER_FLUID_TARGET}. */
        public boolean isComplete() {
            for (int amount : amounts) {
                if (amount < PER_FLUID_TARGET) return false;
            }
            return true;
        }

        /** Read-only view of the current counter for {@code baseName}, or {@code -1}
         *  if the name is not one of {@link BCEnergyFluids#BASE_NAMES}. */
        public int get(String baseName) {
            int index = BCEnergyFluids.BASE_NAMES.indexOf(baseName);
            return index < 0 ? -1 : amounts[index];
        }

        //? if >=1.21.10 {
        @Override
        public void serialize(ValueOutput output) {
            // Single packed array keyed by BASE_NAMES order. Storing under each
            // base name as separate keys would survive list reordering but BASE_NAMES
            // is a canonical, code-controlled order — reordering it is a breaking
            // change anyway, so the array form is fine and is denser.
            output.putIntArray("amounts", amounts.clone());
        }

        @Override
        public void deserialize(ValueInput input) {
            int[] saved = input.getIntArray("amounts").orElse(null);
            if (saved == null) return;
            // Defensive copy bounded by amounts.length so adding a new base fluid in a
            // future version does not lose progress (old saves shorter), and removing
            // one does not crash on read (old saves longer, extra entries discarded).
            int n = Math.min(saved.length, amounts.length);
            for (int i = 0; i < n; i++) {
                amounts[i] = Math.min(PER_FLUID_TARGET, Math.max(0, saved[i]));
            }
        }
        //?} else {
        /*@Override
        public net.minecraft.nbt.CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putIntArray("amounts", amounts.clone());
            return tag;
        }

        @Override
        public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, net.minecraft.nbt.CompoundTag nbt) {
            // 1.21.1 CompoundTag.getIntArray returns the array directly (empty if absent).
            int[] saved = nbt.getIntArray("amounts");
            int n = Math.min(saved.length, amounts.length);
            for (int i = 0; i < n; i++) {
                amounts[i] = Math.min(PER_FLUID_TARGET, Math.max(0, saved[i]));
            }
        }*/
        //?}
    }
}
