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
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.client.render.MarkerRenderer;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

import buildcraft.core.marker.volume.ClientVolumeBoxes;
import buildcraft.core.marker.volume.Lock;
import buildcraft.core.marker.volume.VolumeBox;

/**
 * Renders all client-side VolumeBoxes as laser box outlines. Laser type switches based on edit/lock
 * state: SIGNAL while a player drags a corner, the locking machine's stripes (STRIPES_WRITE for
 * Filler, STRIPES_READ for Architect) when locked, otherwise CONNECTED.
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
