package buildcraft.energy.client.sprite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buildcraft.energy.BCEnergy;

/**
 * A custom {@link SpriteSource} that replicates 1.12's {@code AtlasSpriteFluid} recoloring.
 *
 * <p>At atlas stitching time, loads the vanilla water_still and water_flow textures,
 * applies the 1.12 recoloring formula per pixel, and registers the recolored sprites.
 *
 * <p>Formula: {@code result = (dark * (256 - data) + light * data) / 256}
 * where {@code data} is the source pixel brightness (0=black, 255=white).
 *
 * <p>NativeImage pixel format is ABGR: {@code (A << 24) | (B << 16) | (G << 8) | R}
 */
public class FluidRecolorSpriteSource implements SpriteSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(FluidRecolorSpriteSource.class);

    public static final Identifier ID = Identifier.fromNamespaceAndPath(BCEnergy.MODID, "fluid_recolor");

    public static final MapCodec<FluidRecolorSpriteSource> CODEC =
            MapCodec.unit(FluidRecolorSpriteSource::new);

    // 1.12 fluid data
    private static final String[] NAMES = {
        "oil", "oil_residue", "oil_heavy", "oil_dense", "oil_distilled",
        "fuel_dense", "fuel_mixed_heavy", "fuel_light", "fuel_mixed_light", "fuel_gaseous"
    };

    private static final int[] TEX_LIGHT = {
        0x505050, 0x100F10, 0xA08F1F, 0x876E77, 0xE4AF78,
        0xFFAF3F, 0xF2A700, 0xFFFF30, 0xF6D700, 0xFAF630
    };

    private static final int[] TEX_DARK = {
        0x050505, 0x421042, 0x423520, 0x422424, 0xB47F00,
        0xE07F00, 0xC48700, 0xE4CF00, 0xC4B700, 0xE0D900
    };

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        LOGGER.info("[energy.sprite] FluidRecolorSpriteSource running...");

        // Load vanilla water textures
        Identifier waterStillPath = Identifier.withDefaultNamespace("textures/block/water_still.png");
        Identifier waterFlowPath = Identifier.withDefaultNamespace("textures/block/water_flow.png");

        var waterStillOpt = resourceManager.getResource(waterStillPath);
        var waterFlowOpt = resourceManager.getResource(waterFlowPath);

        if (waterStillOpt.isEmpty() || waterFlowOpt.isEmpty()) {
            LOGGER.error("[energy.sprite] Cannot find vanilla water textures!");
            return;
        }

        Resource waterStillRes = waterStillOpt.get();
        Resource waterFlowRes = waterFlowOpt.get();

        NativeImage waterStill, waterFlow;
        try {
            waterStill = NativeImage.read(waterStillRes.open());
            waterFlow = NativeImage.read(waterFlowRes.open());
        } catch (IOException e) {
            LOGGER.error("[energy.sprite] Failed to read water textures", e);
            return;
        }

        int generated = 0;

        for (int i = 0; i < NAMES.length; i++) {
            String baseName = NAMES[i];
            int light = TEX_LIGHT[i];
            int dark = TEX_DARK[i];

            for (int heat = 0; heat < 3; heat++) {
                String suffix = heat == 0 ? "" : "_heat_" + heat;
                String fullName = baseName + suffix;

                // Generate recolored still texture
                NativeImage recoloredStill = recolourImage(waterStill, light, dark);
                Identifier stillId = Identifier.fromNamespaceAndPath(
                        BCEnergy.MODID, "block/fluid/" + fullName + "_still");
                addSpriteFromImage(output, stillId, recoloredStill, waterStillRes);

                // Generate recolored flow texture
                NativeImage recoloredFlow = recolourImage(waterFlow, light, dark);
                Identifier flowId = Identifier.fromNamespaceAndPath(
                        BCEnergy.MODID, "block/fluid/" + fullName + "_flow");
                addSpriteFromImage(output, flowId, recoloredFlow, waterFlowRes);

                generated++;
            }
        }

        waterStill.close();
        waterFlow.close();

        LOGGER.info("[energy.sprite] Generated {} fluid texture variants ({} sprites)",
                generated, generated * 2);
    }

    /**
     * Write a NativeImage to a temp file, read the PNG bytes, and add to the atlas.
     * The original Resource is used to inherit animation metadata.
     */
    private void addSpriteFromImage(Output output, Identifier id,
                                     NativeImage image, Resource originalResource) {
        try {
            // Write to temp file (NativeImage only supports file-based PNG export)
            Path tempFile = Files.createTempFile("bc_fluid_", ".png");
            try {
                image.writeToFile(tempFile);
                byte[] pngBytes = Files.readAllBytes(tempFile);

                IoSupplier<InputStream> pngSupplier = () -> new ByteArrayInputStream(pngBytes);
                Resource resource = new Resource(
                        originalResource.source(), pngSupplier, originalResource::metadata);
                output.add(id, resource);
            } finally {
                Files.deleteIfExists(tempFile);
                image.close();
            }
        } catch (IOException e) {
            LOGGER.error("[energy.sprite] Failed to encode texture for {}", id, e);
        }
    }

    /**
     * Apply the 1.12 AtlasSpriteFluid recoloring formula to every pixel.
     *
     * <p>NativeImage pixel format is ABGR: {@code (A << 24) | (B << 16) | (G << 8) | R}
     */
    private NativeImage recolourImage(NativeImage source, int lightRgb, int darkRgb) {
        int w = source.getWidth();
        int h = source.getHeight();
        NativeImage result = new NativeImage(w, h, false);

        int lr = (lightRgb >> 16) & 0xFF;
        int lg = (lightRgb >> 8) & 0xFF;
        int lb = lightRgb & 0xFF;
        int dr = (darkRgb >> 16) & 0xFF;
        int dg = (darkRgb >> 8) & 0xFF;
        int db = darkRgb & 0xFF;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = source.getPixel(x, y);
                int a = (abgr >> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;

                if (a == 0) {
                    result.setPixel(x, y, 0);
                    continue;
                }

                // 1.12 formula: result = (dark * (256 - data) + light * data) / 256
                int nr = (dr * (256 - r) + lr * r) / 256;
                int ng = (dg * (256 - g) + lg * g) / 256;
                int nb = (db * (256 - b) + lb * b) / 256;

                // Pack back to ABGR
                result.setPixel(x, y, (0xFF << 24) | (nb << 16) | (ng << 8) | nr);
            }
        }
        return result;
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
