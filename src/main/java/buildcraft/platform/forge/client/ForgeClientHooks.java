/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.client;

//? if forge && >=1.21.10 {
/*import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
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
import net.minecraftforge.eventbus.api.listener.Priority;

import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.BCTooltipPreContext;
import buildcraft.platform.client.ClientHooks;
import buildcraft.platform.forge.ForgeRecipeSync;

/^*
 * {@link ClientHooks} on MinecraftForge (not NeoForge): each call adds one listener to the event's own static
 * {@code BUS} at the call, so listeners keep the order (and the normal priority) of NeoForge's twin. What differs:
 *
 * <ul>
 * <li>{@link #onLevelRender}: Forge has no submit-pass level hook, so {@code LevelRendererMixin} calls
 * {@link #submitLevelRender} where NeoForge posts {@code SubmitCustomGeometryEvent}.</li>
 * <li>{@link #onRecipesReceived}: Forge has no recipe sync to the client (vanilla 26.x sends none), so the listener is
 * fed by BuildCraft's own payload ({@link ForgeRecipeSync}), whose client cache is cleared on logout there.</li>
 * <li>{@link #onBlockOutline}: Forge's {@code RenderHighlightEvent.Block} takes a custom renderer for the frame, the twin
 * of NeoForge's outline render-state event; it is posted for a missed hit too, so a miss is skipped here, and the
 * custom outline does not get vanilla's translucency flag, so it is read from the block's model.</li>
 * <li>Cancellable events ({@code LivingEntityUseItemEvent.Tick}, {@code RenderTooltipEvent.Pre},
 * {@code RenderHighlightEvent.Block}): a plain listener, which never cancels (EventBus 7; see {@code ForgeGameHooks}),
 * and so never sees an event an earlier listener cancelled. None of these contracts needs either.</li>
 * </ul>
 *^/
public final class ForgeClientHooks implements ClientHooks {

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

    @Override
    public void onClientTick(Runnable listener) {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.run());
    }

    @Override
    public void onLoggingIn(Runnable listener) {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> listener.run());
    }

    @Override
    public void onLoggingInEarly(Runnable listener) {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(Priority.HIGH, event -> listener.run());
    }

    @Override
    public void onLoggingOut(Runnable listener) {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> listener.run());
    }

    @Override
    public void onRecipesReceived(Consumer<Collection<RecipeHolder<?>>> listener) {
        ForgeRecipeSync.addClientListener(listener);
    }

    @Override
    public void onItemTooltip(ItemTooltipListener listener) {
        ItemTooltipEvent.BUS.addListener(
                event -> listener.onTooltip(event.getItemStack(), event.getEntity(), event.getToolTip()));
    }

    @Override
    public void onClientItemUseTick(ItemUseTickListener listener) {
        LivingEntityUseItemEvent.Tick.BUS.addListener((LivingEntityUseItemEvent.Tick event) -> {
            if (event.getEntity().level().isClientSide()) {
                listener.onUseTick(event.getEntity(), event.getItem(), event.getDuration());
            }
        });
    }

    @Override
    public void onTooltipPre(Consumer<BCTooltipPreContext> listener) {
        RenderTooltipEvent.Pre.BUS.addListener((RenderTooltipEvent.Pre event) -> listener.accept(new TooltipContext(event)));
    }

    @Override
    public void onBlockOutline(BlockOutlineOverride override) {
        RenderHighlightEvent.Block.BUS.addListener((RenderHighlightEvent.Block event) -> {
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

    @Override
    public void onClientLevelUnload(Runnable listener) {
        // Client levels only: an integrated server's levels post the same event.
        LevelEvent.Unload.BUS.addListener(event -> {
            if (event.getLevel().isClientSide()) {
                listener.run();
            }
        });
    }

    @Override
    public void onClientChunkLoad(BiConsumer<Level, ChunkPos> listener) {
        ChunkEvent.Load.BUS.addListener(event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    @Override
    public void onClientChunkUnload(BiConsumer<Level, ChunkPos> listener) {
        ChunkEvent.Unload.BUS.addListener(
                event -> acceptClientChunk(listener, event.getLevel(), event.getChunk().getPos()));
    }

    private static void acceptClientChunk(BiConsumer<Level, ChunkPos> listener, LevelAccessor accessor, ChunkPos pos) {
        if (accessor != null && accessor.isClientSide() && accessor instanceof Level level) {
            listener.accept(level, pos);
        }
    }

    private record LevelContext(PoseStack poseStack, Vec3 cameraPos, SubmitNodeCollector collector)
            implements BCLevelRenderContext {}

    /^* {@link BCTooltipPreContext} over the event, read live (another listener may move or re-font the tooltip). *^/
    private record TooltipContext(RenderTooltipEvent.Pre event) implements BCTooltipPreContext {
        @Override
        public ItemStack stack() {
            return event.getItemStack();
        }

        @Override
        public GuiGraphicsExtractor graphics() {
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
