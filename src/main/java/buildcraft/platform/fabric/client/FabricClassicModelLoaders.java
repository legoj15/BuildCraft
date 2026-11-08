/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <1.21.10 {
/*import java.io.Reader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.platform.Platform;
import buildcraft.platform.client.ClassicItemModel;

/^*
 * The two NeoForge classic model loaders BuildCraft's 1.21.1 item models name, for Fabric 1.21.1, which has no model
 * loader of its own: {@code buildcraftunofficial:fluid_container} (NeoForge's {@code neoforge:fluid_container}, the
 * buckets and the fragile fluid shard) and {@code buildcraftunofficial:separate_transforms} (NeoForge's
 * {@code neoforge:separate_transforms}, the goggles). The build's Fabric data transform renames the {@code loader} in
 * {@code models/item}; vanilla would read such a file as a plain model with no geometry, so every item model naming one
 * of the two is read here first and handed to Fabric's model resolver.
 *
 * <p>Gaps from NeoForge's fluid container: the fluid layer is flat front and back quads over the mask (NeoForge's also
 * has the mask's side walls, hidden inside the base's), it ignores {@code apply_fluid_luminosity} (vanilla's 1.21.1 quads
 * carry no light emission; no BuildCraft fluid emits light) and {@code force_opaque_fluid}, and the fluid sprite is the
 * one Fabric's variant rendering declares. Tints are NeoForge's: index 0 base, 1 fluid, 2 cover, coloured only by an
 * item colour registered for the item.
 *^/
public final class FabricClassicModelLoaders {
    public static final Identifier FLUID_CONTAINER = Identifier.fromNamespaceAndPath("buildcraftunofficial", "fluid_container");
    public static final Identifier SEPARATE_TRANSFORMS =
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "separate_transforms");

    // How far each layer is pushed out in front of the base along the item's thickness, as a z scale.
    private static final Transformation FLUID_DEPTH = depth(1.002F);
    private static final Transformation COVER_DEPTH = depth(1.004F);
    private static final Transformation UPSIDE_DOWN =
            new Transformation(null, new Quaternionf().rotateZ((float) Math.PI), null, null);

    private FabricClassicModelLoaders() {}

    /^* Registers the resolver; called once from the Fabric client initializer. *^/
    public static void register() {
        PreparableModelLoadingPlugin.register(
                (resourceManager, executor) -> CompletableFuture.supplyAsync(() -> {
                    Map<Identifier, JsonObject> found = new HashMap<>();
                    Map<Identifier, Resource> files =
                            resourceManager.listResources("models/item", id -> id.getPath().endsWith(".json"));
                    files.forEach((file, resource) -> {
                        try (Reader reader = resource.openAsReader()) {
                            JsonElement json = JsonParser.parseReader(reader);
                            if (json.isJsonObject() && json.getAsJsonObject().has("loader")) {
                                found.put(ModelBakery.MODEL_LISTER.fileToId(file), json.getAsJsonObject());
                            }
                        } catch (Exception e) {
                            // Left to vanilla, which reports the broken file when it loads it.
                        }
                    });
                    return found;
                }, executor),
                (found, context) -> context.resolveModel().register(resolver -> {
                    JsonObject json = found.get(resolver.id());
                    if (json == null) {
                        return null;
                    }
                    Identifier loader = Identifier.parse(json.get("loader").getAsString());
                    if (loader.equals(FLUID_CONTAINER)) {
                        return new FluidContainerUnbaked(json);
                    }
                    if (loader.equals(SEPARATE_TRANSFORMS)) {
                        return new SeparateTransformsUnbaked(resolver.id(), json);
                    }
                    return null;
                }));
    }

    private static Transformation depth(float zScale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, zScale), new Quaternionf());
    }

    private static Material material(Identifier texture) {
        return new Material(BCLibRenderTypes.BLOCKS_ATLAS_ID, texture);
    }

    // A model state with one more transformation composed after its parent's.
    private record Composed(Transformation rotation, boolean uvLocked) implements ModelState {
        static ModelState of(ModelState parent, Transformation composed) {
            return new Composed(parent.getRotation().compose(composed), parent.isUvLocked());
        }

        @Override
        public Transformation getRotation() {
            return rotation;
        }

        @Override
        public boolean isUvLocked() {
            return uvLocked;
        }
    }

    // Bakes a JSON model the way vanilla's baker bakes a top-level one: a generated item layer model when its root is
    // item/generated, its own elements otherwise.
    private static BakedModel bakeBlockModel(BlockModel model, ModelBaker baker,
            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        if (model.getRootModel() == ModelBakery.GENERATION_MARKER) {
            return new ItemModelGenerator().generateBlockModel(sprites, model).bake(baker, model, sprites, state, false);
        }
        return model.bake(baker, model, sprites, state, true);
    }

    // Every quad of a baked model, culled faces included (an item draws them all).
    private static List<BakedQuad> allQuads(BakedModel model) {
        List<BakedQuad> quads = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (Direction side : Direction.values()) {
            quads.addAll(model.getQuads(null, side, random));
        }
        quads.addAll(model.getQuads(null, null, random));
        return quads;
    }

    // ─── fluid_container ─────────────────────────────────────────────────────

    private static final class FluidContainerUnbaked implements UnbakedModel {
        @Nullable private final Identifier particle;
        @Nullable private final Identifier base;
        @Nullable private final Identifier fluidMask;
        @Nullable private final Identifier cover;
        private final Fluid fluid;
        private final boolean flipGas;
        private final boolean coverIsMask;
        private final ItemTransforms transforms;

        FluidContainerUnbaked(JsonObject json) {
            JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
            this.particle = texture(textures, "particle");
            this.base = texture(textures, "base");
            this.fluidMask = texture(textures, "fluid");
            this.cover = texture(textures, "cover");
            this.fluid = BuiltInRegistries.FLUID.get(Identifier.parse(json.get("fluid").getAsString()));
            this.flipGas = json.has("flip_gas") && json.get("flip_gas").getAsBoolean();
            this.coverIsMask = !json.has("cover_is_mask") || json.get("cover_is_mask").getAsBoolean();
            // The JSON's own display block, as NeoForge's classic loader reads it (the build stamps item/generated's).
            this.transforms = BlockModel.fromString(json.toString()).getTransforms();
        }

        @Nullable
        private static Identifier texture(JsonObject textures, String role) {
            return textures.has(role) ? Identifier.parse(textures.get(role).getAsString()) : null;
        }

        @Override
        public Collection<Identifier> getDependencies() {
            return Set.of();
        }

        @Override
        public void resolveParents(Function<Identifier, UnbakedModel> resolver) {}

        @Override
        public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            return new FluidContainerModel(this, baker, sprites, state);
        }
    }

    // The baked fluid container: one model per contained fluid, baked on first sight (the fluid's sprite is only known
    // once the atlas is in use) and kept until the next resource reload.
    private static final class FluidContainerModel implements BakedModel {
        private final FluidContainerUnbaked spec;
        private final ModelBaker baker;
        private final Function<Material, TextureAtlasSprite> sprites;
        private final ModelState state;
        private final Map<Fluid, BakedModel> perFluid = new IdentityHashMap<>();
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                    @Nullable LivingEntity entity, int seed) {
                BCFluidStack contained = Platform.transfer().getFluidContained(stack);
                return forFluid(contained.isEmpty() ? spec.fluid : contained.getFluid());
            }
        };

        FluidContainerModel(FluidContainerUnbaked spec, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                ModelState state) {
            this.spec = spec;
            this.baker = baker;
            this.sprites = sprites;
            this.state = state;
        }

        BakedModel forFluid(Fluid fluid) {
            return perFluid.computeIfAbsent(fluid, this::bakeFor);
        }

        @Nullable
        private TextureAtlasSprite sprite(@Nullable Identifier texture) {
            return texture == null ? null : sprites.apply(material(texture));
        }

        private BakedModel bakeFor(Fluid fluid) {
            TextureAtlasSprite baseSprite = sprite(spec.base);
            TextureAtlasSprite fluidSprite = fluid == Fluids.EMPTY ? null
                    : FluidVariantRendering.getSprite(FluidVariant.of(fluid));
            TextureAtlasSprite coverSprite = spec.cover != null && (!spec.coverIsMask || spec.base != null)
                    ? sprite(spec.cover) : null;

            TextureAtlasSprite particleSprite = sprite(spec.particle);
            if (particleSprite == null) particleSprite = fluidSprite;
            if (particleSprite == null) particleSprite = baseSprite;
            if (particleSprite == null && !spec.coverIsMask) particleSprite = coverSprite;

            ModelState layerState = state;
            if (spec.flipGas && fluid != Fluids.EMPTY && Platform.fluids().isLighterThanAir(fluid)) {
                layerState = Composed.of(layerState, UPSIDE_DOWN);
            }

            List<BakedQuad> quads = new ArrayList<>();
            if (baseSprite != null) {
                BlockModel layer = BlockModel.fromString("{\"textures\":{\"layer0\":\"" + spec.base + "\"}}");
                quads.addAll(allQuads(new ItemModelGenerator().generateBlockModel(sprites, layer)
                        .bake(baker, layer, sprites, layerState, false)));
            }
            if (spec.fluidMask != null && fluidSprite != null) {
                quads.addAll(maskedQuads(sprite(spec.fluidMask), fluidSprite, 1, Composed.of(layerState, FLUID_DEPTH)));
            }
            if (coverSprite != null) {
                TextureAtlasSprite drawn = spec.coverIsMask ? baseSprite : coverSprite;
                if (drawn != null) {
                    quads.addAll(maskedQuads(coverSprite, drawn, 2, Composed.of(layerState, COVER_DEPTH)));
                }
            }
            return new FluidLayersModel(List.copyOf(quads), particleSprite, spec.transforms);
        }

        // NeoForge's GUI-flat bake: no GUI 3D, no block light. The top-level model draws the item's own fluid.
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState blockState, @Nullable Direction side, RandomSource random) {
            return forFluid(spec.fluid).getQuads(blockState, side, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return forFluid(spec.fluid).getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return spec.transforms;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    /^*
     * Flat front and back quads covering exactly the opaque pixels of {@code mask} (in any animation frame), each drawn
     * with {@code drawn}: the opaque area split greedily into rectangles, a run of opaque pixels along a row grown
     * downwards while every pixel under it is opaque too.
     *^/
    private static List<BakedQuad> maskedQuads(TextureAtlasSprite mask, TextureAtlasSprite drawn, int tintIndex,
            ModelState state) {
        SpriteContents contents = mask.contents();
        int width = contents.width();
        int height = contents.height();
        BitSet opaque = new BitSet(width * height);
        contents.getUniqueFrames().forEach((int frame) -> {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!contents.isTransparent(frame, x, y)) {
                        opaque.set(y * width + x);
                    }
                }
            }
        });

        FaceBakery bakery = new FaceBakery();
        List<BakedQuad> quads = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!opaque.get(y * width + x)) {
                    x++;
                    continue;
                }
                int left = x;
                while (x < width && opaque.get(y * width + x)) {
                    x++;
                }
                int right = x; // exclusive
                int bottom = y + 1; // exclusive
                while (bottom < height && opaque.nextClearBit(bottom * width + left) >= bottom * width + right) {
                    bottom++;
                }
                for (int row = y; row < bottom; row++) {
                    opaque.clear(row * width + left, row * width + right);
                }
                Vector3f from = new Vector3f(16F * left / width, 16F - 16F * bottom / height, 7.5F);
                Vector3f to = new Vector3f(16F * right / width, 16F - 16F * y / height, 8.5F);
                // The UVs of the front face are the ones that read the texture the right way round from behind and
                // vice versa (as vanilla's generated item layers pair them).
                BlockElementFace front = new BlockElementFace(null, tintIndex, "",
                        new BlockFaceUV(new float[] {16F - to.x(), 16F - to.y(), 16F - from.x(), 16F - from.y()}, 0));
                BlockElementFace back = new BlockElementFace(null, tintIndex, "",
                        new BlockFaceUV(new float[] {from.x(), 16F - to.y(), to.x(), 16F - from.y()}, 0));
                quads.add(bakery.bakeQuad(from, to, front, drawn, Direction.SOUTH, state, null, true));
                quads.add(bakery.bakeQuad(from, to, back, drawn, Direction.NORTH, state, null, true));
            }
        }
        return quads;
    }

    // One contained fluid's layers, drawn unculled.
    private record FluidLayersModel(List<BakedQuad> quads, TextureAtlasSprite particle, ItemTransforms transforms)
            implements BakedModel {
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return side == null ? quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    // ─── separate_transforms ─────────────────────────────────────────────────

    private static final class SeparateTransformsUnbaked implements UnbakedModel {
        private final BlockModel base;
        private final Map<ItemDisplayContext, BlockModel> perspectives = new EnumMap<>(ItemDisplayContext.class);
        private final BlockModel.GuiLight guiLight;
        private final boolean ambientOcclusion;

        SeparateTransformsUnbaked(Identifier id, JsonObject json) {
            this.base = BlockModel.fromString(json.getAsJsonObject("base").toString());
            this.base.name = id + "#base";
            if (json.has("perspectives")) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("perspectives").entrySet()) {
                    for (ItemDisplayContext context : ItemDisplayContext.values()) {
                        if (context.getSerializedName().equals(entry.getKey())) {
                            BlockModel perspective = BlockModel.fromString(entry.getValue().toString());
                            perspective.name = id + "#" + entry.getKey();
                            perspectives.put(context, perspective);
                        }
                    }
                }
            }
            this.guiLight = json.has("gui_light") ? BlockModel.GuiLight.getByName(json.get("gui_light").getAsString())
                    : BlockModel.GuiLight.SIDE;
            this.ambientOcclusion = !json.has("ambientocclusion") || json.get("ambientocclusion").getAsBoolean();
        }

        @Override
        public Collection<Identifier> getDependencies() {
            Set<Identifier> dependencies = new HashSet<>(base.getDependencies());
            perspectives.values().forEach(model -> dependencies.addAll(model.getDependencies()));
            return dependencies;
        }

        @Override
        public void resolveParents(Function<Identifier, UnbakedModel> resolver) {
            base.resolveParents(resolver);
            perspectives.values().forEach(model -> model.resolveParents(resolver));
        }

        @Override
        public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            BakedModel bakedBase = bakeBlockModel(base, baker, sprites, state);
            Map<ItemDisplayContext, BakedModel> bakedPerspectives = new EnumMap<>(ItemDisplayContext.class);
            perspectives.forEach((context, model) -> bakedPerspectives.put(context,
                    bakeBlockModel(model, baker, sprites, state)));
            return new SeparateTransformsModel(bakedBase, bakedPerspectives, ambientOcclusion, guiLight.lightLikeBlock());
        }
    }

    // NeoForge's baked separate-transforms model: the base's quads, and the chosen view's model to draw (whose own
    // item transforms vanilla then applies, where NeoForge's has that model apply them).
    private record SeparateTransformsModel(BakedModel base, Map<ItemDisplayContext, BakedModel> perspectives,
            boolean ambientOcclusion, boolean blockLight) implements ClassicItemModel {
        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            return perspectives.getOrDefault(context, base);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return base.getQuads(state, side, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return blockLight;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return base.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
*///?}
