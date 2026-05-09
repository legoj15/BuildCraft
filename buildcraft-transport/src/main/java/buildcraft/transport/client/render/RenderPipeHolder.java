/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.render;

import java.util.List;
import java.util.Random;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.pipe.IPipeBehaviourRenderer;
import buildcraft.api.transport.pipe.IPipeFlowRenderer;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeFlow;
import buildcraft.api.transport.pluggable.PipePluggable;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.render.ItemRenderUtil;
import buildcraft.transport.client.PipeRegistryClient;
import buildcraft.transport.client.model.ModelPipe;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

import org.jetbrains.annotations.Nullable;

/** BlockEntityRenderer for pipe holder blocks. Renders the pipe body geometry
 *  plus dynamic content (pluggables, fluid/item/power flow) via registered renderers.
 *
 *  Following the vanilla CampfireRenderer pattern: item models are resolved during
 *  extractRenderState() and stored as pre-resolved ItemStackRenderState instances.
 *  The submit() method only positions and draws them. */
public class RenderPipeHolder implements BlockEntityRenderer<TilePipeHolder, PipeHolderRenderState> {

    private final ItemModelResolver itemModelResolver;

    public RenderPipeHolder(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PipeHolderRenderState createRenderState() {
        return new PipeHolderRenderState();
    }

    /** Populate render state with tile data AND pre-resolve item models.
     *  This is called by the engine BEFORE submit(), on the render thread.
     *  Following the vanilla CampfireRenderer pattern, item models are resolved here. */
    public void extractRenderState(TilePipeHolder blockEntity, PipeHolderRenderState renderState,
                                    float partialTick, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);
        renderState.pipe = blockEntity;

        // Pre-resolve item models for item pipe flow
        renderState.itemEntries.clear();
        Pipe pipe = blockEntity.getPipe();
        if (pipe != null && pipe.flow instanceof PipeFlowItems flowItems) {
            Level world = blockEntity.getLevel();
            if (world != null) {
                long now = world.getGameTime();
                int posHash = (int) blockEntity.getBlockPos().asLong();
                List<TravellingItem> items = flowItems.getAllItemsForRender();

                for (int i = 0; i < items.size(); i++) {
                    TravellingItem item = items.get(i);
                    ItemStack stack = item.clientItemLink.get();
                    if (stack == null || stack.isEmpty()) {
                        stack = item.getStack();
                    }
                    if (stack == null || stack.isEmpty()) continue;

                    // Create a fresh ItemStackRenderState per item (vanilla pattern)
                    ItemStackRenderState itemState = new ItemStackRenderState();
                    // Use NONE to skip display transforms — 1.12.2 read raw model quads
                    // and scaled them uniformly. FIXED applies per-model transforms that
                    // shrink blocks more than items, causing inconsistent sizing.
                    this.itemModelResolver.updateForTopItem(
                        itemState, stack, ItemDisplayContext.NONE,
                        world, null, posHash + i);

                    if (itemState.isEmpty()) continue;

                    // Compute render position
                    Vec3 pos = item.getRenderPosition(BlockPos.ZERO, now, partialTick, flowItems);
                    Direction dir = item.getRenderDirection(now, partialTick);
                    int count = item.stackSize > 0 ? item.stackSize : stack.getCount();

                    renderState.itemEntries.add(new PipeHolderRenderState.ItemRenderEntry(
                        itemState, pos.x, pos.y, pos.z, dir, item.colour, count));
                }
            }
        }
    }

    @Override
    public void submit(PipeHolderRenderState renderState, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        TilePipeHolder pipe = renderState.pipe;
        if (pipe == null) return;
        Level level = pipe.getLevel();
        if (level == null) return;

        int light = LevelRenderer.getLightColor(level, pipe.getBlockPos());

        poseStack.pushPose();

        // Static pipe body is rendered by the chunk mesh via PipeBlockStateModel.
        // The BER only handles dynamic content (flows, behaviours).

        // --- Render pre-resolved item models ---
        // Following vanilla CampfireRenderer: item models were resolved in
        // extractRenderState(). We just position and submit each here.
        submitItems(renderState, poseStack, collector, light);

        // --- Render flow content that doesn't use pre-resolved state ---
        // (fluids, power — these create their own buffer sources internally)
        // Also renders behaviours (stripes beam)
        ItemRenderUtil.beginItemBatch(poseStack, collector, light);
        renderContents(pipe, 0, 0, 0, 0, poseStack.last());
        ItemRenderUtil.endItemBatch();

        poseStack.popPose();
    }

