/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=1.21.10 {
import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
//?} else {
/^import com.google.common.collect.BiMap;

import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
^///?}
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.util.ExtraCodecs;
//?}

/^*
 * Vanilla's sprite-source type map, for {@code ClientRegistrationSinks#spriteSource} ({@code #spriteSourceType} on
 * 1.21.1): Forge has no registration event for sprite source types (NeoForge's {@code RegisterSpriteSourcesEvent},
 * {@code RegisterSpriteSourceTypesEvent} on 1.21.1) and its access transformer leaves the map private (26.3: the
 * {@code ID_MAPPER}; 1.21.1: the {@code TYPES} map, whose own {@code register} is private too). An accessor has no
 * injection point, so nothing here can drift with Forge's patches.
 *^/
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    //? if >=1.21.10 {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpriteSource>> buildcraft$idMapper() {
        throw new AssertionError("mixin accessor");
    }
    //?} else {
    /^@Accessor("TYPES")
    static BiMap<Identifier, SpriteSourceType> buildcraft$types() {
        throw new AssertionError("mixin accessor");
    }
    ^///?}
}
*///?}
