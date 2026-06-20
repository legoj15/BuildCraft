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
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.Sheets;
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//? if >=1.21.10 {
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;*/
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
import buildcraft.transport.client.model.PipeModelCacheAll;
import buildcraft.transport.pipe.Pipe;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.pipe.flow.TravellingItem;
import buildcraft.transport.tile.TilePipeHolder;

import org.jspecify.annotations.Nullable;

/** BlockEntityRenderer for pipe holder blocks. Renders the pipe body geometry
 *  plus dynamic content (pluggables, fluid/item/power flow) via registered renderers.
 *
 *  Following the vanilla CampfireRenderer pattern: item models are resolved during
 *  extractRenderState() and stored as pre-resolved ItemStackRenderState instances.
 *  The submit() method only positions and draws them. */
//? if >=1.21.10 {
public class RenderPipeHolder implements BlockEntityRenderer<TilePipeHolder, PipeHolderRenderState> {

    private final ItemModelResolver itemModelResolver;

    public RenderPipeHolder(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }
//?} else {
/*public class RenderPipeHolder implements BlockEntityRenderer<TilePipeHolder> {

    public RenderPipeHolder(BlockEntityRendererProvider.Context context) {
    }*/
//?}

    //? if >=1.21.10 {
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
        renderState.partialTick = partialTick;

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

        int light = buildcraft.lib.client.render.LightUtil.getLightCoords(level, pipe.getBlockPos());

        poseStack.pushPose();

        // Static pipe body geometry is rendered by the chunk mesh via PipeBlockStateModel
        // (DynamicBlockStateModel). The BER only handles dynamic content.

        // --- Render wires ---
        //? if >=26.2 {
        /*PipeWireRenderer.renderWires(pipe, poseStack, light, collector);*/
        //?} else {
        // 1.21.10/1.21.11/26.1 keep the proven immediate-mode self-sourcing wire draw; only 26.2
        // (which removed renderBuffers()) routes wires through the collector above.
        PipeWireRenderer.renderWires(pipe, poseStack.last(), light);
        //?}

        // --- Render pre-resolved item models ---
        // Following vanilla CampfireRenderer: item models were resolved in
        // extractRenderState(). We just position and submit each here.
        submitItems(renderState, poseStack, collector, light);

        // --- Render flow content that doesn't use pre-resolved state ---
        // (fluids, power, stripes beam, pluggables). On 1.21.10/1.21.11/26.1 these self-source their
        // own immediate-mode buffers; on 26.2 (renderBuffers() removed) they route through the
        // collector via submitCustomGeometry. renderContents gates the two paths internally.
        ItemRenderUtil.beginItemBatch(poseStack, collector, light);
        renderContents(pipe, 0, 0, 0, renderState.partialTick, poseStack, collector);
        ItemRenderUtil.endItemBatch();

