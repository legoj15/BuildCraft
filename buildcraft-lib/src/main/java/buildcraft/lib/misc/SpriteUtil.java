package buildcraft.lib.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** Sprite utility stubs. */
public class SpriteUtil {
    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("missingno");

    public static TextureAtlasSprite missingSprite() {
        return getBlockAtlas().getSprite(MISSING);
    }

    /** Resolves a sprite from the block atlas by name (e.g. "buildcrafttransport:pipes/wood_item_clear"). */
    public static TextureAtlasSprite getSprite(String name) {
        ResourceLocation loc = ResourceLocation.parse(name);
        return getBlockAtlas().getSprite(loc);
    }

    /** Resolves a sprite from the block atlas by ResourceLocation. */
    public static TextureAtlasSprite getSprite(ResourceLocation loc) {
        return getBlockAtlas().getSprite(loc);
    }

    private static TextureAtlas getBlockAtlas() {
        return (TextureAtlas) Minecraft.getInstance()
            .getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
    }
}
