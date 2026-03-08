package buildcraft.energy.client.sprite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import buildcraft.energy.BCEnergy;

/**
 * A custom {@link SpriteSource} that replicates 1.12's {@code AtlasSpriteFluid} recoloring.
 *
 * <p>At atlas stitching time, loads the vanilla water_still and water_flow textures,
 * applies the 1.12 recoloring formula per pixel, and registers the result as a new sprite.
 *
 * <p>Formula: {@code result = (dark * (256 - data) + light * data) / 256}
 * where {@code data} is the source pixel brightness (0=black, 255=white).
 */
public class FluidRecolorSpriteSource implements SpriteSource {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(BCEnergy.MODID, "fluid_recolor");

    // No codec fields needed — this source is self-contained, not JSON-driven
    public static final MapCodec<FluidRecolorSpriteSource> CODEC =
            MapCodec.unit(FluidRecolorSpriteSource::new);

    // 1.12 fluid data: name, tex_light, tex_dark
    // All 10 fluid types × 3 heat levels = 30 variants
    private static final String[] FLUID_NAMES = {
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
        // Load vanilla water textures
        Identifier waterStillId = Identifier.withDefaultNamespace("textures/block/water_still.png");
        Identifier waterFlowId = Identifier.withDefaultNamespace("textures/block/water_flow.png");

        Optional<Resource> waterStillRes = resourceManager.getResource(waterStillId);
        Optional<Resource> waterFlowRes = resourceManager.getResource(waterFlowId);

        if (waterStillRes.isEmpty() || waterFlowRes.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(FluidRecolorSpriteSource.class)
                    .error("[energy.sprite] Cannot find vanilla water textures!");
            return;
        }

        // Read base images
        BufferedImage waterStill, waterFlow;
        try {
            waterStill = ImageIO.read(waterStillRes.get().open());
            waterFlow = ImageIO.read(waterFlowRes.get().open());
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(FluidRecolorSpriteSource.class)
                    .error("[energy.sprite] Failed to read water textures", e);
            return;
        }

        // Generate recolored textures for each fluid variant
        for (int i = 0; i < FLUID_NAMES.length; i++) {
            String baseName = FLUID_NAMES[i];
            int light = TEX_LIGHT[i];
            int dark = TEX_DARK[i];

            for (int heat = 0; heat < 3; heat++) {
                String suffix = heat == 0 ? "" : "_heat_" + heat;
                String fullName = baseName + suffix;

                // Generate still texture
                BufferedImage recoloredStill = recolourImage(waterStill, light, dark);
                Identifier stillId = Identifier.fromNamespaceAndPath(
                        BCEnergy.MODID, "block/fluid/" + fullName + "_still");
                addRecoloredSprite(output, stillId, recoloredStill,
                        waterStillRes.get());

                // Generate flow texture
                BufferedImage recoloredFlow = recolourImage(waterFlow, light, dark);
                Identifier flowId = Identifier.fromNamespaceAndPath(
                        BCEnergy.MODID, "block/fluid/" + fullName + "_flow");
                addRecoloredSprite(output, flowId, recoloredFlow,
                        waterFlowRes.get());
            }
        }
    }

    /**
     * Add a recolored sprite to the atlas output by wrapping the image bytes
     * in a Resource that reuses the original water texture's metadata.
     */
    private void addRecoloredSprite(Output output, Identifier id,
                                     BufferedImage image, Resource originalResource) {
        // Encode image to PNG bytes
        byte[] pngBytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            pngBytes = baos.toByteArray();
        } catch (IOException e) {
            return;
        }

        // Create a Resource wrapping the recolored PNG, reusing original metadata
        IoSupplier<InputStream> ioSupplier = () -> new ByteArrayInputStream(pngBytes);
        Resource resource = new Resource(originalResource.source(), ioSupplier, originalResource::metadata);
        output.add(id, resource);
    }

    /**
     * Apply the 1.12 AtlasSpriteFluid recoloring to every pixel.
     * Formula: result = (dark * (256 - data) + light * data) / 256
     */
    private BufferedImage recolourImage(BufferedImage source, int lightRgb, int darkRgb) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int lr = (lightRgb >> 16) & 0xFF;
        int lg = (lightRgb >> 8) & 0xFF;
        int lb = lightRgb & 0xFF;
        int dr = (darkRgb >> 16) & 0xFF;
        int dg = (darkRgb >> 8) & 0xFF;
        int db = darkRgb & 0xFF;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = source.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    result.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                int nr = (dr * (256 - r) + lr * r) / 256;
                int ng = (dg * (256 - g) + lg * g) / 256;
                int nb = (db * (256 - b) + lb * b) / 256;

                result.setRGB(x, y, (0xFF << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return result;
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return CODEC;
    }
}
