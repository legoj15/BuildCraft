/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.ClientHooks;

/^* {@link ClientHooks} on Fabric: Fabric API's client events, one callback per call, in call order. *^/
public final class FabricClientHooks implements ClientHooks {

    @Override
    public void onLevelRender(Consumer<BCLevelRenderContext> renderer) {
        // COLLECT_SUBMITS is fabric-rendering-v1's point for queueing custom geometry on the frame's collector,
        // NeoForge's SubmitCustomGeometryEvent.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> renderer.accept(new LevelContext(context)));
    }

    private record LevelContext(LevelRenderContext context) implements BCLevelRenderContext {
        @Override
        public PoseStack poseStack() {
            return context.poseStack();
        }

        @Override
        public Vec3 cameraPos() {
            return context.levelState().cameraRenderState.pos;
        }

        @Override
        public float partialTick() {
            return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        }

        @Override
        public SubmitNodeCollector collector() {
            return context.submitNodeCollector();
        }
    }
}
*///?}
