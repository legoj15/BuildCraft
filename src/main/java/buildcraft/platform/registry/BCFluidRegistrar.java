/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.registry;

import java.util.Collection;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import buildcraft.platform.FluidBehaviour;
import buildcraft.platform.FluidProperties;
import buildcraft.platform.Platform;

/**
 * Registers flowing fluids (a source and a flowing form) under one namespace, the fluid half of {@link BCRegistrar}.
 * The loader builds its own description of the fluid from {@link FluidProperties} (NeoForge: a {@code FluidType}
 * registered under the same name, then {@code BaseFlowingFluid} subclasses; Fabric: {@code FlowingFluid} subclasses
 * plus a fluid-variant attribute handler) and hooks {@link FluidBehaviour} into the flow code. Both fluids implement
 * {@link buildcraft.platform.FluidFlow}.
 *
 * <p>Ids: the source is {@code namespace:name}, the flowing form {@code namespace:name_flowing}. The block and the
 * bucket are registered by the caller (through {@link BCRegistrar.Blocks} and {@link BCRegistrar.Items}) and handed in
 * as suppliers, resolved only once the fluid is in use. Timing as {@link BCRegistrar}.
 */
public interface BCFluidRegistrar {

    /** A new fluid registrar naming its entries in {@code namespace}. */
    static BCFluidRegistrar create(String namespace) {
        return Platform.fluids().createRegistrar(namespace);
    }

    /**
     * Registers the fluid pair {@code name} / {@code name_flowing}.
     *
     * @param block  the fluid's block, registered by the caller
     * @param bucket the fluid's bucket, registered by the caller
     */
    Entry register(String name, FluidProperties properties, FluidBehaviour behaviour,
            Supplier<? extends LiquidBlock> block, Supplier<? extends Item> bucket);

    /** Every fluid registered so far, in call order (each pair's source, then its flowing form). */
    Collection<BCHolder<Fluid, ? extends Fluid>> getEntries();

    /** Attaches the registrar to the loader, as {@link BCRegistrar#subscribe()}. */
    void subscribe();

    /** The two registered fluids of one {@link #register} call. */
    record Entry(BCHolder<Fluid, FlowingFluid> source, BCHolder<Fluid, FlowingFluid> flowing) {}
}
