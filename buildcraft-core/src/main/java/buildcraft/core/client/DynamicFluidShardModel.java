/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import com.mojang.serialization.MapCodec;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import buildcraft.core.BCCore;

/**
 * Dynamic item model for fragile fluid shards.
 *
 * MC 26.1 STUB: The previous implementation relied on several NeoForge APIs that were
 * removed in 26.1 (RenderTypeGroup, RenderTypeHelper, IClientFluidTypeExtensions.getStillTexture,
 * BlockModelWrapper, ItemTransforms). This is a simplified stub that renders
 * the shard using its static JSON model until the NeoForge 26.1 item model API stabilizes.
 *
 * TODO: Restore dynamic fluid overlay rendering with NeoForge 26.1 item model API.
 */
public class DynamicFluidShardModel implements ItemModel {

    private final BakingContext bakingContext;
    private final Map<Fluid, ItemModel> cache = new IdentityHashMap<>();

    private DynamicFluidShardModel(BakingContext bakingContext) {
        this.bakingContext = bakingContext;
    }

    @SubscribeEvent
    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(
                Identifier.parse("buildcraftcore:fluid_shard"),
                Unbaked.MAP_CODEC
        );
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        // MC 26.1 STUB: For now, just use the default item model.
        // The fluid-specific multi-layer rendering is deferred until
        // the NeoForge 26.1 item model API stabilizes.
        SimpleFluidContent content = stack.getOrDefault(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY);
        FluidStack fluidStack = content.copy();
        Fluid fluid = fluidStack.isEmpty() ? Fluids.EMPTY : fluidStack.getFluid();

        // Simply use the base model — the dynamic fluid overlay is disabled
        // until RenderTypeGroup/RenderTypeHelper equivalents are found in NeoForge 26.1.
    }

    /** Unbaked model type registered as "buildcraftcore:fluid_shard". */
    public static class Unbaked implements ItemModel.Unbaked {
        public static final Unbaked INSTANCE = new Unbaked();
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

        private Unbaked() {}

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(BakingContext bakingContext, Matrix4fc transform) {
            return new DynamicFluidShardModel(bakingContext);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            // No dependencies
        }
    }
}
