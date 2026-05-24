package buildcraft.core;

import net.minecraft.resources.Identifier;

/**
 * Constants for the multi-criterion {@code buildcraftunofficial:paper} advancement.
 * Criterion names mirror the keys in {@code data/buildcraftunofficial/advancement/paper.json};
 * a mismatch silently prevents the advancement from progressing, so the strings are
 * centralised here as the single source of truth.
 */
public final class PaperAdvancement {
    public static final Identifier ID = Identifier.parse("buildcraftunofficial:paper");

    public static final String WRITE_TO_LIST = "write_to_list";
    public static final String WRITE_TO_BLUEPRINT = "write_to_blueprint";
    public static final String WRITE_TO_TEMPLATE = "write_to_template";
    public static final String CAPTURE_WITH_SCHEMATIC = "capture_with_schematic";

    private PaperAdvancement() {}
}
