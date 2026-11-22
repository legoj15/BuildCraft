/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import org.joml.Vector3f;
//? if >=1.21.10 {
import org.joml.Vector4f;
//?}

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.10 {
import net.minecraft.client.renderer.fog.FogData;
//?}
import net.minecraft.resources.Identifier;
//? if >=1.21.10 {
import net.minecraft.world.level.material.FogType;
//?}

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/^*
 * A BuildCraft fluid type's {@link IClientFluidTypeExtensions} from a
 * {@link buildcraft.platform.client.ClientRegistrationSinks#fluidAppearance} call: its still and flowing textures, no
 * tint, and the fog seen from inside it.
 *
 * <p>Differs from the NeoForge twin in shape only: Forge's fog-colour hook returns the colour (a {@code Vector3f}) rather
 * than setting it, and on 26.3 its fog-render hook gets the fog type and colour where NeoForge's gets the fog environment.
 * As on NeoForge 26.x, the 26.3 world model comes from the {@code FluidModel} the sink registers, not from these textures.
 * On 1.21.1 (Forge 52) the textures are what draws the fluid in the world and in its buckets (Forge's fluid renderer and
 * its {@code DynamicFluidContainerModel} read {@link #getStillTexture}), and the fog is set through the render system,
 * as on NeoForge 1.21.1.
 *^/
final class ForgeFluidTypeExtensions implements IClientFluidTypeExtensions {
    private final Identifier stillTexture;
    private final Identifier flowingTexture;
    private final int fogColor;
    private final float fogDistance;

    ForgeFluidTypeExtensions(Identifier stillTexture, Identifier flowingTexture, int fogColor, float fogDistance) {
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
    }

    @Override
    public Identifier getStillTexture() {
        return stillTexture;
    }

    @Override
    public Identifier getFlowingTexture() {
        return flowingTexture;
    }

    @Override
    public int getTintColor() {
        // No tint: the colours are in the sprites.
        return 0xFFFFFFFF;
    }

    @Override
    public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
            float darkenWorldAmount, Vector3f fluidFogColor) {
        return new Vector3f(((fogColor >> 16) & 0xFF) / 255.0F, ((fogColor >> 8) & 0xFF) / 255.0F,
                (fogColor & 0xFF) / 255.0F);
    }

    //? if >=1.21.10 {
    @Override
    public void modifyFogRender(Camera camera, FogType type, float partialTick, FogData data, Vector4f color) {
        data.environmentalStart = 0.0F;
        data.environmentalEnd = fogDistance;
        data.renderDistanceStart = 0.0F;
        data.renderDistanceEnd = fogDistance;
    }
    //?} else {
    /^@Override
    public void modifyFogRender(Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode,
            float renderDistance, float partialTick, float nearDistance, float farDistance,
            com.mojang.blaze3d.shaders.FogShape shape) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0.0F);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(fogDistance);
    }
    ^///?}
}
*///?}
