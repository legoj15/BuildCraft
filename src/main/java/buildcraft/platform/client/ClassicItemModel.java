/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.client;

//? if <1.21.10 {
/*/^*
 * A 1.21.1 item model that picks what to draw per display context, in place of the item transforms vanilla would apply
 * (NeoForge's {@code applyTransform}, which this overrides there; Fabric's client mixin on {@code ItemRenderer} calls
 * it where vanilla applies the transforms). Later lines draw items through {@code ItemModel} and have no such hook.
 *
 * <p>This is a contract (see the package documentation of {@code buildcraft.platform}): JDK and vanilla types only.
 *^/
public interface ClassicItemModel extends net.minecraft.client.resources.model.BakedModel {
    /^*
     * The model to draw in {@code context}, after applying to {@code pose} whatever transform it needs. Return a model
     * whose item transforms are empty (the transform baked into its quads, or applied here), or one whose transforms
     * are not applied here: Fabric applies the returned model's transforms afterwards, where NeoForge does not.
     *^/
    net.minecraft.client.resources.model.BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,
            com.mojang.blaze3d.vertex.PoseStack pose, boolean leftHand);
}
*///?}
