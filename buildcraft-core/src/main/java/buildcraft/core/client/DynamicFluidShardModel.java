/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.client;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.color.item.FluidContentsTint;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.ComposedModelState;
import net.neoforged.neoforge.client.model.UnbakedElementsHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import buildcraft.core.BCCore;

/**
 * Dynamic item model for fragile fluid shards. Uses the mask-based approach
 * from DynamicFluidContainerModel (bucket model) but with per-fluid render
 * type selection: opaque fluids use CUTOUT_MIPPED, translucent fluids use
 * TRANSLUCENT. This gives animated textures for all fluids while avoiding
 * z-fighting for translucent fluids and forced translucency for opaque ones.
 */

public class DynamicFluidShardModel implements ItemModel {

    // Depth offsets to prevent Z-fighting between layers
    private static final Transformation FLUID_TRANSFORM =
            new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1.002f), new Quaternionf());
    private static final Transformation COVER_TRANSFORM =
            new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1.004f), new Quaternionf());

    private static final ModelDebugName DEBUG_NAME = () -> "DynamicFluidShardModel";

    // Render type groups — entity render types must match the blending mode:
    // CUTOUT uses binary alpha (opaque fluids like lava, oil)
    // TRANSLUCENT uses alpha blending (translucent fluids like water)
    private static final RenderTypeGroup RENDER_TYPES_CUTOUT =
            new RenderTypeGroup(ChunkSectionLayer.CUTOUT, NeoForgeRenderTypes::getItemLayeredCutout);
    private static final RenderTypeGroup RENDER_TYPES_TRANSLUCENT =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getItemLayeredTranslucent);
    private static final RenderTypeGroup RENDER_TYPES_UNLIT =
            new RenderTypeGroup(ChunkSectionLayer.TRANSLUCENT, NeoForgeRenderTypes::getUnlitTranslucent);

    // Texture locations for the shard
    private static final Identifier BASE_TEXTURE =
            Identifier.parse("buildcraftcore:item/fragile_fluid_shard_base");
    private static final Identifier FLUID_MASK_TEXTURE =
            Identifier.parse("buildcraftcore:item/fragile_fluid_shard_fluid");

    private final BakingContext bakingContext;
    private final ItemTransforms itemTransforms;
    private final Map<Fluid, ItemModel> cache = new IdentityHashMap<>();

    private DynamicFluidShardModel(BakingContext bakingContext) {
        this.bakingContext = bakingContext;
        var baseItemModel = bakingContext.blockModelBaker().getModel(Identifier.withDefaultNamespace("item/generated"));
        if (baseItemModel == null) {
            throw new IllegalStateException("Failed to access item/generated model");
        }
        this.itemTransforms = baseItemModel.getTopTransforms();
    }

    @SubscribeEvent
    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(
                Identifier.parse("buildcraftcore:fluid_shard"),
                Unbaked.MAP_CODEC
        );
    }

    private ItemModel bakeModelForFluid(Fluid fluid) {
        var sprites = bakingContext.blockModelBaker().sprites();

        Material baseMaterial = ClientHooks.getItemMaterial(BASE_TEXTURE);
        Material fluidMaskMaterial = ClientHooks.getItemMaterial(FLUID_MASK_TEXTURE);

        TextureAtlasSprite baseSprite = sprites.get(baseMaterial, DEBUG_NAME);

        // Get the fluid's STILL texture for the look inside the shard
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        Identifier stillTexId = ext.getStillTexture();
        TextureAtlasSprite fluidSprite = (fluid != Fluids.EMPTY && stillTexId != null)
                ? sprites.get(ClientHooks.getBlockMaterial(stillTexId), DEBUG_NAME)
                : null;

        // Use the fluid mask as clipping shape
        TextureAtlasSprite maskSprite = sprites.get(fluidMaskMaterial, DEBUG_NAME);

        TextureAtlasSprite particleSprite = fluidSprite != null ? fluidSprite : baseSprite;
        ModelState state = BlockModelRotation.IDENTITY;

        List<ItemModel> subModels = new ArrayList<>();
        ModelRenderProperties renderProperties = new ModelRenderProperties(false, particleSprite, itemTransforms);

        // Layer 0: Base shard crystal texture (no tint, always cutout)
        {
            var unbaked = UnbakedElementsHelper.createUnbakedItemElements(0, baseSprite);
            var quads = UnbakedElementsHelper.bakeElements(unbaked, $ -> baseSprite, state);
            var renderType = RenderTypeHelper.detectItemModelRenderType(quads, RENDER_TYPES_CUTOUT);
            subModels.add(new BlockModelWrapper(List.of(), quads, renderProperties, renderType));
        }

        // Layer 1: Fluid texture clipped by the shard mask, with fluid tint
        if (fluidSprite != null) {
            var transformedState = new ComposedModelState(state, FLUID_TRANSFORM);
            // Use the mask sprite shape to clip, but bake with the actual fluid texture
            var unbaked = UnbakedElementsHelper.createUnbakedItemMaskElements(0, maskSprite);
            var quads = UnbakedElementsHelper.bakeElements(unbaked, $ -> fluidSprite, transformedState);

            // Only vanilla water is truly translucent. BuildCraft fluids (oil, etc.)
            // reuse water's flowing texture but are opaque — sprite pixel scanning
            // would incorrectly flag them as translucent.
            boolean isTranslucent = fluid.isSame(Fluids.WATER);

            boolean emissive = fluid.getFluidType().getLightLevel() > 0;
            RenderTypeGroup renderTypeGroup;
            if (emissive) {
                renderTypeGroup = RENDER_TYPES_UNLIT;
            } else if (isTranslucent) {
                renderTypeGroup = RENDER_TYPES_TRANSLUCENT;
            } else {
                renderTypeGroup = RENDER_TYPES_CUTOUT;
            }

            var renderType = RenderTypeHelper.detectItemModelRenderType(quads, renderTypeGroup);
            if (emissive) {
                quads = new ArrayList<>(quads);
                quads.replaceAll(DynamicFluidShardModel::setMaxEmissivity);
            }

            // FluidContentsTint handles tinting for fluids that use it (water = blue)
            // For pre-colored fluids (lava), it returns white which is a no-op
            subModels.add(new BlockModelWrapper(List.of(FluidContentsTint.INSTANCE), quads, renderProperties, renderType));

            // Layer 2: Cover layer — shard base drawn on top of fluid to hide z-fighting
            // Only for opaque fluids; for translucent ones the cover would hide the fluid
            if (!isTranslucent) {
                var coverState = new ComposedModelState(state, COVER_TRANSFORM);
                var coverUnbaked = UnbakedElementsHelper.createUnbakedItemMaskElements(0, maskSprite);
                var coverQuads = UnbakedElementsHelper.bakeElements(coverUnbaked, $ -> baseSprite, coverState);
                var coverRenderType = RenderTypeHelper.detectItemModelRenderType(coverQuads, RENDER_TYPES_CUTOUT);
                subModels.add(new BlockModelWrapper(List.of(), coverQuads, renderProperties, coverRenderType));
            }
        }

        return new CompositeModel(subModels);
    }

    private static BakedQuad setMaxEmissivity(BakedQuad quad) {
        return new BakedQuad(
                quad.position0(),
                quad.position1(),
                quad.position2(),
                quad.position3(),
                quad.packedUV0(),
                quad.packedUV1(),
                quad.packedUV2(),
                quad.packedUV3(),
                quad.tintIndex(),
                quad.direction(),
                quad.sprite(),
                quad.shade(),
                Level.MAX_BRIGHTNESS,
                quad.bakedNormals(),
                quad.bakedColors(),
                quad.hasAmbientOcclusion());
    }

    /** Checks if a sprite's first frame contains any semi-transparent pixels (0 < alpha < 255). */
    private static boolean hasTranslucentPixels(TextureAtlasSprite sprite) {
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // getPixelRGBA returns ABGR-ordered int (NativeImage pixel format)
                int pixel = sprite.getPixelRGBA(0, x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a > 0 && a < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver modelResolver,
            ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        SimpleFluidContent content = stack.getOrDefault(BCCore.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY);
        FluidStack fluidStack = content.copy();
        Fluid fluid = fluidStack.isEmpty() ? Fluids.EMPTY : fluidStack.getFluid();

        cache.computeIfAbsent(fluid, this::bakeModelForFluid)
                .update(renderState, stack, modelResolver, displayContext, level, owner, seed);
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
        public ItemModel bake(BakingContext bakingContext) {
            return new DynamicFluidShardModel(bakingContext);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            // No dependencies
        }
    }
}
