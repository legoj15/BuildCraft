package buildcraft.lib.client.sprite;

import buildcraft.api.core.render.ISprite;

public class SpriteHolderRegistry {
    public void registerInitialSprites() {}

    /** Stub holder that will eventually wrap a TextureAtlasSprite lookup. */
    public static class SpriteHolder implements ISprite {
        private final String location;

        public SpriteHolder(String location) {
            this.location = location;
        }

        public String getLocation() {
            return location;
        }

        @Override
        public void bindTexture() { /* stub */ }

        @Override
        public double getInterpU(double u) { return u; }

        @Override
        public double getInterpV(double v) { return v; }
    }

    public static SpriteHolder getHolder(String location) {
        return new SpriteHolder(location);
    }
}
