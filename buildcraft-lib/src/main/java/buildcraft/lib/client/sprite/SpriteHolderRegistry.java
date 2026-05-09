package buildcraft.lib.client.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import buildcraft.api.core.render.ISprite;

public class SpriteHolderRegistry {
    public void registerInitialSprites() {}

    /**
     * Wraps a texture location and provides UV lookups against the block texture atlas.
     * The sprite is lazily resolved from the atlas on first UV query.
     */
    public static class SpriteHolder implements ISprite {
        private final String location;
        private final ResourceLocation resourceLocation;
        private TextureAtlasSprite cachedSprite;

        public SpriteHolder(String location) {
            this.location = location;
            // location is in the format "modid:path"
            this.resourceLocation = ResourceLocation.parse(location);
        }

        public String getLocation() {
            return location;
        }

        public ResourceLocation getResourceLocation() {
            return resourceLocation;
        }

        public TextureAtlasSprite getSprite() {
            if (cachedSprite == null) {
                try {
                    TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
                            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
                    cachedSprite = atlas.getSprite(resourceLocation);
                } catch (Exception e) {
                    // During early init or server side, atlas may not be available
                    return null;
                }
            }
            return cachedSprite;
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
