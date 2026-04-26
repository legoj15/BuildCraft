/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.marker.volume;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.player.Player;

public interface IFastAddonRenderer<T extends Addon> {
    /**
     * Renders the addon at its corner / fill volume. Vertices are world-space; the implementation
     * uses {@code poseStack.last().pose()} to apply the active world→camera transform pushed by the
     * caller.
     */
    void renderAddonFast(T addon, Player player, float partialTicks, PoseStack poseStack, VertexConsumer bb);

    default IFastAddonRenderer<T> then(IFastAddonRenderer<? super T> after) {
        return (addon, player, partialTicks, poseStack, bb) -> {
            renderAddonFast(addon, player, partialTicks, poseStack, bb);
            after.renderAddonFast(addon, player, partialTicks, poseStack, bb);
        };
    }
}
