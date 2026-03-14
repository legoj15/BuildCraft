package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

/** Colour utilities — provides constants and display helpers used across BuildCraft. */
public class ColourUtil {
    /** All 16 dye colours, in ordinal order. Equivalent to DyeColor.values() but cached. */
    public static final DyeColor[] COLOURS = DyeColor.values();

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
}

