/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.model;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

/** Holds all of the information necessary to make a {@link BakedQuad}. This provides a variety of methods to quickly
 * set or get different elements. This currently holds 4 {@link MutableVertex}. */
public class MutableQuad {
    public static final MutableQuad[] EMPTY_ARRAY = new MutableQuad[0];

    public final MutableVertex vertex_0 = new MutableVertex();
    public final MutableVertex vertex_1 = new MutableVertex();
    public final MutableVertex vertex_2 = new MutableVertex();
    public final MutableVertex vertex_3 = new MutableVertex();

    private int tintIndex = -1;
    private Direction face = null;
    private boolean shade = false;
    private TextureAtlasSprite sprite = null;
    private int lightEmission = 0;
    private boolean hasAmbientOcclusion = true;

    public MutableQuad() {}

    public MutableQuad(int tintIndex, Direction face) {
        this(tintIndex, face, false);
    }

    public MutableQuad(int tintIndex, Direction face, boolean shade) {
        this.tintIndex = tintIndex;
        this.face = face;
        this.shade = shade;
    }

    public MutableQuad(MutableQuad from) {
        copyFrom(from);
    }

    public MutableQuad copyFrom(MutableQuad from) {
        tintIndex = from.tintIndex;
        face = from.face;
        shade = from.shade;
        sprite = from.sprite;
        lightEmission = from.lightEmission;
        hasAmbientOcclusion = from.hasAmbientOcclusion;
        vertex_0.copyFrom(from.vertex_0);
        vertex_1.copyFrom(from.vertex_1);
        vertex_2.copyFrom(from.vertex_2);
        vertex_3.copyFrom(from.vertex_3);
        return this;
    }

    public MutableQuad setTint(int tint) {
        tintIndex = tint;
        return this;
    }

    public int getTint() {
        return tintIndex;
    }

    public MutableQuad setFace(Direction face) {
        this.face = face;
        return this;
    }

    public Direction getFace() {
        return face;
    }

    public void setShade(boolean shade) {
        this.shade = shade;
    }

    public boolean isShade() {
        return this.shade;
    }

    public void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public TextureAtlasSprite getSprite() {
        return this.sprite;
    }

    public void setLightEmission(int lightEmission) {
        this.lightEmission = lightEmission;
    }

    public int getLightEmission() {
        return this.lightEmission;
    }

