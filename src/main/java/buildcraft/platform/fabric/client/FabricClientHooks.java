/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric {
/*import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.Event;

import buildcraft.platform.client.BCLevelRenderContext;
import buildcraft.platform.client.BCTooltipPreContext;
import buildcraft.platform.client.ClientHooks;

/^* {@link ClientHooks} on Fabric: Fabric API's client events, one callback per call, in call order. *^/
public final class FabricClientHooks implements ClientHooks {

    /^* The join phase {@link #onLoggingInEarly} listeners run in, ahead of the default phase. *^/
    private static final Identifier EARLY_JOIN = Identifier.fromNamespaceAndPath("buildcraftunofficial", "early");

    public FabricClientHooks() {
        ClientPlayConnectionEvents.JOIN.addPhaseOrdering(EARLY_JOIN, Event.DEFAULT_PHASE);
    }

    @Override
    public void onLevelRender(Consumer<BCLevelRenderContext> renderer) {
        // COLLECT_SUBMITS is fabric-rendering-v1's point for queueing custom geometry on the frame's collector,
        // NeoForge's SubmitCustomGeometryEvent.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> renderer.accept(new LevelContext(context)));
    }

    @Override
    public void onClientTick(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void onLoggingIn(Runnable listener) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.run());
    }

    @Override
    public void onLoggingInEarly(Runnable listener) {
        ClientPlayConnectionEvents.JOIN.register(EARLY_JOIN, (handler, sender, client) -> listener.run());
    }

    @Override
    public void onLoggingOut(Runnable listener) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
    }

    @Override
    public void onRecipesReceived(Consumer<Collection<RecipeHolder<?>>> listener) {
        // fabric-recipe-api-v1's client sync: the recipe types the server synchronises (none unless one asked).
        ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> listener.accept(recipes.recipes()));
    }

    @Override
    public void onItemTooltip(ItemTooltipListener listener) {
        // Fabric's callback carries no player; the local player is the one the tooltip is built for.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                listener.onTooltip(stack, Minecraft.getInstance().player, lines));
    }

    @Override
    public void onTooltipPre(Consumer<BCTooltipPreContext> listener) {
        // Gap: Fabric API 26.3 has no "tooltip about to be drawn" event with its position and components (only
        // ClientTooltipComponentCallback, which maps tooltip data to components). The listener is not called, so the
        // panels BuildCraft draws below a tooltip (blueprint previews) do not show; a Fabric port of them would be a
        // ClientTooltipComponent instead.
    }

    @Override
    public void onBlockOutline(BlockOutlineOverride override) {
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null || !(mc.hitResult instanceof BlockHitResult hit)) {
                return true;
            }
            BlockPos pos = outline.pos();
            VoxelShape shape = override.outline(level, pos, level.getBlockState(pos), hit);
            if (shape == null) {
                return true;
            }
            // The same draw as NeoForge's custom outline renderer: a translucent line outline, deferred to the pass
            // the vanilla outline would have used.
            Vec3 cam = context.levelState().cameraRenderState.pos;
            PoseStack poseStack = context.poseStack();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            context.submitNodeCollector().submitShapeOutline(poseStack, shape,
                    mc.gameRenderer.useImprovedTransparency()
                            ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent(),
                    ARGB.black(102), 2.5F, outline.isTranslucent());
            poseStack.popPose();
            return false;
        });
    }

    @Override
    public void onClientLevelUnload(Runnable listener) {
        // Fabric has no client level-unload event: a level change (dimension change) and a disconnect are the two
        // ways a client level closes.
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> listener.run());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
    }

    @Override
    public void onClientChunkLoad(BiConsumer<Level, ChunkPos> listener) {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> listener.accept(level, chunk.getPos()));
    }

    @Override
    public void onClientChunkUnload(BiConsumer<Level, ChunkPos> listener) {
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> listener.accept(level, chunk.getPos()));
    }

    private record LevelContext(LevelRenderContext context) implements BCLevelRenderContext {
        @Override
        public PoseStack poseStack() {
            return context.poseStack();
        }

        @Override
        public Vec3 cameraPos() {
            return context.levelState().cameraRenderState.pos;
        }

        @Override
        public float partialTick() {
            return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        }

        @Override
        public SubmitNodeCollector collector() {
            return context.submitNodeCollector();
        }
    }
}
*///?}
