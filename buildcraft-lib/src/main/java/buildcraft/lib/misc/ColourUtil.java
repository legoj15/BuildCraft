package buildcraft.lib.misc;

import javax.annotation.Nullable;
import net.minecraft.world.item.DyeColor;

/** Stub for colour utilities — will be fleshed out when client rendering is ported. */
public class ColourUtil {
    /** Returns a display-friendly name for the given dye colour (or "Clean" if null). */
    public static String getTextFullTooltip(@Nullable DyeColor colour) {
        if (colour == null) return "Clean";
        // Capitalize first letter of the enum name
        String name = colour.getName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
