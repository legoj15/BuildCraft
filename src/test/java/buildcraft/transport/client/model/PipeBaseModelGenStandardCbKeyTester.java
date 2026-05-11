package buildcraft.transport.client.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.DyeColor;

/** Pure-function checks for the (def, colourblind, dyeColor) cache-key composition that
 *  {@link PipeBaseModelGenStandard}'s SPRITES / MASK_SPRITES / DYED_SPRITES rely on to keep
 *  both colourblind and non-colourblind sprite arrays resolved at the same time. The actual
 *  sprite resolution path goes through {@link buildcraft.lib.misc.SpriteUtil}, which
 *  dereferences {@code Minecraft.getInstance()} — that's null under JUnit and only available
 *  in a real client process — so end-to-end sprite-swap verification stays manual (see the
 *  changelog entry for the runClient walkthrough). What is testable here is that the key
 *  composition keeps the cb-on and cb-off lookups disjoint, which is the load-bearing
 *  invariant for the cache strategy described in {@code PipeBaseModelGenStandard}.
 *
 *  All tests pass {@code null} for the {@code PipeDefinition} argument because
 *  {@link System#identityHashCode}{@code (null)} is well-defined as 0 per the JLS, and
 *  the helpers only call that method on the def — they don't dereference any field.
 *  {@link buildcraft.api.transport.pipe.PipeDefinition} is final and its constructor
 *  requires a fully-populated builder (registered flow types, etc.), which would force
 *  this test to boot half the mod just to exercise an arithmetic helper. */
public class PipeBaseModelGenStandardCbKeyTester {

    @Test
    public void defKey_distinctForCbVsNonCb() {
        Assertions.assertNotEquals(
                PipeBaseModelGenStandard.defKey(null, false),
                PipeBaseModelGenStandard.defKey(null, true),
                "cb-on and cb-off must hash to different keys for the same definition"
        );
    }

    @Test
    public void defKey_stableForSameInputs() {
        Assertions.assertEquals(
                PipeBaseModelGenStandard.defKey(null, true),
                PipeBaseModelGenStandard.defKey(null, true),
                "same inputs must hash to the same key (cache hit on second lookup)"
        );
    }

    @Test
    public void defKey_cbBitInLowPosition() {
        // Documents the bit layout: cb is the low bit so it can't accidentally collide with
        // anything in the identity-hash high half. defKey(null, true) - defKey(null, false) == 1.
        long off = PipeBaseModelGenStandard.defKey(null, false);
        long on = PipeBaseModelGenStandard.defKey(null, true);
        Assertions.assertEquals(1L, on - off,
                "cb bit must be the low bit so it can't collide with the identity hash");
    }

    @Test
    public void dyedKey_distinctForCbVsNonCb() {
        Assertions.assertNotEquals(
                PipeBaseModelGenStandard.dyedKey(null, DyeColor.RED, false),
                PipeBaseModelGenStandard.dyedKey(null, DyeColor.RED, true),
                "cb bit must propagate into the dyed cache key"
        );
    }

    @Test
    public void dyedKey_distinctForDifferentColours() {
        Assertions.assertNotEquals(
                PipeBaseModelGenStandard.dyedKey(null, DyeColor.RED, false),
                PipeBaseModelGenStandard.dyedKey(null, DyeColor.BLUE, false),
                "different dye colours must hash to different keys"
        );
    }

    @Test
    public void dyedKey_allColours_disjoint_perCbState() {
        DyeColor[] colours = DyeColor.values();
        Assertions.assertEquals(16, colours.length, "DyeColor enum must have 16 values");
        // Two passes: cb=false and cb=true. Within each pass all 16 colours must produce
        // unique keys. Across passes, a given colour must also produce different keys.
        for (boolean cb : new boolean[] { false, true }) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            for (DyeColor c : colours) {
                long k = PipeBaseModelGenStandard.dyedKey(null, c, cb);
                Assertions.assertTrue(seen.add(k),
                        "colour " + c + " (cb=" + cb + ") collided with another colour's key");
            }
            Assertions.assertEquals(16, seen.size());
        }
    }

    @Test
    public void dyedKey_cbAndColourBitsAreDisjoint() {
        // Layout: low bit = cb, next 4+ bits = colour ordinal. Verify by checking that a
        // colour's two-cb-states differ by exactly 1 and consecutive colours differ by
        // exactly 2 (the colour-ordinal-shifted-by-one increment).
        long redOff  = PipeBaseModelGenStandard.dyedKey(null, DyeColor.RED, false);
        long redOn   = PipeBaseModelGenStandard.dyedKey(null, DyeColor.RED, true);
        Assertions.assertEquals(1L, redOn - redOff, "cb toggle must change exactly the low bit");

        // ordinals are stable in vanilla; pick two consecutive ones
        DyeColor[] colours = DyeColor.values();
        DyeColor c0 = colours[0];
        DyeColor c1 = colours[1];
        long k0 = PipeBaseModelGenStandard.dyedKey(null, c0, false);
        long k1 = PipeBaseModelGenStandard.dyedKey(null, c1, false);
        Assertions.assertEquals(2L, k1 - k0, "consecutive colour ordinals must differ by 2 (shifted past the cb bit)");
    }
}