    public void setAmbientOcclusion(boolean hasAmbientOcclusion) {
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    // ############################
    //
    // BakedQuad conversion
    //
    // NeoForge 1.21.11: BakedQuad uses Vector3fc positions,
    // packed long UVs, BakedNormals, BakedColors
    //
    // ############################

    /** Converts this MutableQuad into a NeoForge 1.21.11 BakedQuad. */
    public BakedQuad toBakedBlock() {
        return new BakedQuad(
            vertex_0.positionvf(), vertex_1.positionvf(),
            vertex_2.positionvf(), vertex_3.positionvf(),
            UVPair.pack(vertex_0.tex_u, vertex_0.tex_v),
            UVPair.pack(vertex_1.tex_u, vertex_1.tex_v),
            UVPair.pack(vertex_2.tex_u, vertex_2.tex_v),
            UVPair.pack(vertex_3.tex_u, vertex_3.tex_v),
            tintIndex, face, sprite, shade, lightEmission,
            BakedNormals.of(
                vertex_0.normalToPackedInt(), vertex_1.normalToPackedInt(),
                vertex_2.normalToPackedInt(), vertex_3.normalToPackedInt()
            ),
            BakedColors.of(
                ARGB.color(vertex_0.colour_a, vertex_0.colour_r, vertex_0.colour_g, vertex_0.colour_b),
                ARGB.color(vertex_1.colour_a, vertex_1.colour_r, vertex_1.colour_g, vertex_1.colour_b),
                ARGB.color(vertex_2.colour_a, vertex_2.colour_r, vertex_2.colour_g, vertex_2.colour_b),
                ARGB.color(vertex_3.colour_a, vertex_3.colour_r, vertex_3.colour_g, vertex_3.colour_b)
            ),
            hasAmbientOcclusion
        );
    }

    /** In 1.21.11, block and item use the same vertex format. Alias for {@link #toBakedBlock()}. */
    public BakedQuad toBakedItem() {
        return toBakedBlock();
    }

    /** Renders this quad directly into the given VertexConsumer, applying the given PoseStack transform.
     * The tex_u/tex_v values should already be atlas-mapped (via texFromSprite). */
    public void render(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer buffer) {
        renderVertex(pose, buffer, vertex_0);
        renderVertex(pose, buffer, vertex_1);
        renderVertex(pose, buffer, vertex_2);
        renderVertex(pose, buffer, vertex_3);
    }

    private static void renderVertex(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer buffer, MutableVertex v) {
        Vector3f pos = new Vector3f(v.position_x, v.position_y, v.position_z);
        pose.pose().transformPosition(pos);
        Vector3f norm = new Vector3f(v.normal_x, v.normal_y, v.normal_z);
        pose.normal().transform(norm);
        buffer.addVertex(pos.x, pos.y, pos.z)
              .setColor(v.colour_r, v.colour_g, v.colour_b, v.colour_a)
              .setUv(v.tex_u, v.tex_v)
              .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
              .setUv2(v.light_block << 4, v.light_sky << 4)
              .setNormal(norm.x, norm.y, norm.z);
    }

    /** Reads a BakedQuad's data into this MutableQuad. */
    public MutableQuad fromBakedBlock(BakedQuad quad) {
        tintIndex = quad.tintIndex();
        face = quad.direction();
        sprite = quad.sprite();
        shade = quad.shade();
        lightEmission = quad.lightEmission();
        hasAmbientOcclusion = quad.hasAmbientOcclusion();

        readVertexFromBaked(vertex_0, quad.position0(), quad.packedUV0(),
            quad.bakedNormals().normal(0), quad.bakedColors().color(0));
        readVertexFromBaked(vertex_1, quad.position1(), quad.packedUV1(),
            quad.bakedNormals().normal(1), quad.bakedColors().color(1));
        readVertexFromBaked(vertex_2, quad.position2(), quad.packedUV2(),
            quad.bakedNormals().normal(2), quad.bakedColors().color(2));
        readVertexFromBaked(vertex_3, quad.position3(), quad.packedUV3(),
            quad.bakedNormals().normal(3), quad.bakedColors().color(3));

        return this;
    }

    private static void readVertexFromBaked(MutableVertex v, Vector3fc pos, long packedUV,
            int packedNormal, int argbColor) {
        v.positionf(pos.x(), pos.y(), pos.z());
        // UVPair.pack stores two floats in a long; unpack manually
        v.texf(Float.intBitsToFloat((int) (packedUV >> 32)), Float.intBitsToFloat((int) packedUV));
        v.normali(packedNormal);
        // BakedColors stores ARGB, convert to our r/g/b/a shorts
        v.colour_a = (short) ((argbColor >> 24) & 0xFF);
        v.colour_r = (short) ((argbColor >> 16) & 0xFF);
        v.colour_g = (short) ((argbColor >> 8) & 0xFF);
        v.colour_b = (short) (argbColor & 0xFF);
    }

    /** Alias for {@link #fromBakedBlock(BakedQuad)}. */
    public MutableQuad fromBakedItem(BakedQuad quad) {
        return fromBakedBlock(quad);
    }

    public void render(VertexConsumer bb) {
        vertex_0.render(bb);
        vertex_1.render(bb);
        vertex_2.render(bb);
        vertex_3.render(bb);
    }

    // ############################
    //
    // Normal / Diffuse calculations
    //
    // ############################

    public Vector3f getCalculatedNormal() {
        Vector3f a = new Vector3f(vertex_1.positionvf());
        a.sub(vertex_0.positionvf());

        Vector3f b = new Vector3f(vertex_2.positionvf());
        b.sub(vertex_0.positionvf());

        Vector3f c = new Vector3f();
        c.set(a).cross(b);
        return c;
    }

    public void setCalculatedNormal() {
        normalvf(getCalculatedNormal());
    }

    public static float diffuseLight(Vector3f normal) {
        return diffuseLight(normal.x, normal.y, normal.z);
    }

    public static float diffuseLight(float x, float y, float z) {
        boolean up = y >= 0;

        float xx = x * x;
        float yy = y * y;
        float zz = z * z;

        float t = xx + yy + zz;
        float light = (xx * 0.6f + zz * 0.8f) / t;

        float yyt = yy / t;
        if (!up) yyt *= 0.5;
        light += yyt;

        return light;
    }

    public float getCalculatedDiffuse() {
        return diffuseLight(getCalculatedNormal());
    }

    public void setDiffuse(Vector3f normal) {
        float diffuse = diffuseLight(normal);
        colourf(diffuse, diffuse, diffuse, 1);
    }

    public void setCalculatedDiffuse() {
        float diffuse = getCalculatedDiffuse();
        colourf(diffuse, diffuse, diffuse, 1);
    }

    /** Inverts a copy of this quad's normal so that it will render in the opposite direction. You will need to recall
     * diffusion calculations if you had previously calculated the diffuse. */
    public MutableQuad copyAndInvertNormal() {
        MutableQuad copy = new MutableQuad(this);
        copy.vertex_0.copyFrom(vertex_3).invertNormal();
        copy.vertex_1.copyFrom(vertex_2).invertNormal();
        copy.vertex_2.copyFrom(vertex_1).invertNormal();
        copy.vertex_3.copyFrom(vertex_0).invertNormal();
        return copy;
    }

    public MutableQuad rotateTextureUp(int times) {
        switch (times & 3) {
            case 0: {
                return this;
            }
            case 1: {
                Vector2f t = vertex_0.tex();
                vertex_0.texv(vertex_1.tex());
                vertex_1.texv(vertex_2.tex());
                vertex_2.texv(vertex_3.tex());
                vertex_3.texv(t);
                return this;
            }
            case 2: {
                Vector2f t0 = vertex_0.tex();
                Vector2f t1 = vertex_1.tex();
                vertex_0.texv(vertex_2.tex());
                vertex_1.texv(vertex_3.tex());
                vertex_2.texv(t0);
                vertex_3.texv(t1);
                return this;
            }
            case 3: {
                Vector2f t = vertex_3.tex();
                vertex_3.texv(vertex_2.tex());
                vertex_2.texv(vertex_1.tex());
                vertex_1.texv(vertex_0.tex());
                vertex_0.texv(t);
                return this;
            }
            default: {
                throw new IllegalStateException("'times & 3' was not 0, 1, 2 or 3!");
            }
        }
    }

    // ############################
    //
    // Delegate vertex functions
    //
    // Basically a lot of functions that
    // change every vertex in the same way
    //
    // ############################

    /* Normal */

    /** Sets the normal for all vertices to the specified float coordinates. */
    public MutableQuad normalf(float x, float y, float z) {
        vertex_0.normalf(x, y, z);
        vertex_1.normalf(x, y, z);
        vertex_2.normalf(x, y, z);
        vertex_3.normalf(x, y, z);
        return this;
    }

    /** Sets the normal for all vertices to the specified double coordinates. */
    public MutableQuad normald(double x, double y, double z) {
        return normalf((float) x, (float) y, (float) z);
    }

    /** Sets the normal for all vertices to the specified {@link Vector3f}. */
    public MutableQuad normalvf(Vector3f vec) {
        return normalf(vec.x, vec.y, vec.z);
    }

    /** Sets the normal for all vertices to the specified {@link Vec3}. */
    public MutableQuad normalvd(Vec3 vec) {
        return normald(vec.x, vec.y, vec.z);
    }

    // VecDouble normalvd removed: expression subproject not on buildcraft-lib classpath

    /** @return A new {@link Vector3f} with the normal of the first vertex. Only useful if the normal is expected to be
     *         the same for every vertex. */
    public Vector3f normalvf() {
        return new Vector3f(vertex_0.normal_x, vertex_0.normal_y, vertex_0.normal_z);
    }

    /** @return A new {@link Vec3} with the normal of the first vertex. Only useful if the normal is expected to be the
     *         same for every vertex. */
    public Vec3 normalvd() {
        return new Vec3(vertex_0.normal_x, vertex_0.normal_y, vertex_0.normal_z);
    }

    /* Colour */

    public MutableQuad colouri(int r, int g, int b, int a) {
        vertex_0.colouri(r, g, b, a);
        vertex_1.colouri(r, g, b, a);
        vertex_2.colouri(r, g, b, a);
        vertex_3.colouri(r, g, b, a);
        return this;
    }

    public MutableQuad colouri(int rgba) {
        vertex_0.colouri(rgba);
        vertex_1.colouri(rgba);
        vertex_2.colouri(rgba);
        vertex_3.colouri(rgba);
        return this;
    }

    public MutableQuad colourf(float r, float g, float b, float a) {
        vertex_0.colourf(r, g, b, a);
        vertex_1.colourf(r, g, b, a);
        vertex_2.colourf(r, g, b, a);
        vertex_3.colourf(r, g, b, a);
        return this;
    }

    // VecLong colourvl removed: expression subproject not on buildcraft-lib classpath

    public MutableQuad colourvf(Vector4f vec) {
        return colourf(vec.x, vec.y, vec.z, vec.w);
    }

    public MutableQuad multColourd(double r, double g, double b, double a) {
        vertex_0.multColourd(r, g, b, a);
        vertex_1.multColourd(r, g, b, a);
        vertex_2.multColourd(r, g, b, a);
        vertex_3.multColourd(r, g, b, a);
        return this;
    }

    public MutableQuad multColourd(double by) {
        int m = (int) (by * 255);
        return multColouri(m);
    }

    public MutableQuad multColouri(int by) {
        vertex_0.multColouri(by);
        vertex_1.multColouri(by);
        vertex_2.multColouri(by);
        vertex_3.multColouri(by);
        return this;
    }

    public MutableQuad multColouri(int r, int g, int b, int a) {
        r &= 0xFF;
        g &= 0xFF;
        b &= 0xFF;
        a &= 0xFF;
        vertex_0.multColouri(r, g, b, a);
        vertex_1.multColouri(r, g, b, a);
        vertex_2.multColouri(r, g, b, a);
        vertex_3.multColouri(r, g, b, a);
        return this;
    }

    /** Multiplies every vertex by {@link #diffuseLight(float, float, float)} for the normal, if {@link #isShade()}
     * returns true. Also sets {@link #isShade()} to false. */
    public MutableQuad multShade() {
        if (isShade()) {
            setShade(false);
            vertex_0.multShade();
            vertex_1.multShade();
            vertex_2.multShade();
            vertex_3.multShade();
        }
        return this;
    }

    /* Texture co-ords */

    public MutableQuad texFromSprite(TextureAtlasSprite sprite) {
        vertex_0.texFromSprite(sprite);
        vertex_1.texFromSprite(sprite);
        vertex_2.texFromSprite(sprite);
        vertex_3.texFromSprite(sprite);
        return this;
    }

    /* Lightmap texture co-ords */

    public MutableQuad lighti(int block, int sky) {
        vertex_0.lighti(block, sky);
        vertex_1.lighti(block, sky);
        vertex_2.lighti(block, sky);
        vertex_3.lighti(block, sky);
        return this;
    }

    public MutableQuad lighti(int combined) {
        vertex_0.lighti(combined);
        vertex_1.lighti(combined);
        vertex_2.lighti(combined);
        vertex_3.lighti(combined);
        return this;
    }

    public MutableQuad lightf(float block, float sky) {
        return lighti((int) (block * 15), (int) (sky * 15));
    }

    public MutableQuad lightvf(Vector2f vec) {
        return lightf(vec.x, vec.y);
    }

    /** Sets the current light value of every vertex to be the maximum of the given in value, and the current value */
    public MutableQuad maxLighti(int block, int sky) {
        vertex_0.maxLighti(block, sky);
        vertex_1.maxLighti(block, sky);
        vertex_2.maxLighti(block, sky);
        vertex_3.maxLighti(block, sky);
        return this;
    }

    /* Transforms */

    public MutableQuad transform(Matrix4f transformation) {
        vertex_0.transform(transformation);
        vertex_1.transform(transformation);
        vertex_2.transform(transformation);
        vertex_3.transform(transformation);
        return this;
    }

    public MutableQuad translatei(int x, int y, int z) {
        return translatef(x, y, z);
    }

    public MutableQuad translatef(float x, float y, float z) {
        vertex_0.translatef(x, y, z);
        vertex_1.translatef(x, y, z);
        vertex_2.translatef(x, y, z);
        vertex_3.translatef(x, y, z);
        return this;
    }

    public MutableQuad translated(double x, double y, double z) {
        return translatef((float) x, (float) y, (float) z);
    }

    public MutableQuad translatevi(Vec3i vec) {
        return translatei(vec.getX(), vec.getY(), vec.getZ());
    }

    public MutableQuad translatevf(Vector3f vec) {
        return translatef(vec.x, vec.y, vec.z);
    }

    public MutableQuad translatevd(Vec3 vec) {
        return translated(vec.x, vec.y, vec.z);
    }

    public MutableQuad scalef(float scale) {
        vertex_0.scalef(scale);
        vertex_1.scalef(scale);
        vertex_2.scalef(scale);
        vertex_3.scalef(scale);
        return this;
    }

    public MutableQuad scaled(double scale) {
        return scalef((float) scale);
    }

    public MutableQuad scalef(float x, float y, float z) {
        vertex_0.scalef(x, y, z);
        vertex_1.scalef(x, y, z);
        vertex_2.scalef(x, y, z);
        vertex_3.scalef(x, y, z);
        return this;
    }

    public MutableQuad scaled(double x, double y, double z) {
        return scalef((float) x, (float) y, (float) z);
    }

    public void rotateX(float angle) {
        vertex_0.rotateX(angle);
        vertex_1.rotateX(angle);
        vertex_2.rotateX(angle);
        vertex_3.rotateX(angle);
    }

    public void rotateY(float angle) {
        vertex_0.rotateY(angle);
        vertex_1.rotateY(angle);
        vertex_2.rotateY(angle);
        vertex_3.rotateY(angle);
    }

    public void rotateZ(float angle) {
        vertex_0.rotateZ(angle);
        vertex_1.rotateZ(angle);
        vertex_2.rotateZ(angle);
        vertex_3.rotateZ(angle);
    }

    public void rotateDirectlyX(float cos, float sin) {
        vertex_0.rotateDirectlyX(cos, sin);
        vertex_1.rotateDirectlyX(cos, sin);
        vertex_2.rotateDirectlyX(cos, sin);
        vertex_3.rotateDirectlyX(cos, sin);
    }

    public void rotateDirectlyY(float cos, float sin) {
        vertex_0.rotateDirectlyY(cos, sin);
        vertex_1.rotateDirectlyY(cos, sin);
        vertex_2.rotateDirectlyY(cos, sin);
        vertex_3.rotateDirectlyY(cos, sin);
    }

    public void rotateDirectlyZ(float cos, float sin) {
        vertex_0.rotateDirectlyZ(cos, sin);
        vertex_1.rotateDirectlyZ(cos, sin);
        vertex_2.rotateDirectlyZ(cos, sin);
        vertex_3.rotateDirectlyZ(cos, sin);
    }

    public MutableQuad rotate(Direction from, Direction to, float ox, float oy, float oz) {
        if (from == to) {
            // don't bother rotating: there is nothing to rotate!
            return this;
        }

        translatef(-ox, -oy, -oz);
        // @formatter:off
        switch (from.getAxis()) {
            case X: {
                int mult = from.getStepX();
                switch (to.getAxis()) {
                    case X: rotateY_180(); break;
                    case Y: rotateZ_90(mult * to.getStepY()); break;
                    case Z: rotateY_90(mult * to.getStepZ()); break;
                }
                break;
            }
            case Y: {
                int mult = from.getStepY();
                switch (to.getAxis()) {
                    case X: rotateZ_90(-mult * to.getStepX()); break;
                    case Y: rotateZ_180(); break;
                    case Z: rotateX_90(mult * to.getStepZ()); break;
                }
                break;
            }
            case Z: {
                int mult = -from.getStepZ();
                switch (to.getAxis()) {
                    case X: rotateY_90(mult * to.getStepX()); break;
                    case Y: rotateX_90(mult * to.getStepY()); break;
                    case Z: rotateY_180(); break;
                }
                break;
            }
        }
        // @formatter:on
        translatef(ox, oy, oz);
        return this;
    }

    public MutableQuad rotateX_90(float scale) {
        vertex_0.rotateX_90(scale);
        vertex_1.rotateX_90(scale);
        vertex_2.rotateX_90(scale);
        vertex_3.rotateX_90(scale);
        return this;
    }

    public MutableQuad rotateY_90(float scale) {
        vertex_0.rotateY_90(scale);
        vertex_1.rotateY_90(scale);
        vertex_2.rotateY_90(scale);
        vertex_3.rotateY_90(scale);
        return this;
    }

    public MutableQuad rotateZ_90(float scale) {
        vertex_0.rotateZ_90(scale);
        vertex_1.rotateZ_90(scale);
        vertex_2.rotateZ_90(scale);
        vertex_3.rotateZ_90(scale);
        return this;
    }

    public MutableQuad rotateX_180() {
        vertex_0.rotateX_180();
        vertex_1.rotateX_180();
        vertex_2.rotateX_180();
        vertex_3.rotateX_180();
        return this;
    }

    public MutableQuad rotateY_180() {
        vertex_0.rotateY_180();
        vertex_1.rotateY_180();
        vertex_2.rotateY_180();
        vertex_3.rotateY_180();
        return this;
    }

    public MutableQuad rotateZ_180() {
        vertex_0.rotateZ_180();
        vertex_1.rotateZ_180();
        vertex_2.rotateZ_180();
        vertex_3.rotateZ_180();
        return this;
    }

    @Override
    public String toString() {
        return "MutableQuad [vertices=" + vToS() + ", tintIndex=" + tintIndex + ", face=" + face + "]";
    }

    private String vToS() {
        return "[ " + vertex_0 + ", " + vertex_1 + ", " + vertex_2 + ", " + vertex_3 + " ]";
    }
}
