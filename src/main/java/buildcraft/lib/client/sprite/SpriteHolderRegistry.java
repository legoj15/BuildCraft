package buildcraft.lib.client.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.render.ISprite;

public class SpriteHolderRegistry {
    public void registerInitialSprites() {}

    /** Atlas lookup order. Blocks atlas first since the bulk of mod statement icons
     *  live in directories declared under {@code assets/minecraft/atlases/blocks.json}
     *  ({@code triggers/}, {@code pipes/}, {@code wires/}, …). Items atlas second so
     *  that sprites which are also referenced by item models — e.g. paintbrush colour
     *  textures at {@code item/paintbrush/<colour>} — resolve without having to be
     *  declared a second time on the blocks atlas. GUI atlas last for the handful of
     *  sprites that live there. The fall-back lets each PNG live on exactly one atlas
     *  page (no GPU duplication) while still being reachable from {@link SpriteHolder}. */
    private static final Identifier[] ATLAS_LOOKUP_ORDER = {
        TextureAtlas.LOCATION_BLOCKS,
        TextureAtlas.LOCATION_ITEMS,
        Sheets.GUI_SHEET,
    };

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

        /** Walk {@link #ATLAS_LOOKUP_ORDER} and return the first atlas that has this
         *  sprite as a real entry (i.e. {@code getSprite()} did not fall through to
         *  the missing-texture sprite). If every atlas misses, return the missing
         *  sprite from the first atlas we successfully queried so callers still get
         *  a non-null {@link TextureAtlasSprite} to render. */
        private TextureAtlasSprite resolveSprite() {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            Identifier missingId = MissingTextureAtlasSprite.getLocation();
            TextureAtlasSprite firstMissing = null;
            for (Identifier atlasId : ATLAS_LOOKUP_ORDER) {
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
