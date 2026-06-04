/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.builders.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only renderer for the 1.21.11 builder/filler place-task throwing animation: each flying
 * place-task item is drawn as a small (~0.30-block) cube textured with the item's particle sprite.
 *
 * <p>This deliberately lives in its OWN class rather than in {@code buildcraft.builders.BCBuildersEventDist}.
 * BCBuildersEventDist is loaded on the dedicated server (its per-server-tick "destroying the world"
 * advancement scan). The {@code updateForTopItem(…, mc.level, …)} call below passes a {@code ClientLevel}
 * into a {@code Level} parameter, which makes the bytecode verifier load
 * {@code net.minecraft.client.multiplayer.ClientLevel} when the enclosing class is linked — and the
 * dedicated server's {@code NeoForgeDevDistCleaner} rejects that client-only class, hard-crashing the
 * server on its first tick. Keeping it here means the class is only ever loaded on the client, when the
 * render path actually runs — exactly as the 26.1 path keeps the equivalent call in {@code ItemRenderUtil}.
 *
 * <p>The body is 1.21.11-only: the 26.1 builder animation uses the {@code SubmitNodeCollector} path and
 * never calls this. (The particle accessor also diverges — 1.21.11 {@code pickParticleIcon():TextureAtlasSprite}
 * vs 26.1 {@code pickParticleMaterial():Material.Baked} — so even the signature-shared body must be gated.)
 */
public final class PlaceTaskCubeRenderer {
    private PlaceTaskCubeRenderer() {}

    /** Draw one place-task item as a ~0.30-block sprite cube at {@code pos} (camera-relative). */
    public static void renderItemCube(ItemStack item, Vec3 pos, Vec3 cameraPos, PoseStack poseStack) {
        //? if <26.1 {
        /*if (item.isEmpty()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int light = buildcraft.lib.client.render.laser.LaserRenderer_BC8.computeLightmap(pos.x, pos.y, pos.z, 0);
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);

        if (item.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
            // Render the actual block model so each face gets its correct texture — a particle-sprite
            // cube shows the block's side texture on every face (wrong top on grass/logs/etc). Centre
            // the 0..1 model on the origin and scale to ~0.30 block.
            poseStack.scale(0.30F, 0.30F, 0.30F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            mc.getBlockRenderer().renderSingleBlock(blockItem.getBlock().defaultBlockState(), poseStack,
                bufferSource, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } else {
            // Non-block items: small cube textured with the item's particle sprite.
            net.minecraft.client.renderer.item.ItemStackRenderState rs =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(rs, item,
                net.minecraft.world.item.ItemDisplayContext.FIXED, mc.level, null, 0);
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                rs.isEmpty() ? null : rs.pickParticleIcon(net.minecraft.util.RandomSource.create());
            if (sprite != null) {
                com.mojang.blaze3d.vertex.VertexConsumer buffer = bufferSource.getBuffer(
                    net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(sprite.atlasLocation()));
                float r = 0.15F;
                buildcraft.lib.client.model.ModelUtil.UvFaceData uv =
                    new buildcraft.lib.client.model.ModelUtil.UvFaceData(
                        sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
                for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
                    buildcraft.lib.client.model.ModelUtil.createFace(
                        face, new org.joml.Vector3f(0F, 0F, 0F), new org.joml.Vector3f(r, r, r), uv)
                        .lighti(light)
                        .colouri(255, 255, 255, 255)
                        .render(poseStack.last(), buffer);
                }
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();*/
        //?}
    }
}
