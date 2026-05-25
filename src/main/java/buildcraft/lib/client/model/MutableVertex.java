/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.model;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;




import buildcraft.api.core.render.ISprite;

/**
 * Holds all of the information necessary to make one of the vertices in a {@link BakedQuad}. This provides a variety
 * of methods to quickly set or get different elements. This should be used with {@link MutableQuad} to make a face, or
 * by itself if you only need to define a single vertex. <br>
 * This currently holds the 3D position, normal, colour, 2D texture, skylight and blocklight. Note that you don't have
 * to use all of the elements for this to work - the extra elements come with sensible defaults. <br>
 * All of the mutating methods are in the form {@literal <element><type>}, where {@literal <element>} is the element to
 * set/get, and {@literal <type>} is the type that they should be set as. So {@link #positiond(double, double, double)}
 * will take in 3 doubles and set them to the position element, and {@link #colouri(int, int, int, int)} will take in 4
 * int's and set them to the colour elements.
 */
@SuppressWarnings("this-escape")
public class MutableVertex {
    /** The position of this vertex. */
    public float position_x, position_y, position_z;
    /** The normal of this vertex. Might not be normalised. Default value is [0, 1, 0]. */
    public float normal_x, normal_y, normal_z;
    /** The colour of this vertex, where each one is a number in the range 0-255. Default value is 255. */
    public short colour_r, colour_g, colour_b, colour_a;
    /** The texture co-ord of this vertex. Should usually be between 0-1 */
    public float tex_u, tex_v;
    /** The light of this vertex. Should be in the range 0-15. */
    public byte light_block, light_sky;

    public MutableVertex() {
        normal_x = 0;
        normal_y = 1;
        normal_z = 0;

        colour_r = 0xFF;
        colour_g = 0xFF;
        colour_b = 0xFF;
        colour_a = 0xFF;
    }

    public MutableVertex(MutableVertex from) {
        copyFrom(from);
    }

    @Override
    public String toString() {
        return "{ pos = [ " + position_x + ", " + position_y + ", " + position_z //
                + " ], norm = [ " + normal_x + ", " + normal_y + ", " + normal_z//
                + " ], colour = [ " + colour_r + ", " + colour_g + ", " + colour_b + ", " + colour_a//
                + " ], tex = [ " + tex_u + ", " + tex_v //
                + " ], light_block = " + light_block + ", light_sky = " + light_sky + " }";
    }

    public MutableVertex copyFrom(MutableVertex from) {
        position_x = from.position_x;
        position_y = from.position_y;
        position_z = from.position_z;

        normal_x = from.normal_x;
        normal_y = from.normal_y;
        normal_z = from.normal_z;

        colour_r = from.colour_r;
        colour_g = from.colour_g;
        colour_b = from.colour_b;
        colour_a = from.colour_a;

        tex_u = from.tex_u;
        tex_v = from.tex_v;

        light_block = from.light_block;
        light_sky = from.light_sky;
        return this;
    }

    // ############################
    //
    // Baked quad serialization
    // 1.21 BLOCK format: 8 ints per vertex
    //   [0-2] position (3 floats)
    //   [3]   colour (RGBA packed)
    //   [4-5] texture UV (2 floats)
    //   [6]   lightmap UV (packed)
    //   [7]   normal (packed byte x/y/z)
    //
    // ############################

    /** Writes this vertex into the given int array at the specified offset using the 1.21 BLOCK vertex format
     * (8 ints per vertex). */
    public void toBakedBlock(int[] data, int offset) {
        // POSITION_3F
        data[offset + 0] = Float.floatToRawIntBits(position_x);
        data[offset + 1] = Float.floatToRawIntBits(position_y);
        data[offset + 2] = Float.floatToRawIntBits(position_z);
        // COLOR_4UB
        data[offset + 3] = colourRGBA();
        // TEX_2F
        data[offset + 4] = Float.floatToRawIntBits(tex_u);
        data[offset + 5] = Float.floatToRawIntBits(tex_v);
        // TEX_2S (lightmap)
        data[offset + 6] = lightc();
        // NORMAL_3B
        data[offset + 7] = normalToPackedInt();
    }

    /** Alias for {@link #toBakedBlock(int[], int)} — in 1.21 block and item use the same vertex format. */
    public void toBakedItem(int[] data, int offset) {
        toBakedBlock(data, offset);
    }

