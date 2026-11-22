/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && <1.21.10 {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.Platform;

/^*
 * The {@code buildcraftunofficial:fluid_container} classic model loader on 1.21.1 (Forge 52), which BuildCraft's fluid
 * containers name once the build renames the 1.21.1 down-port's {@code neoforge:fluid_container} (DataForge step 5).
 * It is Forge's own {@code DynamicFluidContainerModel}, read from the same JSON fields and baked the same way, with one
 * change: the fluid a stack holds is read through {@code Platform.transfer().getFluidContained} rather than Forge's
 * {@code FluidUtil.getFluidContained}, which on Forge 52 never finds an item's fluid handler (its
 * {@code FluidUtil.getFluidHandler(ItemStack)} always answers empty). Without that, the fragile fluid shard, whose JSON
 * names no fluid, would never show its contents. Containers whose JSON names their fluid (the buckets) draw the same
 * either way. A per-fluid model is baked on first sight and kept until the next resource reload, as Forge's is.
 *^/
final class ForgeFluidContainerGeometry implements IUnbakedGeometry<ForgeFluidContainerGeometry> {
    /^* The loader's path; Forge files it under the registering mod's namespace. *^/
    static final String NAME = "fluid_container";

    static final IGeometryLoader<ForgeFluidContainerGeometry> LOADER =
            (json, context) -> new ForgeFluidContainerGeometry(DynamicFluidContainerModel.Loader.INSTANCE.read(json, context));

    private final DynamicFluidContainerModel forge;

    private ForgeFluidContainerGeometry(DynamicFluidContainerModel forge) {
        this.forge = forge;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
            ModelState state, ItemOverrides overrides) {
        return new Baked(forge.bake(context, baker, sprites, state, overrides), forge, context, baker, state);
    }

    @Override
    public void resolveParents(Function<Identifier, UnbakedModel> models, IGeometryBakingContext context) {
        forge.resolveParents(models, context);
    }

    private static final class Baked extends BakedModelWrapper<BakedModel> {
        private final Map<Fluid, BakedModel> perFluid = new ConcurrentHashMap<>();
        private final ItemOverrides overrides;

        Baked(BakedModel base, DynamicFluidContainerModel forge, IGeometryBakingContext context, ModelBaker baker,
                ModelState state) {
            super(base);
            this.overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity,
                        int seed) {
                    // Forge's own handler first: the JSON's nested overrides, and its fluid lookup should it ever work.
                    BakedModel nested = base.getOverrides().resolve(base, stack, level, entity, seed);
                    if (nested != base) {
                        return nested;
                    }
                    BCFluidStack contained = Platform.transfer().getFluidContained(stack);
                    if (contained.isEmpty()) {
                        return base;
                    }
                    return perFluid.computeIfAbsent(contained.getFluid(),
                            fluid -> forge.withFluid(fluid).bake(context, baker, Material::sprite, state, ItemOverrides.EMPTY));
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
*///?}
