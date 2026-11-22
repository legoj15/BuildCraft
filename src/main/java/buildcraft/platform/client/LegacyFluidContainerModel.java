/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

//? if >=1.21.10 && <26.1 {
/*import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.Platform;

/^*
 * The fluid-container item model for the lines before 26.1 (1.21.10, 1.21.11), one bake shared by every loader:
 * {@code FabricLegacyFluidContainerModel} and {@code ForgeLegacyFluidContainerModel} register its type in their own
 * item model maps, and on NeoForge nothing does (its item JSON reads NeoForge's own {@code neoforge:fluid_container}
 * type, which this mirrors), so the class is unused there. These lines' item model API bakes with a plain
 * {@code Material} and sprite getter, has no item-layer quad keys, and draws each item layer on one render type
 * chosen by the layer. The same JSON type id ({@code buildcraftunofficial:fluid_container}) and fields as the 26.x
 * models, the same layers: the {@code base} texture as a generated item layer, the contained fluid's still sprite
 * cut to the opaque pixels of the {@code fluid} mask and tinted with the fluid's colour, and the optional
 * {@code cover}; each layer a hair in front of the one before, upside down for a lighter-than-air fluid when
 * {@code flip_gas} is set. One model per fluid, baked on first sight and kept until the next resource reload. The
 * fluid's look is the loader's through {@link ClientPlatform#fluids()}: the still texture, the tint and the light
 * level.
 *
 * <p>Gaps on top of the 26.x model's: the fluid layer is drawn only when the loader declares the fluid a still
 * texture (no water fallback), and with {@code force_opaque_fluid} off it is drawn translucent whatever its pixels,
 * as these lines expose no per-sprite translucency.
 *^/
public final class LegacyFluidContainerModel implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("buildcraftunofficial", "fluid_container");

    private static final ModelDebugName DEBUG_NAME = () -> "BuildCraft fluid container model";
    private static final Identifier GENERATED = Identifier.withDefaultNamespace("item/generated");
    // The blocks atlas's texture id, where a fluid's still sprite lives on these lines. Value-identical to vanilla's
    // TextureAtlas.LOCATION_BLOCKS (deprecated with no successor) and to lib's BCLibRenderTypes.BLOCKS_ATLAS_ID,
    // which the platform contract keeps out of this package.
    private static final Identifier BLOCKS_ATLAS_ID = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    // How far each layer is pushed out in front of the base along the item's thickness, as a z scale.
    private static final Transformation FLUID_DEPTH = depth(1.002F);
    private static final Transformation COVER_DEPTH = depth(1.004F);
    private static final Transformation UPSIDE_DOWN =
            new Transformation(null, new Quaternionf().rotateZ((float) Math.PI), null, null);

    private final Unbaked spec;
    private final ModelBaker baker;
    private final ItemTransforms transforms;
    private final Map<Fluid, ItemModel> perFluid = new IdentityHashMap<>();

    private LegacyFluidContainerModel(Unbaked spec, ModelBaker baker) {
        this.spec = spec;
        this.baker = baker;
        this.transforms = baker.getModel(GENERATED).getTopTransforms();
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
        BCFluidStack contained = Platform.transfer().getFluidContained(stack);
        Fluid fluid = contained.isEmpty() ? spec.fluid() : contained.getFluid();
        perFluid.computeIfAbsent(fluid, this::bakeFor)
                .update(output, stack, resolver, displayContext, level, owner, seed);
    }

    private TextureAtlasSprite sprite(Optional<Identifier> texture) {
        return texture.map(id -> baker.sprites().get(material(id), DEBUG_NAME)).orElse(null);
    }

    private ItemModel bakeFor(Fluid fluid) {
        TextureAtlasSprite base = sprite(spec.textures().base());
        TextureAtlasSprite mask = sprite(spec.textures().fluid());
        TextureAtlasSprite cover = spec.coverIsMask() && base == null ? null : sprite(spec.textures().cover());
        TextureAtlasSprite fluidSprite = fluid == Fluids.EMPTY ? null : fluidSprite(fluid);

        TextureAtlasSprite particle = sprite(spec.textures().particle());
        if (particle == null) particle = fluidSprite;
        if (particle == null) particle = base;
        if (particle == null && !spec.coverIsMask()) particle = cover;

        ModelState state = identity();
        if (spec.flipGas() && fluid != Fluids.EMPTY && Platform.fluids().isLighterThanAir(fluid)) {
            state = new Composed(state, UPSIDE_DOWN);
        }
        ModelRenderProperties properties = new ModelRenderProperties(false, particle, transforms);
        List<ItemModel> layers = new ArrayList<>(3);

        if (base != null) {
            TextureSlots slots = new TextureSlots.Resolver()
                    .addLast(new TextureSlots.Data.Builder().addTexture("layer0", material(spec.textures().base().get())).build())
                    .resolve(DEBUG_NAME);
            List<BakedQuad> quads = new ItemModelGenerator().geometry().bake(slots, baker, state, DEBUG_NAME).getAll();
            layers.add(new Layer(List.of(), quads, properties, false, true));
        }
        if (mask != null && fluidSprite != null) {
            int luminance = ClientPlatform.fluids().luminance(new BCFluidStack(fluid, 1000));
            boolean glows = spec.applyFluidLuminosity() && luminance > 0;
            List<BakedQuad> quads = maskedQuads(mask, fluidSprite, 0, !glows, glows ? Level.MAX_BRIGHTNESS : 0,
                    new Composed(state, FLUID_DEPTH));
            layers.add(new Layer(List.of(new FluidTint(fluid)), quads, properties,
                    fluidSprite.atlasLocation().equals(BLOCKS_ATLAS_ID), !spec.forceOpaqueFluid()));
        }
        if (cover != null) {
            TextureAtlasSprite drawn = spec.coverIsMask() ? base : cover;
            List<BakedQuad> quads = maskedQuads(cover, drawn, -1, true, 0, new Composed(state, COVER_DEPTH));
            layers.add(new Layer(List.of(), quads, properties, false, true));
        }
        return new CompositeModel(layers);
    }

    /^*
     * The fluid's still sprite on the block atlas, or null when the loader declares the fluid no still texture (no
     * fluid layer then). A declared name the atlas does not hold is the baker's answer, as for any other texture.
     *^/
    private TextureAtlasSprite fluidSprite(Fluid fluid) {
        Identifier still = ClientPlatform.fluids().stillTexture(new BCFluidStack(fluid, 1000));
        return still == null ? null
                : baker.sprites().get(new Material(BLOCKS_ATLAS_ID, still), DEBUG_NAME);
    }

    /^*
     * Flat front and back quads covering exactly the opaque pixels of {@code mask} (in any animation frame), each
     * drawn with {@code drawn}. The opaque area is split greedily into rectangles: a run of opaque pixels along a row,
     * grown downwards while every pixel under it is opaque too.
     *^/
    private List<BakedQuad> maskedQuads(TextureAtlasSprite mask, TextureAtlasSprite drawn, int tintIndex,
            boolean shade, int lightEmission, ModelState state) {
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

        QuadCollection.Builder builder = new QuadCollection.Builder();
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
                while (bottom < height && rowOpaque(opaque, width, bottom, left, right)) {
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
                        new BlockElementFace.UVs(16F - to.x(), 16F - to.y(), 16F - from.x(), 16F - from.y()), Quadrant.R0);
                BlockElementFace back = new BlockElementFace(null, tintIndex, "",
                        new BlockElementFace.UVs(from.x(), 16F - to.y(), to.x(), 16F - from.y()), Quadrant.R0);
                builder.addUnculledFace(bakeQuad(from, to, front, drawn, Direction.SOUTH, state, shade, lightEmission));
                builder.addUnculledFace(bakeQuad(from, to, back, drawn, Direction.NORTH, state, shade, lightEmission));
            }
        }
        return builder.build().getAll();
    }

    private BakedQuad bakeQuad(Vector3f from, Vector3f to, BlockElementFace face, TextureAtlasSprite sprite,
            Direction direction, ModelState state, boolean shade, int lightEmission) {
        //? if >=1.21.11 {
        return FaceBakery.bakeQuad(baker.parts(), from, to, face, sprite, direction, state, null, shade, lightEmission);
        //?} else {
        /^return FaceBakery.bakeQuad(from, to, face, sprite, direction, state, null, shade, lightEmission);
        ^///?}
    }

    // An item texture's material: the block-or-item atlas from 1.21.11 (vanilla's item JSON resolves textures the same
    // way), the block atlas before (items and blocks shared it).
    private static Material material(Identifier texture) {
        //? if >=1.21.11 {
        return new Material(net.minecraft.client.resources.model.ModelManager.BLOCK_OR_ITEM, texture);
        //?} else {
        /^return new Material(BLOCKS_ATLAS_ID, texture);
        ^///?}
    }

    private static ModelState identity() {
        //? if >=1.21.11 {
        return BlockModelRotation.IDENTITY;
        //?} else {
        /^return BlockModelRotation.X0_Y0;
        ^///?}
    }

    private static boolean rowOpaque(BitSet opaque, int width, int row, int left, int right) {
        int start = row * width + left;
        int end = row * width + right;
        return opaque.nextClearBit(start) >= end;
    }

    private static Transformation depth(float zScale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, zScale), new Quaternionf());
    }

    /^*
     * One item layer: vanilla's {@code BlockModelWrapper} (whose constructor is not public) with the render type
     * chosen here. The fluid layer on the block atlas draws cutout, or translucent when the model lets it; everything
     * else draws on vanilla's own item sheet.
     *^/
    private static final class Layer implements ItemModel {
        private final List<ItemTintSource> tints;
        private final List<BakedQuad> quads;
        private final ModelRenderProperties properties;
        private final boolean blockAtlasFluid;
        private final boolean translucent;
        private final boolean animated;

        Layer(List<ItemTintSource> tints, List<BakedQuad> quads, ModelRenderProperties properties,
                boolean blockAtlasFluid, boolean translucent) {
            this.tints = tints;
            this.quads = quads;
            this.properties = properties;
            this.blockAtlasFluid = blockAtlasFluid;
            this.translucent = translucent;
            this.animated = quads.stream().anyMatch(quad -> quad.sprite().contents().isAnimated());
        }

        @Override
        public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
                ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
            output.appendModelIdentityElement(this);
            ItemStackRenderState.LayerRenderState layer = output.newLayer();
            if (stack.hasFoil()) {
                layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
                output.setAnimated();
                output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
            }
            int[] tintLayers = layer.prepareTintLayers(tints.size());
            for (int i = 0; i < tints.size(); i++) {
                int tint = tints.get(i).calculate(stack, level, owner == null ? null : owner.asLivingEntity());
                tintLayers[i] = tint;
                output.appendModelIdentityElement(tint);
            }
            layer.setExtents(() -> BlockModelWrapper.computeExtents(quads));
            //? if >=1.21.11 {
            layer.setRenderType(!blockAtlasFluid ? Sheets.translucentItemSheet()
                    : translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockSheet());
            //?} else {
            /^layer.setRenderType(!blockAtlasFluid || translucent ? Sheets.translucentItemSheet() : Sheets.cutoutBlockSheet());
            ^///?}
            properties.applyToLayer(layer, displayContext);
            layer.prepareQuadList().addAll(quads);
            if (animated) {
                output.setAnimated();
            }
        }
    }

    /^* A model state with one more transformation composed after its parent's (face transformations unchanged). *^/
    private record Composed(ModelState parent, Transformation composed) implements ModelState {
        Composed(ModelState parent, Transformation composed) {
            this.parent = parent;
            this.composed = parent.transformation().compose(composed);
        }

        @Override
        public Transformation transformation() {
            return composed;
        }

        @Override
        public Matrix4fc faceTransformation(Direction side) {
            return parent.faceTransformation(side);
        }

        @Override
        public Matrix4fc inverseFaceTransformation(Direction side) {
            return parent.inverseFaceTransformation(side);
        }
    }

    /^* The fluid layer's tint: the loader's colour for the fluid, made opaque (some fluids report zero alpha). *^/
    private record FluidTint(Fluid fluid) implements ItemTintSource {
        @Override
        public int calculate(ItemStack stack, ClientLevel level, LivingEntity entity) {
            return ARGB.opaque(ClientPlatform.fluids().tint(new BCFluidStack(fluid, 1000)));
        }

        @Override
        public MapCodec<? extends ItemTintSource> type() {
            // Built in code per fluid, never read from or written to JSON.
            return MapCodec.unit(this);
        }
    }

    /^* The model's textures, by role; at least one must be given. *^/
    public record Textures(Optional<Identifier> particle, Optional<Identifier> base, Optional<Identifier> fluid,
            Optional<Identifier> cover) {
        public static final Codec<Textures> CODEC = RecordCodecBuilder.<Textures>create(i -> i.group(
                        Identifier.CODEC.optionalFieldOf("particle").forGetter(Textures::particle),
                        Identifier.CODEC.optionalFieldOf("base").forGetter(Textures::base),
                        Identifier.CODEC.optionalFieldOf("fluid").forGetter(Textures::fluid),
                        Identifier.CODEC.optionalFieldOf("cover").forGetter(Textures::cover))
                .apply(i, Textures::new))
                .validate(t -> t.particle.isPresent() || t.base.isPresent() || t.fluid.isPresent()
                        || t.cover.isPresent()
                        ? DataResult.success(t)
                        : DataResult.error(() -> "A fluid container model needs a particle, base, fluid or cover texture"));
    }

    /^* The JSON form: NeoForge's {@code neoforge:fluid_container} fields and defaults. *^/
    public record Unbaked(Textures textures, Fluid fluid, boolean flipGas, boolean coverIsMask,
            boolean applyFluidLuminosity, boolean forceOpaqueFluid) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        Textures.CODEC.fieldOf("textures").forGetter(Unbaked::textures),
                        BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(Unbaked::fluid),
                        Codec.BOOL.optionalFieldOf("flip_gas", false).forGetter(Unbaked::flipGas),
                        Codec.BOOL.optionalFieldOf("cover_is_mask", true).forGetter(Unbaked::coverIsMask),
                        Codec.BOOL.optionalFieldOf("apply_fluid_luminosity", true)
                                .forGetter(Unbaked::applyFluidLuminosity),
                        Codec.BOOL.optionalFieldOf("force_opaque_fluid", true).forGetter(Unbaked::forceOpaqueFluid))
                .apply(i, Unbaked::new));

        @Override
        public MapCodec<? extends ItemModel.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context) {
            return new LegacyFluidContainerModel(this, context.blockModelBaker());
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(GENERATED); // the item transforms
        }
    }
}
*///?}
