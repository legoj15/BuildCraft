/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=26.1 {
import net.minecraft.client.renderer.fog.FogData;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/^*
 * The camera-inside fog of a fluid registered through {@code ClientRegistrationSinks#fluidAppearance}, applied by
 * {@code FogRendererMixin} at the end of {@code FogRenderer#setupFog}. Fabric API 26.3 has no fluid fog hook, so this
 * mirrors what NeoForge does for the same call ({@code NeoForgeFluidTypeExtensions#modifyFogColor} and
 * {@code #modifyFogRender}, reached from {@code ClientHooks#getFogColor} / {@code #onSetupFog}): the same gate (camera
 * below the fluid surface by {@link FluidState#getHeight}), the colour overwritten with the fluid's own, and fog closed
 * to the fluid's distance. Sky and cloud ends stay as the chosen environment set them, as on NeoForge.
 *^/
public final class FabricFluidFog {

    private record Fog(float red, float green, float blue, float distance) {}

    /^* Keyed by both the source and the flowing form, as the camera can sit in either. *^/
    private static final Map<Fluid, Fog> FOGS = new ConcurrentHashMap<>();

    private FabricFluidFog() {}

    static void register(Fluid source, Fluid flowing, int fogColor, float fogDistance) {
        Fog fog = new Fog(((fogColor >> 16) & 0xFF) / 255.0F, ((fogColor >> 8) & 0xFF) / 255.0F,
                (fogColor & 0xFF) / 255.0F, fogDistance);
        FOGS.put(source, fog);
        FOGS.put(flowing, fog);
    }

    //? if >=26.1 {
    /^* Overwrites {@code data} with the fog of the registered fluid the camera is inside, if any. *^/
    public static void apply(Camera camera, ClientLevel level, FogData data) {
        Fog fog = fogAt(camera, level);
        if (fog == null) {
            return;
        }
        data.color.set(fog.red(), fog.green(), fog.blue(), 1.0F);
        data.environmentalStart = 0.0F;
        data.environmentalEnd = fog.distance();
        data.renderDistanceStart = 0.0F;
        data.renderDistanceEnd = fog.distance();
    }
    //?} else {
    /^// Before 26.1 the fog colour is not part of FogData: FogRenderer#setupFog hands its colour vector and distances
    // straight to the fog buffer. This sets the colour vector in place to the fluid's own and answers the fog distance
    // (both the environmental and render-distance ends, starts 0), or -1 when the camera is in no registered fluid.
    public static float apply(Camera camera, ClientLevel level, org.joml.Vector4f colour) {
        Fog fog = fogAt(camera, level);
        if (fog == null) {
            return -1.0F;
        }
        colour.set(fog.red(), fog.green(), fog.blue(), 1.0F);
        return fog.distance();
    }
    ^///?}

    private static Fog fogAt(Camera camera, ClientLevel level) {
        if (FOGS.isEmpty()) {
            return null;
        }
        BlockPos pos = BlockPos.containing(camera.position());
        FluidState state = level.getFluidState(pos);
        Fog fog = FOGS.get(state.getType());
        if (fog == null || camera.position().y >= pos.getY() + state.getHeight(level, pos)) {
            return null;
        }
        return fog;
    }
}
*///?}
