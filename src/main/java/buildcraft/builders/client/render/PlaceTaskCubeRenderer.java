/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only renderer for the builder/filler place-task throwing animation on every line below 26.1: each flying
 * place-task item is drawn as a small (~0.30-block) cube at the task's interpolated position.
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
 * <p>The body is live on 1.21.1, 1.21.10 and 1.21.11 alike; 26.1 renders the place-task cube through the
 * {@code SubmitNodeCollector} path in {@code ItemRenderUtil} and never calls this. (The particle accessor also
 * diverges — 1.21.10/1.21.11 {@code pickParticleIcon():TextureAtlasSprite} vs 26.1 {@code pickParticleMaterial():
 * Material.Baked} — so even the signature-shared body must be gated.) A block whose block model is empty (BC's
 * pipe holder and engines: particle-only placeholders, the body being a block-entity renderer's) must not go
 * through {@code renderSingleBlock} — it would draw nothing — so such items take the item path instead: the
 * classic item renderer on 1.21.1, and on 1.21.10/1.21.11 a particle-sprite cube tinted with the paint the
 * client sync copies onto the flying stack.
 */
public final class PlaceTaskCubeRenderer {
    private PlaceTaskCubeRenderer() {}

    /** Draw one place-task item as a ~0.30-block cube at {@code pos} (camera-relative). */
    public static void renderItemCube(ItemStack item, Vec3 pos, Vec3 cameraPos, PoseStack poseStack) {
        // 26.1 renders the place-task cube via the SubmitNodeCollector path in ItemRenderUtil;
        // this method is a no-op stub on that node.
        //? if >=26.1 {
        //?} else {
        /*if (item.isEmpty()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        int light = buildcraft.lib.client.render.laser.LaserRenderer_BC8.computeLightmap(pos.x, pos.y, pos.z, 0);
        net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);

        if (item.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
            && !isBlockModelEmpty(blockItem.getBlock())) {
            // Render the actual block model so each face gets its correct texture — a particle-sprite
            // cube shows the block's side texture on every face (wrong top on grass/logs/etc). Centre
            // the 0..1 model on the origin and scale to ~0.30 block.
            poseStack.scale(0.30F, 0.30F, 0.30F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            buildcraft.platform.client.ClientPlatform.extensions().renderSingleBlock(
                blockItem.getBlock().defaultBlockState(), poseStack, bufferSource, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } else {
            //? if >=1.21.10 {
            // A block with an empty block model (a pipe, an engine — the body is a block-entity renderer's) or a
            // non-block item: no classic item renderer remains on this line, and a resolved ItemStackRenderState
            // can only be drawn via submit(..., SubmitNodeCollector, ...), which this render stage has no collector
            // for (see the note in BCBuildersEventDist). So a particle-sprite cube — tinted with the pipe's paint
            // when the flying stack carries one (the display copy the client sync writes).
            net.minecraft.client.renderer.item.ItemStackRenderState rs =
                new net.minecraft.client.renderer.item.ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(rs, item,
                net.minecraft.world.item.ItemDisplayContext.FIXED, mc.level, null, 0);
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                rs.isEmpty() ? null : rs.pickParticleIcon(net.minecraft.util.RandomSource.create());
            if (sprite != null) {
                net.minecraft.world.item.DyeColor paint =
                    item.get(buildcraft.transport.BCTransportItems.PIPE_COLOUR.get());
                renderSpriteCube(poseStack, bufferSource, sprite, light, paint == null ? 0xFFFFFFFF
                    : 0xFF000000 | buildcraft.lib.misc.ColourUtil.getLightHex(paint));
            }
            //?} else {
            /^if (item.getItem() instanceof net.minecraft.world.item.BlockItem) {
                // A block with an empty block model (a pipe, an engine — the body is a block-entity renderer's):
                // draw the item model itself, at the same ~0.30-block size (FIXED applies ~0.5x, hence 0.60). The
                // classic item renderer resolves a painted pipe's overlay model and applies the item tint, so the
                // cube shows the pipe's paint.
                poseStack.scale(0.60F, 0.60F, 0.60F);
                mc.getItemRenderer().renderStatic(item, net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                    mc.level, 0);
            } else {
                // Non-block items: small cube textured with the item's particle sprite.
                net.minecraft.client.resources.model.BakedModel bakedModel =
                    mc.getItemRenderer().getModel(item, mc.level, null, 0);
                // Vanilla's getter, which NeoForge deprecates for its model-data overload (the same sprite for no data).
                @SuppressWarnings("deprecation")
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                    bakedModel == null ? null : bakedModel.getParticleIcon();
                if (sprite != null) {
                    renderSpriteCube(poseStack, bufferSource, sprite, light, 0xFFFFFFFF);
                }
            }
            ^///?}
        }

        poseStack.popPose();
        bufferSource.endBatch();*/
        //?}
    }

    //? if <26.1 {
    /*/^** Whether {@code block}'s block model is a particle-only placeholder with no geometry of its own: BC's pipe
     *  holder (the pipe body is drawn by a block-entity renderer) and the engines ({@code RenderEngine_BC8}).
     *  {@code renderSingleBlock} would draw nothing for such a block, so its items take the item-model path. *^/
    private static boolean isBlockModelEmpty(net.minecraft.world.level.block.Block block) {
        return block instanceof buildcraft.transport.block.BlockPipeHolder
            || block instanceof buildcraft.lib.engine.BlockEngineBase_BC8;
    }

    /^* Emits the small (~0.30-block) cube textured with {@code sprite} into {@code bufferSource}, every face tinted
     *  {@code tint} (ARGB; white for an item with no paint). Shared by the non-block fallbacks on 1.21.10/1.21.11
     *  and 1.21.1. *^/
    private static void renderSpriteCube(PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int light, int tint) {
        com.mojang.blaze3d.vertex.VertexConsumer buffer = bufferSource.getBuffer(
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(sprite.atlasLocation()));
        float r = 0.15F;
        buildcraft.lib.client.model.ModelUtil.UvFaceData uv =
            new buildcraft.lib.client.model.ModelUtil.UvFaceData(
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        int alpha = (tint >>> 24) & 0xFF;
        int red = (tint >>> 16) & 0xFF;
        int green = (tint >>> 8) & 0xFF;
        int blue = tint & 0xFF;
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            buildcraft.lib.client.model.ModelUtil.createFace(
                face, new org.joml.Vector3f(0F, 0F, 0F), new org.joml.Vector3f(r, r, r), uv)
                .lighti(light)
                .colouri(red, green, blue, alpha)
                .render(poseStack.last(), buffer);
        }
    }*/
    //?}
}
