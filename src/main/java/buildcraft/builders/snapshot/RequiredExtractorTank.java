/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.RegistryUtilBC;

/**
 * Prices the fluid a tile or entity holds at {@link #path}, in whichever of the three shapes a tank saves:
 * <ul>
 *   <li>one {@code FluidStack.CODEC} compound (a distiller's {@code fluidIn});</li>
 *   <li>a list of them (NeoForge's transfer handlers save {@code stacks}, an empty slot as an empty compound);</li>
 *   <li>a fluid id string, with its amount at {@link #amountPath} (an iron engine's {@code fuelFluid} and
 *       {@code fuelAmount}, a 1.21.1 tank's {@code fluid0} and {@code amount0}).</li>
 * </ul>
 * A path that finds nothing prices nothing, so a rule may name every node's shape side by side.
 */
public class RequiredExtractorTank extends RequiredExtractor {
    private NbtPath path = null;
    /** Only for the id-string shape: where the amount in mB lives. */
    private NbtPath amountPath = null;

    @Override
    public boolean pricesTileContents() {
        return path != null;
    }

    private static FluidStack parseFluidStack(CompoundTag ct) {
        if (ct.contains("Empty")) return FluidStack.EMPTY;
        return FluidStack.CODEC.parse(NBTUtilBC.registryAwareOps(), ct).resultOrPartial().orElse(FluidStack.EMPTY);
    }

    private List<FluidStack> extract(CompoundTag nbt) {
        Tag found = path.get(nbt);
        List<FluidStack> fluids = new ArrayList<>();
        if (found instanceof CompoundTag ct) {
            fluids.add(parseFluidStack(ct));
        } else if (found instanceof ListTag) {
            NBTUtilBC.readCompoundList(found).map(RequiredExtractorTank::parseFluidStack).forEach(fluids::add);
        } else if (amountPath != null) {
            Identifier id = Identifier.tryParse(readString(found));
            int amount = readInt(amountPath.get(nbt), 0);
            if (id != null && amount > 0 && BuiltInRegistries.FLUID.containsKey(id)) {
                Fluid fluid = RegistryUtilBC.getValue(BuiltInRegistries.FLUID, id);
                fluids.add(new FluidStack(fluid, amount));
            }
        }
        fluids.removeIf(FluidStack::isEmpty);
        return fluids;
    }

    @Nonnull
    @Override
    public List<FluidStack> extractFluidsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        if (tileNbt == null || path == null) return Collections.emptyList();
        return extract(tileNbt);
    }

    @Nonnull
    @Override
    public List<FluidStack> extractFluidsFromEntity(@Nonnull CompoundTag entityNbt) {
        if (path == null) return Collections.emptyList();
        return extract(entityNbt);
    }
}