    /** Reads this vertex from the given int array at the specified offset using the 1.21 BLOCK vertex format
     * (8 ints per vertex). */
    public void fromBakedBlock(int[] data, int offset) {
        // POSITION_3F
        position_x = Float.intBitsToFloat(data[offset + 0]);
        position_y = Float.intBitsToFloat(data[offset + 1]);
        position_z = Float.intBitsToFloat(data[offset + 2]);
        // COLOR_4UB
        colouri(data[offset + 3]);
        // TEX_2F
        tex_u = Float.intBitsToFloat(data[offset + 4]);
        tex_v = Float.intBitsToFloat(data[offset + 5]);
        // TEX_2S (lightmap)
        lighti(data[offset + 6]);
        // NORMAL_3B
        normali(data[offset + 7]);
    }

    /** Alias for {@link #fromBakedBlock(int[], int)} — in 1.21 block and item use the same vertex format. */
    public void fromBakedItem(int[] data, int offset) {
        fromBakedBlock(data, offset);
    }

    // ############################
    //
    // Rendering via VertexConsumer
    //
    // ############################

    /** Renders this vertex into the given {@link VertexConsumer} using the chained BLOCK format API. */
    public void render(VertexConsumer bb) {
        renderAsBlock(bb);
    }

    /** Renders this vertex into the given {@link VertexConsumer}, assuming that the format is BLOCK.
     * Uses the 1.21 chained vertex API. */
    public void renderAsBlock(VertexConsumer bb) {
        bb.addVertex(position_x, position_y, position_z)
          .setColor(colour_r, colour_g, colour_b, colour_a)
          .setUv(tex_u, tex_v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setUv2(light_block << 4, light_sky << 4)
          .setNormal(normal_x, normal_y, normal_z);
    }

    public void renderPosition(VertexConsumer bb) {
        bb.addVertex(position_x, position_y, position_z);
    }

    public void renderNormal(VertexConsumer bb) {
        bb.setNormal(normal_x, normal_y, normal_z);
    }

    public void renderColour(VertexConsumer bb) {
        bb.setColor(colour_r, colour_g, colour_b, colour_a);
    }

    public void renderTex(VertexConsumer bb) {
        bb.setUv(tex_u, tex_v);
    }

    public void renderTex(VertexConsumer bb, ISprite sprite) {
        bb.setUv((float) sprite.getInterpU(tex_u), (float) sprite.getInterpV(tex_v));
    }

    public void renderLightMap(VertexConsumer bb) {
        bb.setUv2(light_block << 4, light_sky << 4);
    }

    // ############################
    //
    // Mutating — Position
    //
    // ############################

    public MutableVertex positionv(Vector3f vec) {
        return positionf(vec.x, vec.y, vec.z);
    }

    public MutableVertex positiond(double x, double y, double z) {
        return positionf((float) x, (float) y, (float) z);
    }

    public MutableVertex positionf(float x, float y, float z) {
        position_x = x;
        position_y = y;
        position_z = z;
        return this;
    }

    public Vector3f positionvf() {
        return new Vector3f(position_x, position_y, position_z);
    }

    // ############################
    //
    // Mutating — Normal
    //
    // ############################

    /** Sets the current normal for this vertex based off the given vector.<br>
     * Note: This calls {@link #normalf(float, float, float)} internally, so refer to that for more warnings.
     *
     * @see #normalf(float, float, float) */
    public MutableVertex normalv(Vector3f vec) {
        return normalf(vec.x, vec.y, vec.z);
    }

    /** Sets the current normal given the x, y, and z coordinates. These are NOT normalised or checked. */
    public MutableVertex normalf(float x, float y, float z) {
        normal_x = x;
        normal_y = y;
        normal_z = z;
        return this;
    }

    public MutableVertex normali(int combined) {
        normal_x = ((byte) (combined & 0xFF)) / 127.0f;
        normal_y = ((byte) ((combined >> 8) & 0xFF)) / 127.0f;
        normal_z = ((byte) ((combined >> 16) & 0xFF)) / 127.0f;
        return this;
    }

    public MutableVertex invertNormal() {
        return normalf(-normal_x, -normal_y, -normal_z);
    }

    /** @return The current normal vector of this vertex. This might be normalised. */
    public Vector3f normal() {
        return new Vector3f(normal_x, normal_y, normal_z);
    }

    public int normalToPackedInt() {
        return normalAsByte(normal_x, 0) //
                | normalAsByte(normal_y, 8) //
                | normalAsByte(normal_z, 16);
    }

    private static int normalAsByte(float norm, int offset) {
        int as = (byte) (Mth.clamp(norm, -1.0f, 1.0f) * 127.0f);
        return (as & 0xFF) << offset;
    }

    // ############################
    //
    // Mutating — Colour
    //
    // ############################

    public MutableVertex colourv(Vector4f vec) {
        return colourf(vec.x, vec.y, vec.z, vec.w);
    }

    public MutableVertex colourf(float r, float g, float b, float a) {
        return colouri((int) (r * 0xFF), (int) (g * 0xFF), (int) (b * 0xFF), (int) (a * 0xFF));
    }

    public MutableVertex colouri(int rgba) {
        return colouri(rgba, rgba >> 8, rgba >> 16, rgba >>> 24);
    }

    public MutableVertex colouri(int r, int g, int b, int a) {
        colour_r = (short) (r & 0xFF);
        colour_g = (short) (g & 0xFF);
        colour_b = (short) (b & 0xFF);
        colour_a = (short) (a & 0xFF);
        return this;
    }

    public Vector4f colourv() {
        return new Vector4f(colour_r / 255f, colour_g / 255f, colour_b / 255f, colour_a / 255f);
    }

    public int colourRGBA() {
        int rgba = 0;
        rgba |= (colour_r & 0xFF) << 0;
        rgba |= (colour_g & 0xFF) << 8;
        rgba |= (colour_b & 0xFF) << 16;
        rgba |= (colour_a & 0xFF) << 24;
        return rgba;
    }

    public int colourABGR() {
        int rgba = 0;
        rgba |= (colour_r & 0xFF) << 24;
        rgba |= (colour_g & 0xFF) << 16;
        rgba |= (colour_b & 0xFF) << 8;
        rgba |= (colour_a & 0xFF) << 0;
        return rgba;
    }

    public MutableVertex multColourd(double d) {
        int m = (int) (d * 255);
        return multColouri(m);
    }

    public MutableVertex multColourd(double r, double g, double b, double a) {
        return multColouri((int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
    }

    public MutableVertex multColouri(int by) {
        return multColouri(by, by, by, 255);
    }

    public MutableVertex multColouri(int r, int g, int b, int a) {
        colour_r = (short) (colour_r * r / 255);
        colour_g = (short) (colour_g * g / 255);
        colour_b = (short) (colour_b * b / 255);
        colour_a = (short) (colour_a * a / 255);
        return this;
    }

    /** Multiplies the colour by {@link MutableQuad#diffuseLight(float, float, float)} for the normal. */
    public MutableVertex multShade() {
        return multColourd(MutableQuad.diffuseLight(normal_x, normal_y, normal_z));
    }

    // ############################
    //
    // Mutating — Texture
    //
    // ############################

    public MutableVertex texFromSprite(TextureAtlasSprite sprite) {
        tex_u = sprite.getU(tex_u);
        tex_v = sprite.getV(tex_v);
        return this;
    }

    public MutableVertex texv(Vector2f vec) {
        return texf(vec.x, vec.y);
    }

    public MutableVertex texf(float u, float v) {
        tex_u = u;
        tex_v = v;
        return this;
    }

    public Vector2f tex() {
        return new Vector2f(tex_u, tex_v);
    }

    // ############################
    //
    // Mutating — Light
    //
    // ############################

    public MutableVertex lightv(Vector2f vec) {
        return lightf(vec.x, vec.y);
    }

    public MutableVertex lightf(float block, float sky) {
        return lighti((int) (block * 0xF), (int) (sky * 0xF));
    }

    public MutableVertex lighti(int combined) {
        return lighti((combined >> 4) & 0xF, (combined >> 20) & 0xF);
    }

    public MutableVertex lighti(int block, int sky) {
        light_block = (byte) block;
        light_sky = (byte) sky;
        return this;
    }

    public MutableVertex maxLighti(int block, int sky) {
        return lighti(Math.max(block, light_block), Math.max(sky, light_sky));
    }

    public Vector2f lightvf() {
        return new Vector2f(light_block * 15f, light_sky * 15f);
    }

    public int lightc() {
        return (light_block << 4) | (light_sky << 20);
    }

    public int[] lighti() {
        return new int[] { light_block, light_sky };
    }

    // ############################
    //
    // Transforms
    //
    // ############################

    public MutableVertex transform(Matrix4f matrix) {
        Vector3f point = positionvf();
        matrix.transformPosition(point);
        positionv(point);

        Vector3f normal = normal();
        matrix.transformDirection(normal);
        normalv(normal);
        return this;
    }

    public MutableVertex translatei(int x, int y, int z) {
        position_x += x;
        position_y += y;
        position_z += z;
        return this;
    }

    public MutableVertex translatef(float x, float y, float z) {
        position_x += x;
        position_y += y;
        position_z += z;
        return this;
    }

    public MutableVertex translated(double x, double y, double z) {
        position_x += (float) x;
        position_y += (float) y;
        position_z += (float) z;
        return this;
    }

    public MutableVertex translatevi(Vec3i vec) {
        return translatei(vec.getX(), vec.getY(), vec.getZ());
    }

    public MutableVertex translatevd(Vec3 vec) {
        return translated(vec.x, vec.y, vec.z);
    }

    public MutableVertex scalef(float scale) {
        position_x *= scale;
        position_y *= scale;
        position_z *= scale;
        return this;
    }

    public MutableVertex scaled(double scale) {
        return scalef((float) scale);
    }

    public MutableVertex scalef(float x, float y, float z) {
        position_x *= x;
        position_y *= y;
        position_z *= z;
        // TODO: scale normals?
        return this;
    }

    public MutableVertex scaled(double x, double y, double z) {
        return scalef((float) x, (float) y, (float) z);
    }

    // ############################
    //
    // Rotations
    //
    // ############################

    /** Rotates around the X axis by angle. */
    public void rotateX(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        rotateDirectlyX(cos, sin);
    }

    /** Rotates around the Y axis by angle. */
    public void rotateY(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        rotateDirectlyY(cos, sin);
    }

    /** Rotates around the Z axis by angle. */
    public void rotateZ(float angle) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        rotateDirectlyZ(cos, sin);
    }

    public void rotateDirectlyX(float cos, float sin) {
        float y = position_y;
        float z = position_z;
        position_y = y * cos - z * sin;
        position_z = y * sin + z * cos;
    }

    public void rotateDirectlyY(float cos, float sin) {
        float x = position_x;
        float z = position_z;
        position_x = x * cos - z * sin;
        position_z = x * sin + z * cos;
    }

    public void rotateDirectlyZ(float cos, float sin) {
        float x = position_x;
        float y = position_y;
        position_x = x * cos + y * sin;
        position_y = x * -sin + y * cos;
    }

    /** Rotates this vertex around the X axis 90 degrees.
     *
     * @param scale The multiplier for scaling. Positive values will rotate clockwise, negative values rotate
     *            anti-clockwise. */
    public MutableVertex rotateX_90(float scale) {
        float ym = scale;
        float zm = -ym;

        float t = position_y * ym;
        position_y = position_z * zm;
        position_z = t;

        t = normal_y * ym;
        normal_y = normal_z * zm;
        normal_z = t;
        return this;
    }

    /** Rotates this vertex around the Y axis 90 degrees.
     *
     * @param scale The multiplier for scaling. Positive values will rotate clockwise, negative values rotate
     *            anti-clockwise. */
    public MutableVertex rotateY_90(float scale) {
        float xm = scale;
        float zm = -xm;

        float t = position_x * xm;
        position_x = position_z * zm;
        position_z = t;

        t = normal_x * xm;
        normal_x = normal_z * zm;
        normal_z = t;
        return this;
    }

    /** Rotates this vertex around the Z axis 90 degrees.
     *
     * @param scale The multiplier for scaling. Positive values will rotate clockwise, negative values rotate
     *            anti-clockwise. */
    public MutableVertex rotateZ_90(float scale) {
        float xm = scale;
        float ym = -xm;

        float t = position_x * xm;
        position_x = position_y * ym;
        position_y = t;

        t = normal_x * xm;
        normal_x = normal_y * ym;
        normal_y = t;
        return this;
    }

    /** Rotates this vertex around the X axis by 180 degrees. */
    public MutableVertex rotateX_180() {
        position_y = -position_y;
        position_z = -position_z;
        normal_y = -normal_y;
        normal_z = -normal_z;
        return this;
    }

    /** Rotates this vertex around the Y axis by 180 degrees. */
    public MutableVertex rotateY_180() {
        position_x = -position_x;
        position_z = -position_z;
        normal_x = -normal_x;
        normal_z = -normal_z;
        return this;
    }

    /** Rotates this vertex around the Z axis by 180 degrees. */
    public MutableVertex rotateZ_180() {
        position_x = -position_x;
        position_y = -position_y;
        normal_x = -normal_x;
        normal_y = -normal_y;
        return this;
    }
}
