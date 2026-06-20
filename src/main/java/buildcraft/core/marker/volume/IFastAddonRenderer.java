/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import com.mojang.blaze3d.vertex.PoseStack;
//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;*/
//?}
import net.minecraft.world.entity.player.Player;

public interface IFastAddonRenderer<T extends Addon> {
    //? if >=26.1 {
    /**
     * Renders the addon at its corner / fill volume. Vertices are world-space; the implementation
     * uses {@code poseStack.last().pose()} to apply the active world→camera transform pushed by the
     * caller. MC 26.1+ removed immediate-mode rendering, so each implementation queues its geometry
     * onto the supplied {@link SubmitNodeCollector} via
     * {@code collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)} —
     * corner-icon renderers want the block atlas, while solid-coloured overlays like the filler
     * planner's preview want an untextured render type.
     */
    void renderAddonFast(T addon, Player player, float partialTicks, PoseStack poseStack, SubmitNodeCollector collector);

    default IFastAddonRenderer<T> then(IFastAddonRenderer<? super T> after) {
        return (addon, player, partialTicks, poseStack, collector) -> {
            renderAddonFast(addon, player, partialTicks, poseStack, collector);
            after.renderAddonFast(addon, player, partialTicks, poseStack, collector);
        };
    }
    //?} else {
    /*/^*
     * Renders the addon at its corner / fill volume. Vertices are world-space; the implementation
     * uses {@code poseStack.last().pose()} to apply the active world→camera transform pushed by the
     * caller. Each implementation picks its own {@link net.minecraft.client.renderer.rendertype.RenderType}
     * off the supplied {@link MultiBufferSource} — corner-icon renderers want the block atlas, while
     * solid-coloured overlays like the filler planner's preview want an untextured render type.
     *^/
    void renderAddonFast(T addon, Player player, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource);

    default IFastAddonRenderer<T> then(IFastAddonRenderer<? super T> after) {
        return (addon, player, partialTicks, poseStack, bufferSource) -> {
            renderAddonFast(addon, player, partialTicks, poseStack, bufferSource);
            after.renderAddonFast(addon, player, partialTicks, poseStack, bufferSource);
        };
    }*/
    //?}
}
