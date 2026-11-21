/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge {
/*import java.util.Collection;
import java.util.List;
//? if >=1.21.10 {
import java.util.concurrent.CopyOnWriteArrayList;
//?}
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=1.21.10 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/^import net.minecraft.client.gui.GuiGraphics;
^///?}
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
//?} else {
/^import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
^///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
//? if <1.21.10 {
/^import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
^///?}

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.BCTooltipPreContext;
import buildcraft.platform.client.ClientHooks;
import buildcraft.platform.event.HookOrder;
import buildcraft.platform.forge.ForgeBus;
//? if >=1.21.10 {
import buildcraft.platform.forge.ForgeRecipeSync;
//?}

/^*
 * {@link ClientHooks} on MinecraftForge (not NeoForge): each call adds one listener through {@link ForgeBus} at the
 * call, so listeners keep the order (and the normal priority) of NeoForge's twin. What differs:
 *
 * <ul>
 * <li>{@link #onLevelRender}: Forge 26.3 has no submit-pass level hook, so {@code LevelRendererMixin} calls
 * {@code submitLevelRender} where NeoForge posts {@code SubmitCustomGeometryEvent}. On 1.21.1 Forge 52's
 * {@code RenderLevelStageEvent} after the translucent blocks is the hook, as on NeoForge 1.21.1, with an identity pose
 * (the level's view rotation is already on the render system's model-view stack there).</li>
 * <li>{@link #onRecipesReceived}: Forge 26.3 has no recipe sync to the client (vanilla 26.x sends none), so the listener
 * is fed by BuildCraft's own payload ({@code ForgeRecipeSync}), whose client cache is cleared on logout there. 1.21.1's
 * vanilla still sends every recipe, and Forge 52 posts {@code RecipesUpdatedEvent} once they have arrived.</li>
 * <li>{@link #onBlockOutline}: Forge 26.3's {@code RenderHighlightEvent.Block} takes a custom renderer for the frame, the
 * twin of NeoForge's outline render-state event; it is posted for a missed hit too, so a miss is skipped here, and the
 * custom outline does not get vanilla's translucency flag, so it is read from the block's model. On 1.21.1 the shape is
 * drawn inline and the vanilla outline cancelled, as on NeoForge 1.21.1.</li>
 * <li>Cancellable events ({@code LivingEntityUseItemEvent.Tick}, {@code RenderTooltipEvent.Pre}, and on 26.3
 * {@code RenderHighlightEvent.Block}): a plain listener, which never cancels and so never sees an event an earlier
 * listener cancelled. None of these contracts needs either.</li>
 * </ul>
 *^/
public final class ForgeClientHooks implements ClientHooks {

    //? if >=1.21.10 {
    /^* The {@link #onLevelRender} renderers, called by {@code LevelRendererMixin} on the render thread. *^/
    private static final List<Consumer<BCLevelRenderContext>> LEVEL_RENDERERS = new CopyOnWriteArrayList<>();

    /^* Called by {@code LevelRendererMixin} once a frame, in the level's submit pass. *^/
    public static void submitLevelRender(PoseStack poseStack, Vec3 cameraPos, SubmitNodeCollector collector) {
        if (LEVEL_RENDERERS.isEmpty()) {
            return;
        }
        LevelContext context = new LevelContext(poseStack, cameraPos, collector);
        for (Consumer<BCLevelRenderContext> renderer : LEVEL_RENDERERS) {
            renderer.accept(context);
        }
    }

    @Override
    public void onLevelRender(Consumer<BCLevelRenderContext> renderer) {
        LEVEL_RENDERERS.add(renderer);
    }
    //?} else {
    /^@Override
    public void onLevelRender(Consumer<BCLevelRenderContext> renderer) {
        ForgeBus.listen(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                renderer.accept(new LevelContext(new PoseStack(), event.getCamera().getPosition(),
                        Minecraft.getInstance().renderBuffers().bufferSource()));
            }
        });
    }
    ^///?}

    @Override
    public void onClientTick(Runnable listener) {
        ForgeBus.listen(TickEvent.ClientTickEvent.Post.class, event -> listener.run());
    }

    @Override
    public void onLoggingIn(Runnable listener) {
        ForgeBus.listen(ClientPlayerNetworkEvent.LoggingIn.class, event -> listener.run());
    }

    @Override
    public void onLoggingInEarly(Runnable listener) {
        ForgeBus.listen(ClientPlayerNetworkEvent.LoggingIn.class, HookOrder.EARLY, event -> listener.run());
    }

    @Override
    public void onLoggingOut(Runnable listener) {
        ForgeBus.listen(ClientPlayerNetworkEvent.LoggingOut.class, event -> listener.run());
    }

    @Override
    public void onRecipesReceived(Consumer<Collection<RecipeHolder<?>>> listener) {
        //? if >=1.21.10 {
        ForgeRecipeSync.addClientListener(listener);
        //?} else {
        /^ForgeBus.listen(RecipesUpdatedEvent.class, event -> listener.accept(event.getRecipeManager().getRecipes()));
        ^///?}
    }

    @Override
    public void onItemTooltip(ItemTooltipListener listener) {
        ForgeBus.listen(ItemTooltipEvent.class,
                event -> listener.onTooltip(event.getItemStack(), event.getEntity(), event.getToolTip()));
    }

    @Override
    public void onClientItemUseTick(ItemUseTickListener listener) {
        ForgeBus.listen(LivingEntityUseItemEvent.Tick.class, event -> {
            if (event.getEntity().level().isClientSide()) {
                listener.onUseTick(event.getEntity(), event.getItem(), event.getDuration());
            }
        });
    }

    @Override
    public void onTooltipPre(Consumer<BCTooltipPreContext> listener) {
        ForgeBus.listen(RenderTooltipEvent.Pre.class, event -> listener.accept(new TooltipContext(event)));
    }

    //? if >=1.21.10 {
    @Override
    public void onBlockOutline(BlockOutlineOverride override) {
        ForgeBus.listen(RenderHighlightEvent.Block.class, event -> {
            BlockHitResult hit = event.getTarget();
            ClientLevel level = Minecraft.getInstance().level;
            if (hit.getType() == HitResult.Type.MISS || level == null) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            VoxelShape shape = override.outline(level, pos, state, hit);
            if (shape == null) {
                return;
            }
            // Vanilla's own test for drawing the outline after the translucent terrain (LevelExtractor.extractBlockOutline).
            boolean translucent = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
                    .hasMaterialFlag(1);
            BlockPos outlinePos = pos.immutable();
            event.setCustomRenderer((collector, poseStack, levelRenderState) -> {
                Vec3 cam = levelRenderState.cameraRenderState.pos;
                poseStack.pushPose();
                poseStack.translate(outlinePos.getX() - cam.x, outlinePos.getY() - cam.y, outlinePos.getZ() - cam.z);
                collector.submitShapeOutline(poseStack, shape, BCLibRenderTypes.blockOutline(), ARGB.black(102), 2.5F,
                        translucent);
                poseStack.popPose();
            });
        });
    }
    //?} else {
    /^@Override
    public void onBlockOutline(BlockOutlineOverride override) {
        // 1.21.1 has no outline render state: the shape is drawn inline and the vanilla outline cancelled.
        ForgeBus.refuse(RenderHighlightEvent.Block.class, event -> {
            BlockHitResult hit = event.getTarget();
            ClientLevel level = Minecraft.getInstance().level;
            if (hit.getType() == HitResult.Type.MISS || level == null) {
                return false;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return false;
            }
            VoxelShape shape = override.outline(level, pos, state, hit);
            if (shape == null) {
                return false;
            }
            Vec3 cam = event.getCamera().getPosition();
            LevelRenderer.renderVoxelShape(event.getPoseStack(),
                    event.getMultiBufferSource().getBuffer(BCLibRenderTypes.lines()), shape, pos.getX() - cam.x,
                    pos.getY() - cam.y, pos.getZ() - cam.z, 0.0F, 0.0F, 0.0F, 0.4F, false);
            return true;
        });
    }
    ^///?}

    @Override
    public void onClientLevelUnload(Runnable listener) {
        // Client levels only: an integrated server's levels post the same event.
        ForgeBus.listen(LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) {
                listener.run();
            }
        });
    }

    @Override
    public void onClientChunkLoad(BiConsumer<Level, ChunkPos> listener) {
        ForgeBus.listen(ChunkEvent.Load.class,
                event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    @Override
    public void onClientChunkUnload(BiConsumer<Level, ChunkPos> listener) {
        ForgeBus.listen(ChunkEvent.Unload.class,
                event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    private static void acceptClientChunk(BiConsumer<Level, ChunkPos> listener, LevelAccessor accessor, ChunkPos pos) {
        if (accessor != null && accessor.isClientSide() && accessor instanceof Level level) {
            listener.accept(level, pos);
        }
    }

    //? if >=1.21.10 {
    private record LevelContext(PoseStack poseStack, Vec3 cameraPos, SubmitNodeCollector collector)
            implements BCLevelRenderContext {}
    //?} else {
    /^private record LevelContext(PoseStack poseStack, Vec3 cameraPos, MultiBufferSource.BufferSource bufferSource)
            implements BCLevelRenderContext {}
    ^///?}

    /^* {@link BCTooltipPreContext} over the event, read live (another listener may move or re-font the tooltip). *^/
    private record TooltipContext(RenderTooltipEvent.Pre event) implements BCTooltipPreContext {
        @Override
        public ItemStack stack() {
            return event.getItemStack();
        }

        @Override
        //? if >=1.21.10 {
        public GuiGraphicsExtractor graphics() {
        //?} else {
        /^public GuiGraphics graphics() {
        ^///?}
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
}
*///?}
