package buildcraft.lib.client.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.render.ISprite;

// TextureAtlas.LOCATION_BLOCKS / LOCATION_ITEMS are @Deprecated in 26.1 but vanilla has not
// shipped a non-deprecated replacement Identifier — Sheets.java only exposes GUI_SHEET as a
// public Identifier; the block/item-atlas SpriteMappers there wrap the same deprecated
// constants. Suppress here; revisit once NeoForge surfaces a stable replacement.
@SuppressWarnings("deprecation")
public class SpriteHolderRegistry {
    public void registerInitialSprites() {}

    /** Initialization-on-demand holder for the atlas lookup order. Kept out of
     *  {@code SpriteHolderRegistry}'s own static init so the class is safe to <em>load</em> on a
     *  dedicated server: the {@link TextureAtlas}/{@link Sheets} constants below are
     *  {@code net.minecraft.client.*} classes that don't exist server-side, yet the registry is
     *  reached from server code — e.g. {@code BCBuildersEventDist}'s laser-type static
     *  initialiser and the quarry/filler/builder/architect {@code validate}/{@code invalidate}
     *  tile hooks. Touching the constants at class-init crashed dedicated servers with
     *  {@code NoClassDefFoundError: …/TextureAtlas}. This holder defers that resolution to first
     *  use; its sole consumer, {@link SpriteHolder#resolveSprite()}, is a client-only render path.
     *
     *  <p>Lookup order: blocks atlas first since the bulk of mod statement icons live in
     *  directories declared under {@code assets/minecraft/atlases/blocks.json} ({@code triggers/},
     *  {@code pipes/}, {@code wires/}, …). Items atlas second so that sprites also referenced by
     *  item models — e.g. paintbrush colour textures at {@code item/paintbrush/<colour>} — resolve
     *  without being declared a second time on the blocks atlas. GUI atlas last for the handful of
     *  sprites that live there. The fall-back lets each PNG live on exactly one atlas page (no GPU
     *  duplication) while still being reachable from {@link SpriteHolder}. */
    private static final class AtlasLookup {
        static final Identifier[] ORDER = {
            TextureAtlas.LOCATION_BLOCKS,
            TextureAtlas.LOCATION_ITEMS,
            Sheets.GUI_SHEET,
        };
    }

    /**
     * Wraps a texture location and provides UV lookups against the texture atlases.
     * The sprite is lazily resolved on first UV query and cached.
     */
    public static class SpriteHolder implements ISprite {
        private final String location;
        private final Identifier resourceLocation;
        private TextureAtlasSprite cachedSprite;

        public SpriteHolder(String location) {
            this.location = location;
            // location is in the format "modid:path"
            this.resourceLocation = Identifier.parse(location);
        }

        public String getLocation() {
            return location;
        }

        public Identifier getResourceLocation() {
            return resourceLocation;
        }

        public TextureAtlasSprite getSprite() {
            if (cachedSprite == null) {
                try {
                    cachedSprite = resolveSprite();
                } catch (Exception e) {
                    // During early init or server side, atlas may not be available
                    return null;
                }
            }
            return cachedSprite;
        }

        /** The atlas page this sprite was stitched onto, resolved the same way as
         *  {@link #getSprite()}. Bind this — not a hard-coded atlas constant — when building
         *  a {@code RenderType}, so the bound page always matches the page whose UV space
         *  {@link #getInterpU}/{@link #getInterpV} map into. Falls back to the blocks atlas
         *  when the sprite can't be resolved yet (early init / dedicated server). */
        public Identifier getAtlasLocation() {
            TextureAtlasSprite sprite = getSprite();
            return sprite != null ? sprite.atlasLocation() : TextureAtlas.LOCATION_BLOCKS;
        }

        /** Walk {@link AtlasLookup#ORDER} and return the first atlas that has this
         *  sprite as a real entry (i.e. {@code getSprite()} did not fall through to
         *  the missing-texture sprite). If every atlas misses, return the missing
         *  sprite from the first atlas we successfully queried so callers still get
         *  a non-null {@link TextureAtlasSprite} to render. */
        private TextureAtlasSprite resolveSprite() {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            Identifier missingId = MissingTextureAtlasSprite.getLocation();
            TextureAtlasSprite firstMissing = null;
            for (Identifier atlasId : AtlasLookup.ORDER) {
                if (!(tm.getTexture(atlasId) instanceof TextureAtlas atlas)) continue;
                TextureAtlasSprite sprite = atlas.getSprite(resourceLocation);
                if (!sprite.contents().name().equals(missingId)) {
                    return sprite;
                }
                if (firstMissing == null) firstMissing = sprite;
            }
            return firstMissing;
        }

        /** Clear the cached sprite (call on resource reload). */
        public void invalidate() {
            cachedSprite = null;
        }

        @Override
        public void bindTexture() { /* No-op in modern MC — texture binding is done by RenderType */ }

        @Override
        public double getInterpU(double u) {
            TextureAtlasSprite sprite = getSprite();
            if (sprite == null) return (float) u;
            return sprite.getU((float) u);
        }

        @Override
        public double getInterpV(double v) {
            TextureAtlasSprite sprite = getSprite();
            if (sprite == null) return (float) v;
            return sprite.getV((float) v);
        }
    }

    public static SpriteHolder getHolder(String location) {
        return new SpriteHolder(location);
    }
}