    /** Submit pre-resolved item render states from extractRenderState(). */
    private static void submitItems(PipeHolderRenderState renderState, PoseStack poseStack,
                                     SubmitNodeCollector collector, int light) {
        if (renderState.itemEntries.isEmpty()) return;

        Random modelOffsetRandom = new Random(0);

        // Lazily create colour overlay buffer only if needed
        VertexConsumer colourBuffer = null;
        MultiBufferSource.BufferSource colourBufferSource = null;
        boolean needsColourFlush = false;

        for (PipeHolderRenderState.ItemRenderEntry entry : renderState.itemEntries) {
            if (entry.renderState.isEmpty()) continue;

            Direction dir = entry.direction != null ? entry.direction : Direction.EAST;
            int itemModelCount = getStackModelCount(entry.stackCount);

            if (itemModelCount > 1) {
                setupModelOffsetRandom(modelOffsetRandom, entry.stackCount);
            }

            for (int i = 0; i < itemModelCount; i++) {
                poseStack.pushPose();

                float dx = 0, dy = 0, dz = 0;
                if (i > 0) {
                    dx = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    dy = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    dz = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                }

                poseStack.translate(entry.posX + dx, entry.posY + dy, entry.posZ + dz);

                // Scale to pipe item size — matches 1.12.2 scale of 0.30f
                poseStack.scale(0.30f, 0.30f, 0.30f);

                // Rotate to face the travel direction
                applyDirectionRotation(poseStack, dir);

                // Submit the pre-resolved item model
                entry.renderState.submit(poseStack, collector,
                    light, OverlayTexture.NO_OVERLAY, 0);

                poseStack.popPose();
            }

            // Render colour overlay box for dye-tagged items
            if (entry.colour != null) {
                if (colourBuffer == null) {
                    colourBufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                    colourBuffer = colourBufferSource.getBuffer(Sheets.cutoutBlockSheet());
                    needsColourFlush = true;
                }
                renderColourOverlay(poseStack, colourBuffer, entry);
            }
        }

        if (needsColourFlush && colourBufferSource != null) {
            colourBufferSource.endBatch(Sheets.cutoutBlockSheet());
        }
    }

    /** Renders the dye colour overlay box for a tagged item. */
    private static void renderColourOverlay(PoseStack poseStack, VertexConsumer buffer,
                                             PipeHolderRenderState.ItemRenderEntry entry) {
        MutableQuad[] colourQuads = PipeFlowRendererItems.getColouredQuads();
        if (colourQuads == null) return;

        int col = buildcraft.lib.misc.ColourUtil.getLightHex(entry.colour);
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;

        poseStack.pushPose();
        poseStack.translate(entry.posX, entry.posY, entry.posZ);
        for (MutableQuad q : colourQuads) {
            if (q == null) continue;
            MutableQuad q2 = new MutableQuad(q);
            q2.lighti(15, 15);
            q2.multColouri(r, g, b, 255);
            q2.render(poseStack.last(), buffer);
        }
        poseStack.popPose();
    }

    /** Applies rotation so the item faces the given direction of travel. */
    private static void applyDirectionRotation(PoseStack ps, Direction dir) {
        switch (dir) {
            case NORTH -> ps.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> ps.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> ps.mulPose(Axis.YP.rotationDegrees(-90));
            case UP -> ps.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> {} // default orientation
        }
    }

    private static void setupModelOffsetRandom(Random random, int stackCount) {
        random.setSeed(stackCount & 0x7F_FF_FF_FFL);
    }

    private static int getStackModelCount(int stackCount) {
        if (stackCount > 48) return 5;
        if (stackCount > 32) return 4;
        if (stackCount > 16) return 3;
        if (stackCount > 1) return 2;
        return 1;
    }

    private static void renderContents(TilePipeHolder pipe, double x, double y, double z,
        float partialTicks, PoseStack.Pose pose) {
        Pipe p = pipe.getPipe();
        if (p == null) {
            return;
        }
        // Item flow rendering is now handled by submitItems() using pre-resolved states.
        // Only render non-item flows here (fluids, power).
        if (p.flow != null && !(p.flow instanceof PipeFlowItems)) {
            renderFlow(p.flow, x, y, z, partialTicks, pose);
        }
        if (p.behaviour != null) {
            renderBehaviour(p.behaviour, x, y, z, partialTicks, pose);
        }
    }

    @SuppressWarnings("unchecked")
    private static <F extends PipeFlow> void renderFlow(F flow, double x, double y, double z,
        float partialTicks, PoseStack.Pose pose) {
        IPipeFlowRenderer<F> renderer = PipeRegistryClient.getFlowRenderer(flow);
        if (renderer != null) {
            renderer.render(flow, x, y, z, partialTicks, null, pose);
        }
    }

    @SuppressWarnings("unchecked")
    private static <B extends PipeBehaviour> void renderBehaviour(B behaviour, double x, double y, double z,
        float partialTicks, PoseStack.Pose pose) {
        IPipeBehaviourRenderer<B> renderer = PipeRegistryClient.getBehaviourRenderer(behaviour);
        if (renderer != null) {
            renderer.render(behaviour, x, y, z, partialTicks, null, pose);
        }
    }
}
