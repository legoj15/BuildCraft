package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

/** Colour utilities — provides constants and display helpers used across BuildCraft. */
public class ColourUtil {
    /** All 16 dye colours, in ordinal order. Equivalent to DyeColor.values() but cached. */
    public static final DyeColor[] COLOURS = DyeColor.values();

    /** Maps Direction ordinals to ChatFormatting colours for display. matching 1.12.2 FACE_TO_FORMAT */
    private static final ChatFormatting[] FACE_TO_FORMAT = new ChatFormatting[6];

    static {
        FACE_TO_FORMAT[Direction.UP.ordinal()] = ChatFormatting.WHITE;
        FACE_TO_FORMAT[Direction.DOWN.ordinal()] = ChatFormatting.DARK_GRAY; // 1.12.2 mapped BLACK to DARK_GRAY for visibility
        FACE_TO_FORMAT[Direction.NORTH.ordinal()] = ChatFormatting.RED;
        FACE_TO_FORMAT[Direction.SOUTH.ordinal()] = ChatFormatting.BLUE;
        FACE_TO_FORMAT[Direction.EAST.ordinal()] = ChatFormatting.YELLOW;
        FACE_TO_FORMAT[Direction.WEST.ordinal()] = ChatFormatting.GREEN;
    }

    /** Light (brighter) hex colour for each dye, used for pipe colouring.
     *  Values from 1.12.2, remapped to MC 26.1 DyeColor ordinal order. */
    private static final int[] LIGHT_HEX = {
        0xe4_e4_e4, // WHITE
        0xEA_78_35, // ORANGE
        0xD9_43_C6, // MAGENTA
        0x66_AA_FF, // LIGHT_BLUE
        0xFF_D9_1C, // YELLOW
        0x39_D5_2E, // LIME
        0xD9_71_99, // PINK
        0x7A_7A_7A, // GRAY
        0xa0_a7_a7, // LIGHT_GRAY
        0x29_97_99, // CYAN
        0x7e_34_bf, // PURPLE
        0x25_31_93, // BLUE
        0x89_50_2D, // BROWN
        0x00_7F_0E, // GREEN
        0xBE_2B_27, // RED
        0x18_14_14, // BLACK
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

    /** Maps Direction ordinals to ARGB colours for gate ledger face indicators.
     *  Ported from 1.12.2 — only DOWN and UP had explicit colours. */
    private static final int[] FACE_TO_COLOUR = new int[6];

    static {
        FACE_TO_COLOUR[Direction.DOWN.ordinal()] = 0xFF_33_33_33;
        FACE_TO_COLOUR[Direction.UP.ordinal()] = 0xFF_CC_CC_CC;
    }

    /** Returns the ledger-background colour associated with the given block face direction. */
    public static int getColourForSide(Direction face) {
        return FACE_TO_COLOUR[face.ordinal()];
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
        String localized = buildcraft.lib.misc.LocaleUtil.localize("direction." + direction.getName());
        ChatFormatting format = FACE_TO_FORMAT[direction.ordinal()];
        return format.toString() + localized + ChatFormatting.RESET;
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