        poseStack.popPose();
    }


    /** Submit pre-resolved item render states from extractRenderState(). */
    private static void submitItems(PipeHolderRenderState renderState, PoseStack poseStack,
                                     SubmitNodeCollector collector, int light) {
        if (renderState.itemEntries.isEmpty()) return;

        Random modelOffsetRandom = new Random(0);

        //? if <26.2 {
        // Lazily create colour overlay buffer only if needed (immediate-mode path; 26.2 routes the
        // colour box through submitCustomGeometry below instead).
        VertexConsumer colourBuffer = null;
        MultiBufferSource.BufferSource colourBufferSource = null;
        boolean needsColourFlush = false;
        //?}

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
                //? if >=26.2 {
                /*// 26.2 removed immediate-mode buffers: queue the colour box through the collector. The
                // entry translation is baked into the snapshotted pose (submitCustomGeometry copies
                // poseStack.last() at submit time), so the deferred lambda only emits the cached quads.
                poseStack.pushPose();
                poseStack.translate(entry.posX, entry.posY, entry.posZ);
                final PipeHolderRenderState.ItemRenderEntry colourEntry = entry;
                collector.submitCustomGeometry(poseStack,
                    buildcraft.lib.client.render.BCLibRenderTypes.cutoutBlockSheet(),
                    (pose, consumer) -> drawColourOverlay(pose, consumer, colourEntry));
                poseStack.popPose();
                *///?} else {
                if (colourBuffer == null) {
                    colourBufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                    colourBuffer = colourBufferSource.getBuffer(Sheets.cutoutBlockSheet());
                    needsColourFlush = true;
                }
                renderColourOverlay(poseStack, colourBuffer, entry);
                //?}
            }
        }

        //? if <26.2 {
        if (needsColourFlush && colourBufferSource != null) {
            colourBufferSource.endBatch(Sheets.cutoutBlockSheet());
        }
        //?}
    }

    //? if <26.2 {
    // Renders the dye colour overlay box for a tagged item (immediate-mode path). Applies the entry
    // translation to poseStack itself, then draws into the supplied buffer.
    private static void renderColourOverlay(PoseStack poseStack, VertexConsumer buffer,
                                             PipeHolderRenderState.ItemRenderEntry entry) {
        poseStack.pushPose();
        poseStack.translate(entry.posX, entry.posY, entry.posZ);
        drawColourOverlay(poseStack.last(), buffer, entry);
        poseStack.popPose();
    }
    //?}

    /** Emits the dye colour overlay quads into {@code buffer} using the already-positioned {@code pose}
     *  (the entry translation must be baked into {@code pose} by the caller). Node-agnostic. */
    private static void drawColourOverlay(PoseStack.Pose pose, VertexConsumer buffer,
                                          PipeHolderRenderState.ItemRenderEntry entry) {
        MutableQuad[] colourQuads = PipeFlowRendererItems.getColouredQuads();
        if (colourQuads == null) return;

        int col = buildcraft.lib.misc.ColourUtil.getLightHex(entry.colour);
        int r = (col >> 16) & 0xFF;
        int g = (col >> 8) & 0xFF;
        int b = col & 0xFF;

        for (MutableQuad q : colourQuads) {
            if (q == null) continue;
            MutableQuad q2 = new MutableQuad(q);
            q2.lighti(15, 15);
            q2.multColouri(r, g, b, 255);
            q2.render(pose, buffer);
        }
    }
    //?} else {
    /*// 1.21.1 classic BlockEntityRenderer: a single render() draws everything directly (no separate
    // render-state / SubmitNodeCollector). The modern extractRenderState (item-model resolution) and
    // submitItems (positioning + submit) collapse into renderItems() using ItemRenderer.renderStatic.
    @Override
    public void render(TilePipeHolder pipe, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (pipe == null) return;
        Level level = pipe.getLevel();
        if (level == null) return;
        int light = buildcraft.lib.client.render.LightUtil.getLightCoords(level, pipe.getBlockPos());

        poseStack.pushPose();
        PipeWireRenderer.renderWires(pipe, poseStack.last(), light);
        renderItems(pipe, poseStack, bufferSource, light, partialTick);
        ItemRenderUtil.beginItemBatch(poseStack, bufferSource, light);
        renderContents(pipe, 0, 0, 0, partialTick, poseStack);
        ItemRenderUtil.endItemBatch();
        poseStack.popPose();
    }

    private void renderItems(TilePipeHolder pipe, PoseStack poseStack,
                             net.minecraft.client.renderer.MultiBufferSource bufferSource, int light, float partialTick) {
        Pipe p = pipe.getPipe();
        if (p == null || !(p.flow instanceof PipeFlowItems flowItems)) return;
        Level world = pipe.getLevel();
        if (world == null) return;
        net.minecraft.client.renderer.entity.ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        long now = world.getGameTime();
        int posHash = (int) pipe.getBlockPos().asLong();
        List<TravellingItem> items = flowItems.getAllItemsForRender();
        Random modelOffsetRandom = new Random(0);
        VertexConsumer colourBuffer = null;
        MultiBufferSource.BufferSource colourBufferSource = null;
        boolean needsColourFlush = false;

        for (int idx = 0; idx < items.size(); idx++) {
            TravellingItem item = items.get(idx);
            ItemStack stack = item.clientItemLink.get();
            if (stack == null || stack.isEmpty()) stack = item.getStack();
            if (stack == null || stack.isEmpty()) continue;

            Vec3 pos = item.getRenderPosition(BlockPos.ZERO, now, partialTick, flowItems);
            Direction dir = item.getRenderDirection(now, partialTick);
            if (dir == null) dir = Direction.EAST;
            int count = item.stackSize > 0 ? item.stackSize : stack.getCount();

            int itemModelCount = getStackModelCount(count);
            if (itemModelCount > 1) setupModelOffsetRandom(modelOffsetRandom, count);

            for (int i = 0; i < itemModelCount; i++) {
                poseStack.pushPose();
                float dx = 0, dy = 0, dz = 0;
                if (i > 0) {
                    dx = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    dy = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                    dz = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                }
                poseStack.translate(pos.x + dx, pos.y + dy, pos.z + dz);
                poseStack.scale(0.30f, 0.30f, 0.30f);
                applyDirectionRotation(poseStack, dir);
                itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                        poseStack, bufferSource, world, posHash + idx);
                poseStack.popPose();
            }

            if (item.colour != null) {
                if (colourBuffer == null) {
                    colourBufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                    colourBuffer = colourBufferSource.getBuffer(Sheets.cutoutBlockSheet());
                    needsColourFlush = true;
                }
                MutableQuad[] colourQuads = PipeFlowRendererItems.getColouredQuads();
                if (colourQuads != null) {
                    int col = buildcraft.lib.misc.ColourUtil.getLightHex(item.colour);
                    int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
                    poseStack.pushPose();
                    poseStack.translate(pos.x, pos.y, pos.z);
                    for (MutableQuad q : colourQuads) {
                        if (q == null) continue;
                        MutableQuad q2 = new MutableQuad(q);
                        q2.lighti(15, 15);
                        q2.multColouri(r, g, b, 255);
                        q2.render(poseStack.last(), colourBuffer);
                    }
                    poseStack.popPose();
                }
            }
        }

        if (needsColourFlush && colourBufferSource != null) {
            colourBufferSource.endBatch(Sheets.cutoutBlockSheet());
        }
    }*/
    //?}

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

    //? if >=1.21.10 {
    private static void renderContents(TilePipeHolder pipe, double x, double y, double z,
        float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
    //?} else {
    /*private static void renderContents(TilePipeHolder pipe, double x, double y, double z,
        float partialTicks, PoseStack poseStack) {*/
    //?}
        Pipe p = pipe.getPipe();
        if (p == null) {
            return;
        }
        // Item flow rendering is now handled by submitItems()/renderItems() using pre-resolved states.
        // Only render non-item flows here (fluids, power).
        if (p.flow != null && !(p.flow instanceof PipeFlowItems)) {
            //? if >=26.2 {
            /*renderFlow(p.flow, x, y, z, partialTicks, poseStack, collector);*/
            //?} else {
            renderFlow(p.flow, x, y, z, partialTicks, poseStack.last());
            //?}
        }
        if (p.behaviour != null) {
            //? if >=26.2 {
            /*renderBehaviour(p.behaviour, x, y, z, partialTicks, poseStack, collector);*/
            //?} else {
            renderBehaviour(p.behaviour, x, y, z, partialTicks, poseStack.last());
            //?}
        }
        //? if >=26.2 {
        /*// 26.2 removed immediate-mode buffers: queue all pluggable geometry through the collector.
        // The plug renderers transform a PoseStack themselves and draw via poseStack.last(); inside a
        // deferred submit lambda the outer poseStack has already been popped, so seed a fresh PoseStack
        // from the snapshotted pose for them to build on.
        collector.submitCustomGeometry(poseStack,
            buildcraft.lib.client.render.BCLibRenderTypes.cutoutBlockSheet(), (pose, consumer) -> {
                for (Direction facing : Direction.values()) {
                    PipePluggable plug = pipe.getPluggable(facing);
                    if (plug != null) {
                        PoseStack plugPose = new PoseStack();
                        plugPose.last().set(pose);
                        renderPluggable(plug, x, y, z, partialTicks, consumer, plugPose);
                    }
                }
            });
        *///?} else {
        VertexConsumer plugBuffer = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(buildcraft.lib.client.render.BCLibRenderTypes.cutoutBlockSheet());
        for (Direction facing : Direction.values()) {
            PipePluggable plug = pipe.getPluggable(facing);
            if (plug != null) {
                renderPluggable(plug, x, y, z, partialTicks, plugBuffer, poseStack);
            }
        }
        //?}
    }

    @SuppressWarnings("unchecked")
    private static <P extends PipePluggable> void renderPluggable(P plug, double x, double y, double z,
        float partialTicks, VertexConsumer plugBuffer, PoseStack poseStack) {
        buildcraft.api.transport.pluggable.IPlugDynamicRenderer<P> renderer = PipeRegistryClient.getPlugRenderer(plug);
        if (renderer != null) {
            renderer.render(plug, x, y, z, partialTicks, plugBuffer, poseStack);
        }
    }

    //? if >=26.2 {
    /*@SuppressWarnings("unchecked")
    private static <F extends PipeFlow> void renderFlow(F flow, double x, double y, double z,
        float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
        // Modern (26.2) flow dispatch: route BuildCraft's own flow renderers through their
        // SubmitNodeCollector overloads (submitCustomGeometry). Any third-party flow renderer (none
        // ship today) falls back to the deprecated immediate-mode interface with the snapshot base pose.
        IPipeFlowRenderer<F> renderer = PipeRegistryClient.getFlowRenderer(flow);
        if (renderer == null) return;
        if (renderer == PipeFlowRendererPower.INSTANCE) {
            PipeFlowRendererPower.INSTANCE.render((buildcraft.transport.pipe.flow.PipeFlowPower) flow,
                x, y, z, partialTicks, collector, poseStack);
        } else if (renderer == PipeFlowRendererFE.INSTANCE) {
            PipeFlowRendererFE.INSTANCE.render((buildcraft.transport.pipe.flow.PipeFlowRedstoneFlux) flow,
                x, y, z, partialTicks, collector, poseStack);
        } else if (renderer == PipeFlowRendererFluids.INSTANCE) {
            PipeFlowRendererFluids.INSTANCE.render((buildcraft.transport.pipe.flow.PipeFlowFluids) flow,
                x, y, z, partialTicks, collector, poseStack);
        } else {
            renderer.render(flow, x, y, z, partialTicks, null, poseStack.last());
        }
    }

    @SuppressWarnings("unchecked")
    private static <B extends PipeBehaviour> void renderBehaviour(B behaviour, double x, double y, double z,
        float partialTicks, PoseStack poseStack, SubmitNodeCollector collector) {
        IPipeBehaviourRenderer<B> renderer = PipeRegistryClient.getBehaviourRenderer(behaviour);
        if (renderer == null) return;
        if (renderer == PipeBehaviourRendererStripes.INSTANCE) {
            PipeBehaviourRendererStripes.INSTANCE.render(
                (buildcraft.transport.pipe.behaviour.PipeBehaviourStripes) behaviour,
                x, y, z, partialTicks, collector, poseStack);
        } else {
            renderer.render(behaviour, x, y, z, partialTicks, null, poseStack.last());
        }
    }
    *///?} else {
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
    //?}
}
