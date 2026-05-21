package buildcraft.lib.client.guide.parts;

import net.minecraft.resources.Identifier;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.render.ISprite;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.sprite.SpriteRaw;

/** Factory for creating {@link GuideImage} instances. Resolves a user-facing texture
 *  reference (e.g. {@code buildcraftunofficial:item/wrench}) to its real texture path;
 *  source dimensions default to 16x16. */
public class GuideImageFactory implements GuidePartFactory {
    private final ISprite sprite;
    private final int srcWidth, srcHeight;
    private final int width, height;

    public GuideImageFactory(String location) {
        this(location, -1, -1);
    }

    public GuideImageFactory(String location, int width, int height) {
        // 1.12 went through TextureAtlas/TextureAtlasSprite which knew the
        // `assets/<ns>/textures/<path>.png` mapping. In 1.21 GUI rendering
        // blits straight from a resource Identifier, so we need to resolve
        // the user-facing form (`buildcraftunofficial:item/wrench`) to the
        // actual texture path (`buildcraftunofficial:textures/item/wrench.png`)
        // ourselves. Defensive: skip the rewrite if the caller already has
        // the modern form (path starts with `textures/` or already ends in `.png`).
        ISprite s;
        int sw = 16, sh = 16;
        try {
            Identifier resLoc = resolveTexturePath(location);
            s = new SpriteRaw(resLoc, 0, 0, 1, 1);
        } catch (Exception e) {
            BCLog.logger.warn("[lib.guide.loader.image] Couldn't load image '" + location + "': " + e.getMessage());
            s = new SpriteRaw(Identifier.parse("buildcraftunofficial:missing"), 0, 0, 1, 1);
        }
        this.sprite = s;
        this.srcWidth = sw;
        this.srcHeight = sh;
        this.width = width <= 0 ? srcWidth : width;
        this.height = height <= 0 ? srcHeight : height;
    }

    private static Identifier resolveTexturePath(String location) {
        Identifier parsed = Identifier.parse(location);
        String path = parsed.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return Identifier.fromNamespaceAndPath(parsed.getNamespace(), path);
    }

    @Override
    public GuideImage createNew(GuiGuide gui) {
        return new GuideImage(gui, sprite, srcWidth, srcHeight, width, height);
    }
}
