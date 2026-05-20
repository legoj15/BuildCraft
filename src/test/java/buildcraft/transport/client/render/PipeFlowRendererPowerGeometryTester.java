package buildcraft.transport.client.render;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Regression test for {@link PipeFlowRendererPower#sideFlowBox}. Pins down the
 *  geometry of a power pipe's side flow stem across the full input range,
 *  including the overload case that produced the visible "gaps" between adjacent
 *  pipes when more power flowed through than the pipe's maxPower per tick.
 *
 *  <p>The bug being guarded against: with unclamped inputs,
 *  {@code centreRadius = 0.252 - 0.248 * centrePower} goes negative once
 *  {@code centrePower > ~1.016}, which inverts the side flow box along the side
 *  axis (min &gt; max). The cell-clipped scroll renderer's
 *  {@code if (pMinX >= pMaxX || ...) continue;} guard then skips every cell,
 *  rendering nothing. Visual symptom: the centre cube of each pipe still
 *  renders, but the connecting stems vanish, producing gaps between adjacent
 *  pipes carrying overload power. */
public class PipeFlowRendererPowerGeometryTester {

    private static final double EPS = 1e-9;

    /** Distance from a box's min to max along the given axis. */
    private static double extent(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX - box.minX;
            case Y -> box.maxY - box.minY;
            case Z -> box.maxZ - box.minZ;
        };
    }

    private static void assertNonDegenerate(AABB box, String msg) {
        Assertions.assertTrue(box.maxX > box.minX, msg + " — X degenerate (min=" + box.minX + ", max=" + box.maxX + ")");
        Assertions.assertTrue(box.maxY > box.minY, msg + " — Y degenerate (min=" + box.minY + ", max=" + box.maxY + ")");
        Assertions.assertTrue(box.maxZ > box.minZ, msg + " — Z degenerate (min=" + box.minZ + ", max=" + box.maxZ + ")");
    }

    /** Side-axis extent of the stem at full {@code (power=1, centrePower=1)} should equal
     *  {@code 0.25 + centreRadius_min = 0.254} — the stem reaches from the centre cube
     *  edge (z=0.748 for SOUTH) out to the block face (z=1.0). */
    @Test
    public void full_power_stem_reaches_block_face() {
        for (Direction side : Direction.values()) {
            AABB box = PipeFlowRendererPower.sideFlowBox(side, 1.0, 1.0);
            assertNonDegenerate(box, side + " full power");
            double sideExtent = extent(box, side.getAxis());
            // 0.125 (half-stem) * 2 + centreRadius (0.004 at centrePower=1) = 0.254
            Assertions.assertEquals(0.254, sideExtent, 1e-6,
                side + " stem side-axis extent should reach from centre cube to block face");
        }
    }

    /** The overload regression: power and centrePower above 1 must NOT produce an
     *  inverted box. Before the clamp fix, centreRadius went negative here and the
     *  box collapsed (min &gt; max along the side axis), causing the renderer to
     *  skip every cell and leaving visual gaps between adjacent pipes. */
    @Test
    public void overload_does_not_invert_box() {
        // sqrt(1280/256) = ~2.236 — the displayPower ratio when five creative engines
        // at 256 MJ each push through a 256 MJ/t diamond power pipe.
        double overload = Math.sqrt(5.0);
        for (Direction side : Direction.values()) {
            AABB box = PipeFlowRendererPower.sideFlowBox(side, overload, overload);
            assertNonDegenerate(box, side + " overload (power=" + overload + ")");
        }
    }

    /** Even more extreme overload (a hypothetical machine pushing 100x maxPower) must
     *  still produce a renderable box, not collapse. Saturates to the same geometry
     *  as the {@code power=1} case. */
    @Test
    public void extreme_overload_saturates_to_full() {
        for (Direction side : Direction.values()) {
            AABB extreme = PipeFlowRendererPower.sideFlowBox(side, 100.0, 100.0);
            AABB full    = PipeFlowRendererPower.sideFlowBox(side, 1.0, 1.0);
            assertNonDegenerate(extreme, side + " extreme overload");
            Assertions.assertEquals(full.minX, extreme.minX, EPS, side + " minX");
            Assertions.assertEquals(full.minY, extreme.minY, EPS, side + " minY");
            Assertions.assertEquals(full.minZ, extreme.minZ, EPS, side + " minZ");
            Assertions.assertEquals(full.maxX, extreme.maxX, EPS, side + " maxX");
            Assertions.assertEquals(full.maxY, extreme.maxY, EPS, side + " maxY");
            Assertions.assertEquals(full.maxZ, extreme.maxZ, EPS, side + " maxZ");
        }
    }

    /** Negative inputs (which shouldn't happen in practice but the renderer should
     *  defend against) clamp to 0 — produces a zero-width side-axis box that is
     *  technically degenerate but won't crash the renderer. We don't assert
     *  non-degeneracy here — just that the math doesn't produce NaN/inf/inverted. */
    @Test
    public void negative_inputs_clamp_to_zero() {
        for (Direction side : Direction.values()) {
            AABB box = PipeFlowRendererPower.sideFlowBox(side, -5.0, -5.0);
            Assertions.assertTrue(box.minX <= box.maxX, side + " X inverted on negative input");
            Assertions.assertTrue(box.minY <= box.maxY, side + " Y inverted on negative input");
            Assertions.assertTrue(box.minZ <= box.maxZ, side + " Z inverted on negative input");
        }
    }

    /** For a SOUTH-facing stem, the box must extend to z=1 (the block boundary
     *  where it meets the neighbour pipe's NORTH stem) and NOT past it. Mirror
     *  for the other 5 faces — each stem hugs its own block face. */
    @Test
    public void stem_terminates_at_block_face() {
        record Expect(double min, double max) {}
        for (Direction side : Direction.values()) {
            AABB box = PipeFlowRendererPower.sideFlowBox(side, 1.0, 1.0);
            Expect e = switch (side) {
                case WEST  -> new Expect(box.minX, box.maxX);
                case EAST  -> new Expect(box.minX, box.maxX);
                case DOWN  -> new Expect(box.minY, box.maxY);
                case UP    -> new Expect(box.minY, box.maxY);
                case NORTH -> new Expect(box.minZ, box.maxZ);
                case SOUTH -> new Expect(box.minZ, box.maxZ);
            };
            double expectedFar = switch (side.getAxisDirection()) {
                case POSITIVE -> 1.0;
                case NEGATIVE -> 0.0;
            };
            // The "far" end (the block face) — exactly at 0 or 1.
            double far = side.getAxisDirection().getStep() > 0 ? e.max : e.min;
            Assertions.assertEquals(expectedFar, far, 1e-6,
                side + " stem far end should be at block face " + expectedFar);
        }
    }

    /** Cross-section (perpendicular to the side axis) at full power is a 0.496×0.496
     *  square centred at 0.5 — i.e. the stem fits inside the centre cube's footprint. */
    @Test
    public void cross_section_matches_centre_cube_footprint() {
        for (Direction side : Direction.values()) {
            AABB box = PipeFlowRendererPower.sideFlowBox(side, 1.0, 1.0);
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis == side.getAxis()) continue;
                double e = extent(box, axis);
                // radius * 2 = 0.248 * 2 = 0.496
                Assertions.assertEquals(0.496, e, 1e-6,
                    side + " cross-section axis " + axis + " should be 0.496");
            }
        }
    }
}
