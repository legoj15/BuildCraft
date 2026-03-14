package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

/** Colour utilities — provides constants and display helpers used across BuildCraft. */
public class ColourUtil {
    /** All 16 dye colours, in ordinal order. Equivalent to DyeColor.values() but cached. */
    public static final DyeColor[] COLOURS = DyeColor.values();

    /** Light (brighter) hex colour for each dye, used for pipe colouring. */
    private static final int[] LIGHT_HEX = {
        0xFF_FF_FF, // WHITE
        0xFF_A5_40, // ORANGE
        0xFF_40_FF, // MAGENTA
        0x40_80_FF, // LIGHT_BLUE
        0xFF_FF_40, // YELLOW
        0x40_FF_40, // LIME
        0xFF_80_A0, // PINK
        0x60_60_60, // GRAY
        0xA0_A0_A0, // LIGHT_GRAY
        0x40_A0_A0, // CYAN
        0xA0_40_FF, // PURPLE
        0x40_40_FF, // BLUE
        0x80_60_40, // BROWN
        0x40_80_40, // GREEN
        0xFF_40_40, // RED
        0x30_30_30, // BLACK
    };

    /** Returns a display-friendly name for the given dye colour (or "Clean" if null). */
    public static String getTextFullTooltip(@Nullable DyeColor colour) {
        if (colour == null) return "Clean";
        String name = colour.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Returns a display-friendly name for the given direction. */
    public static String getTextFullTooltip(Direction direction) {
        String name = direction.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Returns the lighter hex colour for the given dye colour. */
    public static int getLightHex(DyeColor colour) {
        return LIGHT_HEX[colour.ordinal()];
    }

    /** Swaps an ARGB packed int to ABGR (byte-swap R and B). */
    public static int swapArgbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
