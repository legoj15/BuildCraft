/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

import buildcraft.core.marker.volume.Addon;
import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.IFastAddonRenderer;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;

/**
 * Renders all client-side VolumeBoxes as laser box outlines plus their attached addon icons +
 * pattern previews. Laser type switches based on edit/lock state: SIGNAL while a player drags a
 * corner, the locking machine's stripes (STRIPES_WRITE for Filler, STRIPES_READ for Architect)
 * when locked, otherwise CONNECTED.
 */
public class VolumeBoxRenderer {

    public static void renderAll() {
        Player player = Minecraft.getInstance().player;
        PoseStack poseStack = MarkerRenderer.getPoseStack();
        Vec3 cameraPos = MarkerRenderer.getCameraPos();
        if (poseStack == null || cameraPos == null) return;
        //? if >=26.1 {
        SubmitNodeCollector collector = MarkerRenderer.getCollector();
        if (collector == null) return;
        //?}

        // Wireframe boxes.
        for (VolumeBox volumeBox : ClientVolumeBoxes.INSTANCE.volumeBoxes) {
            LaserType type = pickLaserType(volumeBox, player);
            //? if >=26.1 {
            LaserBoxRenderer.renderLaserBoxStatic(
                    poseStack,
                    volumeBox.box,
                    type,
                    false, false,
                    cameraPos, collector);
            //?} else {
            /*LaserBoxRenderer.renderLaserBoxStatic(
                    poseStack,
                    volumeBox.box,
                    type,
                    false, false,
                    cameraPos);*/
            //?}
        }

        if (player == null) return;

        // Addon icons + per-addon overlays. Push a camera-relative translation so the addon
        // renderers can write world-space coords directly. The translation persists onto the
        // captured poseStack until the collector flushes (26.1+), so the deferred submits see it.
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Each addon picks its own RenderType — corner icons go through entityTranslucent on the
        // block atlas; the filler planner's preview goes through an untextured debug-filled type.
        //? if <26.1 {
        /*MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();*/
        //?}
        for (VolumeBox volumeBox : ClientVolumeBoxes.INSTANCE.volumeBoxes) {
            for (Addon addon : volumeBox.addons.values()) {
                if (addon == null) continue;
                @SuppressWarnings("unchecked")
                IFastAddonRenderer<Addon> renderer = (IFastAddonRenderer<Addon>) addon.getRenderer();
                //? if >=26.1 {
                renderer.renderAddonFast(addon, player, 1.0f, poseStack, collector);
                //?} else {
                /*renderer.renderAddonFast(addon, player, 1.0f, poseStack, bufferSource);*/
                //?}
            }
        }

        //? if <26.1 {
        /*bufferSource.endBatch();*/
        //?}
        poseStack.popPose();
    }

    private static LaserType pickLaserType(VolumeBox volumeBox, Player player) {
        if (player != null && volumeBox.isEditingBy(player)) {
            return BuildCraftLaserManager.MARKER_VOLUME_SIGNAL;
        }
        return volumeBox.getLockTargetsStream()
                .filter(Lock.Target.TargetUsedByMachine.class::isInstance)
                .map(Lock.Target.TargetUsedByMachine.class::cast)
                .map(target -> target.type.getLaserType())
                .findFirst()
                .orElse(BuildCraftLaserManager.MARKER_VOLUME_CONNECTED);
    }
}
