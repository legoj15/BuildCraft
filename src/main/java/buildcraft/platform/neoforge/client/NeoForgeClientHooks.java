/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;

import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.ClientHooks;

/**
 * {@link ClientHooks} on NeoForge: each call adds one listener to the game bus ({@link NeoForge#EVENT_BUS}) at the
 * call, so listeners keep the order (and the default priority) of the hand-written ones they replace.
 */
public final class NeoForgeClientHooks implements ClientHooks {

    @Override
    public void onLevelRender(Consumer<BCLevelRenderContext> renderer) {
        //? if >=26.1 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class,
                event -> renderer.accept(new LevelContext(event.getPoseStack(),
                        event.getLevelRenderState().cameraRenderState.pos, event.getSubmitNodeCollector())));
        //?} elif >=1.21.10 {
        /*NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks.class,
                event -> renderer.accept(new LevelContext(event.getPoseStack(),
                        event.getLevelRenderState().cameraRenderState.pos,
                        Minecraft.getInstance().renderBuffers().bufferSource())));*/
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, event -> {
            if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            renderer.accept(new LevelContext(event.getPoseStack(), event.getCamera().getPosition(),
                    Minecraft.getInstance().renderBuffers().bufferSource()));
        });*/
        //?}
    }

    //? if >=26.1 {
    private record LevelContext(PoseStack poseStack, Vec3 cameraPos,
            net.minecraft.client.renderer.SubmitNodeCollector collector) implements BCLevelRenderContext {
    //?} else {
    /*private record LevelContext(PoseStack poseStack, Vec3 cameraPos,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource) implements BCLevelRenderContext {*/
    //?}
        @Override
        public float partialTick() {
            return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        }
    }
}
//?}
