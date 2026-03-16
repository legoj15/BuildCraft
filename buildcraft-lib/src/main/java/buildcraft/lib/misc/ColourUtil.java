package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
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

    /** Maps DyeColor ordinals to ChatFormatting colours for display, matching 1.12.2. */
    private static final ChatFormatting[] COLOUR_TO_FORMAT = new ChatFormatting[16];

    static {
        COLOUR_TO_FORMAT[DyeColor.WHITE.ordinal()] = ChatFormatting.WHITE;
        COLOUR_TO_FORMAT[DyeColor.ORANGE.ordinal()] = ChatFormatting.GOLD;
        COLOUR_TO_FORMAT[DyeColor.MAGENTA.ordinal()] = ChatFormatting.LIGHT_PURPLE;
        COLOUR_TO_FORMAT[DyeColor.LIGHT_BLUE.ordinal()] = ChatFormatting.AQUA;
        COLOUR_TO_FORMAT[DyeColor.YELLOW.ordinal()] = ChatFormatting.YELLOW;
        COLOUR_TO_FORMAT[DyeColor.LIME.ordinal()] = ChatFormatting.GREEN;
        COLOUR_TO_FORMAT[DyeColor.PINK.ordinal()] = ChatFormatting.LIGHT_PURPLE;
        COLOUR_TO_FORMAT[DyeColor.GRAY.ordinal()] = ChatFormatting.DARK_GRAY;
        COLOUR_TO_FORMAT[DyeColor.LIGHT_GRAY.ordinal()] = ChatFormatting.GRAY;
        COLOUR_TO_FORMAT[DyeColor.CYAN.ordinal()] = ChatFormatting.DARK_AQUA;
        COLOUR_TO_FORMAT[DyeColor.PURPLE.ordinal()] = ChatFormatting.DARK_PURPLE;
        COLOUR_TO_FORMAT[DyeColor.BLUE.ordinal()] = ChatFormatting.BLUE;
        COLOUR_TO_FORMAT[DyeColor.BROWN.ordinal()] = ChatFormatting.GOLD;
        COLOUR_TO_FORMAT[DyeColor.GREEN.ordinal()] = ChatFormatting.DARK_GREEN;
        COLOUR_TO_FORMAT[DyeColor.RED.ordinal()] = ChatFormatting.DARK_RED;
        COLOUR_TO_FORMAT[DyeColor.BLACK.ordinal()] = ChatFormatting.DARK_GRAY;
    }

    /** Returns a display-friendly name for the given dye colour (or "Clean" if null). */
    public static String getTextFullTooltip(@Nullable DyeColor colour) {
        if (colour == null) return "Clean";
        String name = colour.getName();
        // Title-case each word (e.g. "light_blue" -> "Light Blue")
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    /** Returns a display-friendly name for the given direction. */
    public static String getTextFullTooltip(Direction direction) {
        String name = direction.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Converts a {@link DyeColor} into an equivalent {@link ChatFormatting} for display.
     *  Black maps to DARK_GRAY so it remains visible on dark backgrounds. */
    public static ChatFormatting convertColourToTextFormat(DyeColor colour) {
        return COLOUR_TO_FORMAT[colour.ordinal()];
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
