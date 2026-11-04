/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
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

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
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

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

import buildcraft.api.fluids.BCFluidStack;
import buildcraft.platform.Platform;
import buildcraft.platform.client.ClientPlatform;

/^*
 * The Fabric twin of NeoForge's {@code neoforge:fluid_container} item model, registered as
 * {@code buildcraftunofficial:fluid_container} with the same JSON fields (the Fabric data transform, {@code DataFabric},
 * renames the type in BuildCraft's bucket and fragile-fluid-shard item models).
 *
 * <p>Layers, back to front: the container's {@code base} texture as an ordinary extruded item layer; the contained
 * fluid's still sprite, cut to the opaque pixels of the {@code fluid} mask and tinted with the fluid's colour
 * ({@link ClientPlatform#fluids()}); and, if given, a {@code cover} (its own texture, or the base texture cut to the
 * cover's shape when {@code cover_is_mask}). Each layer sits a hair in front of the one before so they never z-fight;
 * a lighter-than-air fluid turns the model upside down when {@code flip_gas} is set. The fluid shown is the stack's
 * first contained fluid (its Fabric item fluid storage: the shard's content component, a bucket's own fluid), else
 * the model's {@code fluid}; one model is baked per fluid, on first sight, and kept until the next resource reload.
 *
 * <p>Gaps against NeoForge: the mask and cover layers have front and back faces only (no side faces); a glowing fluid
 * is drawn with full light emission in the normal cutout item sheet, as Fabric has no unlit item render type.
 *^/
public final class FabricFluidContainerModel implements ItemModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("buildcraftunofficial", "fluid_container");

    private static final ModelDebugName DEBUG_NAME = () -> "BuildCraft fluid container model";
    private static final Identifier GENERATED = Identifier.withDefaultNamespace("item/generated");
    // How far each layer is pushed out in front of the base along the item's thickness, as a z scale.
    private static final Transformation FLUID_DEPTH = depth(1.002F);
    private static final Transformation COVER_DEPTH = depth(1.004F);
    private static final Transformation UPSIDE_DOWN =
            new Transformation(null, new Quaternionf().rotateZ((float) Math.PI), null, null);

    private final Unbaked spec;
    private final ModelBaker baker;
    private final Matrix4fc transformation;
    private final ItemTransforms transforms;
    private final Map<Fluid, ItemModel> perFluid = new IdentityHashMap<>();

    private FabricFluidContainerModel(Unbaked spec, ModelBaker baker, Matrix4fc transformation) {
        this.spec = spec;
        this.baker = baker;
        this.transformation = transformation;
        this.transforms = baker.getModel(GENERATED).getTopTransforms();
    }

    /^* Makes the model type known to item model JSON; called once from the Fabric client initializer. *^/
    public static void register() {
        ItemModels.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
        BCFluidStack contained = Platform.transfer().getFluidContained(stack);
        Fluid fluid = contained.isEmpty() ? spec.fluid() : contained.getFluid();
        perFluid.computeIfAbsent(fluid, this::bakeFor)
                .update(output, stack, resolver, displayContext, level, owner, seed);
    }

    private ItemModel bakeFor(Fluid fluid) {
        MaterialBaker materials = baker.materials();
        Material.Baked base = spec.textures().base().map(m -> materials.get(m, DEBUG_NAME)).orElse(null);
        Material.Baked mask = spec.textures().fluid().map(m -> materials.get(m, DEBUG_NAME)).orElse(null);
        Material.Baked cover = spec.textures().cover()
                .filter(m -> !spec.coverIsMask() || base != null)
                .map(m -> materials.get(m, DEBUG_NAME)).orElse(null);
        Material.Baked fluidSprite = fluid == Fluids.EMPTY ? null : Minecraft.getInstance().getModelManager()
                .getFluidStateModelSet().get(fluid.defaultFluidState()).stillMaterial();

        Material.Baked particle = spec.textures().particle().map(m -> materials.get(m, DEBUG_NAME)).orElse(null);
        if (particle == null) particle = fluidSprite;
        if (particle == null) particle = base;
        if (particle == null && !spec.coverIsMask()) particle = cover;

        ModelState state = BlockModelRotation.IDENTITY;
        if (spec.flipGas() && fluid != Fluids.EMPTY && Platform.fluids().isLighterThanAir(fluid)) {
            state = new Composed(state, UPSIDE_DOWN);
        }
        ModelRenderProperties properties = new ModelRenderProperties(false, particle, transforms);
        List<ItemModel> layers = new ArrayList<>(3);

        if (base != null) {
            QuadCollection quads = baker.compute(new ItemModelGenerator.ItemLayerKey(base, state, 0));
            layers.add(new CuboidItemModelWrapper(List.of(), quads, properties, transformation));
        }
        if (mask != null && fluidSprite != null) {
            int luminance = FluidVariantAttributes.getLuminance(FluidVariant.of(fluid));
            boolean glows = spec.applyFluidLuminosity() && luminance > 0;
            RenderType[] sheets = fluidSheets(fluidSprite, spec.forceOpaqueFluid());
            BakedQuad.MaterialInfo info = baker.interner().materialInfo(new BakedQuad.MaterialInfo(
                    fluidSprite.sprite(), ChunkSectionLayer.SOLID, sheets[0], sheets[1], sheets[2], 0,
                    glows ? Direction.UP : null, glows ? Level.MAX_BRIGHTNESS : 0));
            QuadCollection quads = maskedQuads(mask, info, new Composed(state, FLUID_DEPTH));
            layers.add(new CuboidItemModelWrapper(List.of(new FluidTint(fluid)), quads, properties, transformation));
        }
        if (cover != null) {
            Material.Baked drawn = spec.coverIsMask() ? base : cover;
            BakedQuad.MaterialInfo info = baker.interner().materialInfo(
                    BakedQuad.MaterialInfo.of(drawn, drawn.sprite().transparency(), 0, null, 0));
            QuadCollection quads = maskedQuads(cover, info, new Composed(state, COVER_DEPTH));
            layers.add(new CuboidItemModelWrapper(List.of(), quads, properties, transformation));
        }
        return new CompositeModel(layers);
    }

    /^* The item, glint and special-glint sheets for the fluid layer: cutout unless the fluid is let be translucent. *^/
    private static RenderType[] fluidSheets(Material.Baked sprite, boolean forceOpaque) {
        boolean translucent = !forceOpaque
                && (sprite.forceTranslucent() || sprite.sprite().transparency().hasTranslucent());
        boolean blockAtlas = sprite.sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS);
        if (blockAtlas) {
            return translucent
                    ? new RenderType[] {Sheets.translucentBlockItemSheet(), Sheets.translucentBlockItemGlintSheet(),
                        Sheets.translucentBlockItemGlintSpecialSheet()}
                    : new RenderType[] {Sheets.cutoutBlockItemSheet(), Sheets.cutoutBlockItemGlintSheet(),
                        Sheets.cutoutBlockItemGlintSpecialSheet()};
        }
        return translucent
                ? new RenderType[] {Sheets.translucentItemSheet(), Sheets.translucentItemGlintSheet(),
                    Sheets.translucentItemGlintSpecialSheet()}
                : new RenderType[] {Sheets.cutoutItemSheet(), Sheets.cutoutItemGlintSheet(),
                    Sheets.cutoutItemGlintSpecialSheet()};
    }

    /^*
     * Flat front and back quads covering exactly the opaque pixels of {@code mask} (in any animation frame), each
     * drawn with {@code info}'s sprite. The opaque area is split greedily into rectangles: a run of opaque pixels along
     * a row, grown downwards while every pixel under it is opaque too.
     *^/
    private QuadCollection maskedQuads(Material.Baked mask, BakedQuad.MaterialInfo info, ModelState state) {
        SpriteContents contents = mask.sprite().contents();
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
        ModelBaker.Interner interner = baker.interner();
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
                CuboidFace.UVs frontUvs = new CuboidFace.UVs(16F - to.x(), 16F - to.y(), 16F - from.x(), 16F - from.y());
                CuboidFace.UVs backUvs = new CuboidFace.UVs(from.x(), 16F - to.y(), to.x(), 16F - from.y());
                builder.addUnculledFace(FaceBakery.bakeQuad(interner, from, to, frontUvs, Quadrant.R0, info,
                        Direction.SOUTH, state, null));
                builder.addUnculledFace(FaceBakery.bakeQuad(interner, from, to, backUvs, Quadrant.R0, info,
                        Direction.NORTH, state, null));
            }
        }
        return builder.build();
    }

    private static boolean rowOpaque(BitSet opaque, int width, int row, int left, int right) {
        int start = row * width + left;
        int end = row * width + right;
        return opaque.nextClearBit(start) >= end;
    }

    private static Transformation depth(float zScale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, zScale), new Quaternionf());
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
    public record Textures(Optional<Material> particle, Optional<Material> base, Optional<Material> fluid,
            Optional<Material> cover) {
        public static final Codec<Textures> CODEC = RecordCodecBuilder.<Textures>create(i -> i.group(
                        Material.CODEC.optionalFieldOf("particle").forGetter(Textures::particle),
                        Material.CODEC.optionalFieldOf("base").forGetter(Textures::base),
                        Material.CODEC.optionalFieldOf("fluid").forGetter(Textures::fluid),
                        Material.CODEC.optionalFieldOf("cover").forGetter(Textures::cover))
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
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return new FabricFluidContainerModel(this, context.blockModelBaker(), transformation);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(GENERATED); // the item transforms
        }
    }
}
*///?}
