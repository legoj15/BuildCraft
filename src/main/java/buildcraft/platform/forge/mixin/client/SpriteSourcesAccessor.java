/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge && >=1.21.10 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/^*
 * Vanilla's sprite-source type map, for {@code ClientRegistrationSinks#spriteSource}: Forge has no registration event for
 * sprite source types (NeoForge's {@code RegisterSpriteSourcesEvent}) and its access transformer leaves the map private.
 * An accessor has no injection point, so nothing here can drift with Forge's patches.
 *^/
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpriteSource>> buildcraft$idMapper() {
        throw new AssertionError("mixin accessor");
    }
}
*///?}
