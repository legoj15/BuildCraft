/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge.client;

//? if neoforge {
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.BCTooltipPreContext;
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

    @Override
    public void onClientTick(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.run());
    }

    @Override
    public void onLoggingIn(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> listener.run());
    }

    @Override
    public void onLoggingInEarly(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ClientPlayerNetworkEvent.LoggingIn.class,
                event -> listener.run());
    }

    @Override
    public void onLoggingOut(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.run());
    }

    @Override
    public void onRecipesReceived(Consumer<Collection<RecipeHolder<?>>> listener) {
        //? if >=1.21.10 {
        // NeoForge's opt-in recipe sync (requested server-side via OnDatapackSyncEvent.sendRecipes).
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesReceivedEvent.class,
                event -> listener.accept(event.getRecipeMap().values()));
        //?} else {
        /*// Vanilla still syncs the full recipe list; the event fires once it has arrived.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RecipesUpdatedEvent.class,
                event -> listener.accept(event.getRecipeManager().getRecipes()));*/
        //?}
    }

    @Override
    public void onItemTooltip(ItemTooltipListener listener) {
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class,
                event -> listener.onTooltip(event.getItemStack(), event.getEntity(), event.getToolTip()));
    }

    @Override
    public void onTooltipPre(Consumer<BCTooltipPreContext> listener) {
        NeoForge.EVENT_BUS.addListener(RenderTooltipEvent.Pre.class, event -> listener.accept(new TooltipContext(event)));
    }

    @Override
    public void onBlockOutline(BlockOutlineOverride override) {
        //? if >=1.21.10 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent.class,
                event -> {
                    VoxelShape shape = override.outline(event.getLevel(), event.getBlockPos(), event.getBlockState(),
                            event.getHitResult());
                    if (shape != null) {
                        event.addCustomRenderer(new ShapeOutline(shape));
                    }
                });
        //?} else {
        /*// 1.21.1 has no outline-render-state extraction: draw the shape inline and cancel the vanilla outline.
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderHighlightEvent.Block.class, event -> {
            net.minecraft.world.phys.BlockHitResult hit = event.getTarget();
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            BlockPos blockPos = hit.getBlockPos();
            VoxelShape shape = override.outline(level, blockPos, level.getBlockState(blockPos), hit);
            if (shape == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            com.mojang.blaze3d.vertex.VertexConsumer lines =
                    event.getMultiBufferSource().getBuffer(BCLibRenderTypes.lines());
            net.minecraft.client.renderer.LevelRenderer.renderVoxelShape(event.getPoseStack(), lines, shape,
                    blockPos.getX() - cam.x, blockPos.getY() - cam.y, blockPos.getZ() - cam.z,
                    0.0F, 0.0F, 0.0F, 0.4F, false);
            event.setCanceled(true);
        });*/
        //?}
    }

    @Override
    public void onClientLevelUnload(Runnable listener) {
        // Client levels only: an integrated server's levels post the same event on this bus.
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                listener.run();
            }
        });
    }

    @Override
    public void onClientChunkLoad(BiConsumer<Level, net.minecraft.world.level.ChunkPos> listener) {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class,
                event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    @Override
    public void onClientChunkUnload(BiConsumer<Level, net.minecraft.world.level.ChunkPos> listener) {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class,
                event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    private static void acceptClientChunk(BiConsumer<Level, net.minecraft.world.level.ChunkPos> listener,
            LevelAccessor accessor, net.minecraft.world.level.ChunkPos pos) {
        if (accessor.isClientSide() && accessor instanceof Level level) {
            listener.accept(level, pos);
        }
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

    /** {@link BCTooltipPreContext} over the event, read live (another listener may move or re-font the tooltip). */
    private record TooltipContext(RenderTooltipEvent.Pre event) implements BCTooltipPreContext {
        @Override
        public ItemStack stack() {
            return event.getItemStack();
        }

        @Override
        //? if >=26.1 {
        public net.minecraft.client.gui.GuiGraphicsExtractor graphics() {
        //?} else {
        /*public net.minecraft.client.gui.GuiGraphics graphics() {*/
        //?}
            return event.getGraphics();
        }

        @Override
        public int x() {
            return event.getX();
        }

        @Override
        public int y() {
            return event.getY();
        }

        @Override
        public int screenWidth() {
            return event.getScreenWidth();
        }

        @Override
        public int screenHeight() {
            return event.getScreenHeight();
        }

        @Override
        public Font font() {
            return event.getFont();
        }

        @Override
        public List<ClientTooltipComponent> components() {
            return event.getComponents();
        }

        @Override
        public ClientTooltipPositioner positioner() {
            return event.getTooltipPositioner();
        }
    }

    //? if >=1.21.10 {
    /** Draws a shape in place of the vanilla block outline. Captures only the immutable shape — never the level — as
     *  {@link net.neoforged.neoforge.client.CustomBlockOutlineRenderer} requires. */
    private record ShapeOutline(VoxelShape shape) implements net.neoforged.neoforge.client.CustomBlockOutlineRenderer {
        //? if >=26.2 {
        @Override
        public boolean render(net.minecraft.client.renderer.state.level.BlockOutlineRenderState renderState,
                net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
                net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState) {
            // 26.2: outline geometry goes through the retained-mode submit system. There is no
            // per-pass callback anymore — submitShapeOutline takes an `afterTerrain` flag
            // (= renderState.isTranslucent()) that defers the draw to the right pass, exactly as
            // LevelRenderer.submitBlockOutline does for the vanilla outline.
            Vec3 cam = levelRenderState.cameraRenderState.pos;
            BlockPos pos = renderState.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            submitNodeCollector.submitShapeOutline(poseStack, shape, BCLibRenderTypes.blockOutline(),
                    net.minecraft.util.ARGB.black(102), 2.5F, renderState.isTranslucent());
            poseStack.popPose();
            return true;
        }
        //?} elif >=26.1 {
        /*@Override
        public boolean render(net.minecraft.client.renderer.state.level.BlockOutlineRenderState renderState,
                net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer, PoseStack poseStack,
                boolean translucentPass, net.minecraft.client.renderer.state.level.LevelRenderState levelRenderState) {
            return renderImmediate(renderState.pos(), renderState.isTranslucent(), buffer, poseStack, translucentPass,
                    levelRenderState.cameraRenderState.pos);
        }*/
        //?} else {
        /*@Override
        public boolean render(net.minecraft.client.renderer.state.BlockOutlineRenderState renderState,
                net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer, PoseStack poseStack,
                boolean translucentPass, net.minecraft.client.renderer.state.LevelRenderState levelRenderState) {
            return renderImmediate(renderState.pos(), renderState.isTranslucent(), buffer, poseStack, translucentPass,
                    levelRenderState.cameraRenderState.pos);
        }*/
        //?}

        //? if <26.2 {
        /*private boolean renderImmediate(BlockPos pos, boolean outlineTranslucent,
                net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer, PoseStack poseStack,
                boolean translucentPass, Vec3 cam) {
            // Draw — and suppress the vanilla outline — only in the pass vanilla itself would
            // have drawn the outline. Mirrors LevelRenderer.renderBlockOutline's pass gating.
            if (translucentPass != outlineTranslucent) {
                return false;
            }
            com.mojang.blaze3d.vertex.VertexConsumer lines = buffer.getBuffer(BCLibRenderTypes.lines());
            net.minecraft.client.renderer.ShapeRenderer.renderShape(poseStack, lines, shape,
                    pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                    //? if >=1.21.11 {
                    net.minecraft.util.ARGB.black(102), 2.5F);
                    //?} else {
                    /^0x66000000);^/
                    //?}
            buffer.endLastBatch();
            return true;
        }
        *///?}
    }
    //?}
}
//?}
