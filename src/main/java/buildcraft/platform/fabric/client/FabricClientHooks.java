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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
//?} else {
/^import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
^///?}
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//? if >=1.21.10 {
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
//?}
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//?} elif >=1.21.10 {
/^import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
^///?} else {
/^import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
^///?}
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
        //? if >=26.1 {
        // COLLECT_SUBMITS is fabric-rendering-v1's point for queueing custom geometry on the frame's collector,
        // NeoForge's SubmitCustomGeometryEvent.
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> renderer.accept(new LevelContext(context)));
        //?} elif >=1.21.10 {
        /^// END_MAIN runs after the translucent terrain, NeoForge's AfterTranslucentBlocks stage; the geometry is drawn
        // into the main buffer source, which the renderers flush themselves.
        WorldRenderEvents.END_MAIN.register(context -> renderer.accept(new LevelContext(context)));
        ^///?} else {
        /^// AFTER_TRANSLUCENT runs after the translucent terrain and particles, NeoForge's AFTER_TRANSLUCENT_BLOCKS stage;
        // as there, the pose stack is the level's (the camera rotation is on the model-view stack) and the geometry goes
        // into the main buffer source, which the renderers flush themselves.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> renderer.accept(new LevelContext(context)));
        ^///?}
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
        //? if >=1.21.10 {
        // fabric-recipe-api-v1's client sync: the recipe types the server synchronises (none unless one asked).
        ClientRecipeSynchronizedEvent.EVENT.register((client, recipes) -> listener.accept(recipes.recipes()));
        //?} else {
        /^// Vanilla still syncs the whole recipe list, and Fabric API has no event for it: BuildCraft's client mixin on
        // the recipe packet fires once it has arrived, where NeoForge's RecipesUpdatedEvent fires.
        RECIPES_RECEIVED.add(listener);
        ^///?}
    }

    @Override
    public void onItemTooltip(ItemTooltipListener listener) {
        // Fabric's callback carries no player; the local player is the one the tooltip is built for.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
                listener.onTooltip(stack, Minecraft.getInstance().player, lines));
    }

    @Override
    public void onClientItemUseTick(ItemUseTickListener listener) {
        // Gap: Fabric API has no item-use tick event. The closest sound route is the end of each client tick, over the
        // players of the client level that are using an item, so other living entities (a drowned's trident, a
        // skeleton's bow) are not seen and the call comes after the entity ticked rather than inside its use tick.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.isPaused()) {
                return;
            }
            for (AbstractClientPlayer player : client.level.players()) {
                if (player.isUsingItem()) {
                    listener.onUseTick(player, player.getUseItem(), player.getUseItemRemainingTicks());
                }
            }
        });
    }

    @Override
    public void onTooltipPre(Consumer<BCTooltipPreContext> listener) {
        // Fabric API 26.3 has no "tooltip about to be drawn" event with its position and components (only
        // ClientTooltipComponentCallback, which maps tooltip data to components), so BuildCraft's own client mixins
        // fire it at the head of the tooltip draw, where NeoForge's RenderTooltipEvent.Pre fires.
        FabricTooltipHooks.onPre(listener);
    }

    @Override
    public void onBlockOutline(BlockOutlineOverride override) {
        //? if >=26.1 {
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
            //? if >=26.2 {
            context.submitNodeCollector().submitShapeOutline(poseStack, shape,
                    buildcraft.lib.client.render.BCLibRenderTypes.blockOutline(),
                    net.minecraft.util.ARGB.black(102), 2.5F, outline.isTranslucent());
            //?} else {
            /^// 26.1's collector has no shape outline: the same lines go in as custom geometry.
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                    buildcraft.lib.client.render.BCLibRenderTypes.blockOutline(), (pose, lines) -> {
                        PoseStack at = new PoseStack();
                        at.last().set(pose);
                        net.minecraft.client.renderer.ShapeRenderer.renderShape(at, lines, shape, 0, 0, 0,
                                net.minecraft.util.ARGB.black(102), 2.5F);
                    });
            ^///?}
            poseStack.popPose();
            return false;
        });
        //?} elif >=1.21.10 {
        /^// Fabric asks inside vanilla's outline draw, in the pass (translucent or not) the outline belongs to, so the
        // shape is drawn there at once, as NeoForge's custom outline renderer draws it on these lines.
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
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
            Vec3 cam = context.worldState().cameraRenderState.pos;
            drawOutline(context.matrices(),
                    context.consumers().getBuffer(buildcraft.lib.client.render.BCLibRenderTypes.lines()), shape,
                    pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            return false;
        });
        ^///?} else {
        /^// BLOCK_OUTLINE runs inside vanilla's outline draw for the targeted block; returning false skips vanilla's
        // outline there, as NeoForge's cancelled RenderHighlightEvent.Block does, and the shape is drawn in its place.
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null || !(mc.hitResult instanceof BlockHitResult hit) || context.consumers() == null) {
                return true;
            }
            BlockPos pos = outline.blockPos();
            VoxelShape shape = override.outline(level, pos, outline.blockState(), hit);
            if (shape == null) {
                return true;
            }
            net.minecraft.client.renderer.LevelRenderer.renderVoxelShape(context.matrixStack(),
                    context.consumers().getBuffer(buildcraft.lib.client.render.BCLibRenderTypes.lines()), shape,
                    pos.getX() - outline.cameraX(), pos.getY() - outline.cameraY(), pos.getZ() - outline.cameraZ(),
                    0.0F, 0.0F, 0.0F, 0.4F, false);
            return false;
        });
        ^///?}
    }

    //? if >=1.21.11 && <26.1 {
    /^private static void drawOutline(PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer lines,
                                    VoxelShape shape, double x, double y, double z) {
        net.minecraft.client.renderer.ShapeRenderer.renderShape(poseStack, lines, shape, x, y, z,
                net.minecraft.util.ARGB.black(102), 2.5F);
    }
    ^///?} elif >=1.21.10 && <1.21.11 {
    /^private static void drawOutline(PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer lines,
                                    VoxelShape shape, double x, double y, double z) {
        net.minecraft.client.renderer.ShapeRenderer.renderShape(poseStack, lines, shape, x, y, z,
                net.minecraft.util.ARGB.color(102, 0));
    }
    ^///?}

    @Override
    public void onClientLevelUnload(Runnable listener) {
        // Fabric has no client level-unload event: a level change (dimension change) and a disconnect are the two
        // ways a client level closes.
        //? if >=26.1 {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> listener.run());
        //?} else {
        /^ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> listener.run());
        ^///?}
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

    //? if >=26.1 {
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
        public net.minecraft.client.renderer.SubmitNodeCollector collector() {
            return context.submitNodeCollector();
        }
    }
    //?} elif >=1.21.10 {
    /^private record LevelContext(WorldRenderContext context) implements BCLevelRenderContext {
        @Override
        public PoseStack poseStack() {
            return context.matrices();
        }

        @Override
        public Vec3 cameraPos() {
            return context.worldState().cameraRenderState.pos;
        }

        @Override
        public net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource() {
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
    }
    ^///?} else {
    /^private record LevelContext(WorldRenderContext context) implements BCLevelRenderContext {
        @Override
        public PoseStack poseStack() {
            return context.matrixStack();
        }

        @Override
        public Vec3 cameraPos() {
            return context.camera().getPosition();
        }

        @Override
        public net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource() {
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
    }

    // The listeners onRecipesReceived added, run by fireRecipesReceived.
    private static final java.util.List<Consumer<Collection<RecipeHolder<?>>>> RECIPES_RECEIVED =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    // Called by the client recipe-packet mixin once vanilla has replaced the client's recipe list.
    public static void fireRecipesReceived(Collection<RecipeHolder<?>> recipes) {
        for (Consumer<Collection<RecipeHolder<?>>> listener : RECIPES_RECEIVED) {
            listener.accept(recipes);
        }
    }
    ^///?}
}
*///?}
