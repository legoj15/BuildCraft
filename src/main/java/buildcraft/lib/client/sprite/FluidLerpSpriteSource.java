/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.sprite;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.LazyLoadedImage;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Synthesises BuildCraft energy-fluid atlas sprites from a single grayscale
 * "heat" base texture at stitch time, replicating the historical 1.12.2
 * {@code AtlasSpriteFluid} per-pixel recolor.
 *
 * <p>For one entry — a fluid and a frame ({@code still}/{@code flow}) — this
 * emits the three heat-tier sprites
 * {@code <output>_heat_0_<frame>}, {@code _heat_1_}, {@code _heat_2_}. All
 * three share the same recolored pixels and differ only in animation
 * {@code frametime} (3 / 2 / 1), which is the per-heat speed cue.
 *
 * <p>The recolor is the 1.12.2 per-channel intensity lerp: for each base pixel
 * {@code W} and the fluid's {@code light}/{@code dark} endpoints,
 * {@code out = (dark·(256−W) + light·W) / 256} per channel, alpha forced
 * opaque. Output is bit-identical to the pre-baked
 * {@code <fluid>_heat_<n>_<frame>.png} files that used to ship in
 * {@code assets/.../textures/block/fluids/}, so those 60 PNGs are no longer
 * needed on disk.
 *
 * <p>The {@code light}/{@code dark} pairs mirror
 * {@code buildcraft.energy.BCEnergyFluids#FLUID_DATA}.
 *
 * <p>JSON form (must live inside {@code assets/minecraft/atlases/blocks.json} —
 * the vanilla blocks atlas only loads its config from the {@code minecraft}
 * namespace):
 * <pre>
 * {
 *   "type":   "buildcraftunofficial:fluid_lerp",
 *   "source": "buildcraftunofficial:block/fluids/heat_still",
 *   "output": "buildcraftunofficial:block/fluids/oil",
 *   "frame":  "still",
 *   "light":  5263440,
 *   "dark":   328197
 * }
 * </pre>
 */
public record FluidLerpSpriteSource(Identifier source, Identifier output, String frame, int light, int dark)
        implements SpriteSource {

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "fluid_lerp");

    public static final MapCodec<FluidLerpSpriteSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(FluidLerpSpriteSource::source),
            Identifier.CODEC.fieldOf("output").forGetter(FluidLerpSpriteSource::output),
            Codec.STRING.fieldOf("frame").forGetter(FluidLerpSpriteSource::frame),
            Codec.INT.fieldOf("light").forGetter(FluidLerpSpriteSource::light),
            Codec.INT.fieldOf("dark").forGetter(FluidLerpSpriteSource::dark)
        ).apply(i, FluidLerpSpriteSource::new)
    );

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per-heat-tier animation frametime — heat 0 is slowest, heat 2 is the vanilla default. */
    private static final int[] HEAT_FRAMETIMES = { 3, 2, 1 };

    /**
     * Recolors one grayscale heat-base pixel into a fluid pixel via the 1.12.2
     * {@code AtlasSpriteFluid} per-channel intensity lerp
     * {@code out = (dark·(256−W) + light·W) / 256}. Input alpha is discarded;
     * output alpha is forced opaque, matching the original recolor.
     *
     * @param basePixel the heat-base pixel, ARGB
     * @param light     the fluid's light endpoint, {@code 0xRRGGBB}
     * @param dark      the fluid's dark endpoint, {@code 0xRRGGBB}
     * @return the recolored pixel, ARGB (opaque)
     */
    static int recolour(int basePixel, int light, int dark) {
        int wr = (basePixel >> 16) & 0xFF;
        int wg = (basePixel >> 8)  & 0xFF;
        int wb =  basePixel        & 0xFF;
        int lr = (light >> 16) & 0xFF, lg = (light >> 8) & 0xFF, lb = light & 0xFF;
        int dr = (dark  >> 16) & 0xFF, dg = (dark  >> 8) & 0xFF, db = dark  & 0xFF;
        int outR = (dr * (256 - wr) + lr * wr) / 256;
        int outG = (dg * (256 - wg) + lg * wg) / 256;
        int outB = (db * (256 - wb) + lb * wb) / 256;
        return (0xFF << 24) | (outR << 16) | (outG << 8) | outB;
    }

    @Override
    public void run(ResourceManager rm, Output spriteOutput) {
        Identifier sourceTexId = TEXTURE_ID_CONVERTER.idToFile(source);

        Optional<Resource> sourceRes = rm.getResource(sourceTexId);
        if (sourceRes.isEmpty()) {
            LOGGER.warn("FluidLerpSpriteSource: source texture {} not found", sourceTexId);
            return;
        }

        // One shared base image, consumed once per heat tier (release on get or discard).
        LazyLoadedImage baseImage = new LazyLoadedImage(sourceTexId, sourceRes.get(), HEAT_FRAMETIMES.length);

        for (int heat = 0; heat < HEAT_FRAMETIMES.length; heat++) {
            Identifier outputId = output.withSuffix("_heat_" + heat + "_" + frame);
            spriteOutput.add(outputId, new Loader(baseImage, light, dark, HEAT_FRAMETIMES[heat], outputId));
        }
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return MAP_CODEC;
    }

    private record Loader(LazyLoadedImage base, int light, int dark, int frametime, Identifier outputId)
            //? if >=1.21.11 {
            implements DiscardableLoader {
            //?} else {
            /*implements net.minecraft.client.renderer.texture.atlas.SpriteSource.SpriteSupplier {*/
            //?}

        @Override
        //? if >=1.21.11 {
        public @Nullable SpriteContents get(SpriteResourceLoader loader) {
        //?} else {
        /*public @Nullable SpriteContents apply(SpriteResourceLoader loader) {*/
        //?}
            NativeImage out = null;
            try {
                NativeImage baseImg = base.get();
                int w = baseImg.getWidth();
                int h = baseImg.getHeight();

                out = new NativeImage(w, h, false);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        out.setPixel(x, y, recolour(baseImg.getPixel(x, y), light, dark));
                    }
                }

                // Vertical strip of square frames; per-heat animation speed.
                FrameSize frameSize = new FrameSize(w, w);
                AnimationMetadataSection animation = new AnimationMetadataSection(
                    Optional.empty(), Optional.empty(), Optional.empty(), frametime, true);
                //? if >=1.21.11 {
                SpriteContents result = new SpriteContents(
                    outputId, frameSize, out, Optional.of(animation), List.of(), Optional.empty());
                //?} else {
                /*SpriteContents result = new SpriteContents(
                    outputId, frameSize, out, Optional.of(animation), List.of());*/
                //?}
                out = null; // ownership transferred to SpriteContents
                return result;
            } catch (IOException e) {
                LOGGER.error("FluidLerpSpriteSource: failed to generate {}", outputId, e);
                return null;
            } finally {
                if (out != null) out.close();
                base.release();
            }
        }

        @Override
        public void discard() {
            base.release();
        }
    }
}
