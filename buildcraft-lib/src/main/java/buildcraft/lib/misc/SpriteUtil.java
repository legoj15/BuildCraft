package buildcraft.lib.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** Sprite utility stubs. */
public class SpriteUtil {
    private static final Identifier MISSING = Identifier.withDefaultNamespace("missingno");

    public static TextureAtlasSprite missingSprite() {
        return getBlockAtlas().getSprite(MISSING);
    }

    /** Resolves a sprite from the block atlas by name (e.g. "buildcrafttransport:pipes/wood_item_clear"). */
    public static TextureAtlasSprite getSprite(String name) {
        Identifier loc = Identifier.parse(name);
        return getBlockAtlas().getSprite(loc);
    }

    /** Resolves a sprite from the block atlas by ResourceLocation. */
    public static TextureAtlasSprite getSprite(Identifier loc) {
        return getBlockAtlas().getSprite(loc);
    }

    private static TextureAtlas getBlockAtlas() {
        return (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
    }
}
