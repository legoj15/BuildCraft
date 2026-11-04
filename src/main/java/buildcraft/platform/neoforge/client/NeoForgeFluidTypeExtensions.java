/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import net.minecraft.resources.Identifier;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * A fluid type's {@link IClientFluidTypeExtensions} from a
 * {@link buildcraft.platform.client.ClientRegistrationSinks#fluidAppearance} call: its still and flowing textures, no
 * tint, and the camera-inside fog.
 *
 * <p>Below 26.1 world fluids take their textures from the {@code getStillTexture}/{@code getFlowingTexture}/
 * {@code getTintColor} extension methods; 26.1 dropped those hooks in favour of the {@code FluidModel} the sink
 * registers, so on 26.x the three methods are inert. They carry no {@code @Override} so the one class compiles on
 * every node.
 */
final class NeoForgeFluidTypeExtensions implements IClientFluidTypeExtensions {
    private final Identifier stillTexture;
    private final Identifier flowingTexture;
    private final int fogColor;
    private final float fogDistance;

    NeoForgeFluidTypeExtensions(Identifier stillTexture, Identifier flowingTexture, int fogColor, float fogDistance) {
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.fogColor = fogColor;
        this.fogDistance = fogDistance;
    }

    public Identifier getStillTexture() {
        return stillTexture;
    }

    public Identifier getFlowingTexture() {
        return flowingTexture;
    }

    public int getTintColor() {
        // No tint — colours are baked into the sprites
        return 0xFFFFFFFF;
    }

    @Override
    //? if >=26.1 {
    public void modifyFogColor(net.minecraft.client.Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount, org.joml.Vector4f fluidFogColor) {
    //?} elif >=1.21.10 {
    /*public org.joml.Vector4f modifyFogColor(net.minecraft.client.Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount, org.joml.Vector4f fluidFogColor) {*/
    //?} else {
    /*public org.joml.Vector3f modifyFogColor(net.minecraft.client.Camera camera, float partialTick, net.minecraft.client.multiplayer.ClientLevel level, int renderDistance, float darkenWorldAmount, org.joml.Vector3f fluidFogColor) {*/
    //?}
        float r = ((fogColor >> 16) & 0xFF) / 255.0F;
        float g = ((fogColor >> 8) & 0xFF) / 255.0F;
        float b = (fogColor & 0xFF) / 255.0F;
        //? if >=26.1 {
        fluidFogColor.set(r, g, b, 1.0F);
        //?} elif >=1.21.10 {
        /*return fluidFogColor.set(r, g, b, 1.0F);*/
        //?} else {
        /*return fluidFogColor.set(r, g, b);*/
        //?}
    }

    @Override
    //? if >=1.21.10 {
    public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.fog.environment.FogEnvironment environment, float renderDistance, float partialTick, net.minecraft.client.renderer.fog.FogData fogData) {
        fogData.environmentalStart = 0.0F;
        fogData.environmentalEnd = fogDistance;
        fogData.renderDistanceStart = 0.0F;
        fogData.renderDistanceEnd = fogDistance;
    }
    //?} else {
    /*public void modifyFogRender(net.minecraft.client.Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, com.mojang.blaze3d.shaders.FogShape shape) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0.0F);
        com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(fogDistance);
    }*/
    //?}
}
//?}
