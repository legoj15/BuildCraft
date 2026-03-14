package buildcraft.lib.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** Sprite utility stubs. */
public class SpriteUtil {
    private static final Identifier MISSING = Identifier.withDefaultNamespace("missingno");

    public static TextureAtlasSprite missingSprite() {
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        return atlas.getSprite(MISSING);
    }
}
