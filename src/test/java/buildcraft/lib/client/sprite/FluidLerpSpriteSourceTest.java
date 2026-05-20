package buildcraft.lib.client.sprite;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link FluidLerpSpriteSource#recolour} — the per-channel intensity lerp
 * that reproduces the historical pre-baked fluid PNGs from the grayscale heat
 * base. The expected values were verified bit-exact against the deleted
 * {@code <fluid>_heat_0_<frame>.png} files (0 error across all 8192 pixels of
 * every fluid).
 */
public class FluidLerpSpriteSourceTest {

    /** First pixel of the heat base texture: grayscale 88, opaque. */
    private static final int HEAT_BASE_88 = 0xFF585858;

    @Test
    public void testRecolourMatchesBakedFluidPixels() {
        // oil: light 0x505050, dark 0x050505 — (88,88,88) recolors to (30,30,30).
        Assertions.assertEquals(
            0xFF1E1E1E, FluidLerpSpriteSource.recolour(HEAT_BASE_88, 0x505050, 0x050505));

        // fuel_dense: light 0xFFAF3F, dark 0xE07F00 — (88,88,88) recolors to (234,143,21).
        Assertions.assertEquals(
            0xFFEA8F15, FluidLerpSpriteSource.recolour(HEAT_BASE_88, 0xFFAF3F, 0xE07F00));
    }

    @Test
    public void testRecolourEndpointsAndAlpha() {
        // A fully-dark base pixel maps exactly onto the fluid's dark endpoint.
        Assertions.assertEquals(
            0xFFE07F00, FluidLerpSpriteSource.recolour(0xFF000000, 0xFFAF3F, 0xE07F00));

        // Input alpha is discarded — output is always opaque even from a transparent base.
        Assertions.assertEquals(
            0xFF050505, FluidLerpSpriteSource.recolour(0x00000000, 0x505050, 0x050505));
    }
}
