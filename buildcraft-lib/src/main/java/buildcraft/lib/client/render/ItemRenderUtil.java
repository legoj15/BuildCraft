/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.math.Axis;

/**
 * Utility for rendering item stacks inside pipes as 3D models.
 * Uses NeoForge 1.21.11 ItemModelResolver + ItemStackRenderState.submit() API.
 */
public class ItemRenderUtil {

    private static final Random modelOffsetRandom = new Random(0);
    private static final ItemStackRenderState renderState = new ItemStackRenderState();

    // Batch state — set by beginItemBatch, used by renderItemStack
    private static PoseStack currentPoseStack;
    private static SubmitNodeCollector currentCollector;
    private static int currentLight;

    /** Begin a batch of item renders. Must be called before renderItemStack. */
    public static void beginItemBatch(PoseStack poseStack, SubmitNodeCollector collector, int light) {
        currentPoseStack = poseStack;
        currentCollector = collector;
        currentLight = light;
    }

    /** Returns the current PoseStack set by beginItemBatch, or null if not in a batch.
     *  Used by overlay renderers (e.g. colour boxes) that need PoseStack transforms. */
    public static PoseStack getCurrentPoseStack() {
        return currentPoseStack;
    }

    /**
     * Renders a single item stack at the given local coordinates.
     * Must call beginItemBatch() first.
     */
    public static void renderItemStack(double x, double y, double z, ItemStack stack,
            int lightc, Direction dir, com.mojang.blaze3d.vertex.VertexConsumer bb) {
        renderItemStack(x, y, z, stack, stack.getCount(), lightc, dir, bb);
    }

    /**
     * Renders a single item stack at the given local coordinates.
     * Must call beginItemBatch() first.
     */
    public static void renderItemStack(double x, double y, double z, ItemStack stack,
            int stackCount, int lightc, Direction dir, com.mojang.blaze3d.vertex.VertexConsumer bb) {
        if (stack.isEmpty() || currentPoseStack == null || currentCollector == null) {
            return;
        }
        if (dir == null) {
            dir = Direction.EAST;
        }

        // Populate the render state from the ItemStack
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        renderState.clear();
        resolver.updateForTopItem(renderState, stack, ItemDisplayContext.FIXED,
                Minecraft.getInstance().level, null, 0);

        if (renderState.isEmpty()) {
            return;
        }

        final int itemModelCount = getStackModelCount(stackCount);
        if (itemModelCount > 1) {
            setupModelOffsetRandom(stack);
        }

        for (int i = 0; i < itemModelCount; i++) {
            currentPoseStack.pushPose();

            // Translate to item position
            float dx = 0, dy = 0, dz = 0;
            if (i > 0) {
                dx = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                dy = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
                dz = (modelOffsetRandom.nextFloat() * 2.0F - 1.0F) * 0.08F;
            }

            currentPoseStack.translate(x + dx, y + dy, z + dz);

            // Scale to pipe item size (FIXED display applies ~0.5x, so 0.60 * 0.5 ≈ 0.30 matching 1.12.2)
            currentPoseStack.scale(0.60f, 0.60f, 0.60f);

            // Rotate to face the travel direction
            applyDirectionRotation(currentPoseStack, dir);

            // Render the item using the new 1.21.11 API
            renderState.submit(currentPoseStack, currentCollector,
                    lightc, OverlayTexture.NO_OVERLAY, 0);

            currentPoseStack.popPose();
        }
    }

    /** Applies rotation so the item faces the given direction of travel. */
    private static void applyDirectionRotation(PoseStack ps, Direction dir) {
        // Default facing is SOUTH (toward +Z). Rotate to match travel direction.
        switch (dir) {
            case NORTH -> ps.mulPose(Axis.YP.rotationDegrees(180));
            case EAST -> ps.mulPose(Axis.YP.rotationDegrees(90));
            case WEST -> ps.mulPose(Axis.YP.rotationDegrees(-90));
            case UP -> ps.mulPose(Axis.XP.rotationDegrees(-90));
            case DOWN -> ps.mulPose(Axis.XP.rotationDegrees(90));
            case SOUTH -> {} // default orientation
        }
    }

    private static void setupModelOffsetRandom(ItemStack stack) {
        final long seed;
        if (stack.isEmpty()) {
            seed = 137;
        } else {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            seed = key != null ? (key.hashCode() & 0x7F_FF_FF_FFL) : 127L;
        }
        modelOffsetRandom.setSeed(seed);
    }

    private static int getStackModelCount(int stackCount) {
        if (stackCount > 48) return 5;
        if (stackCount > 32) return 4;
        if (stackCount > 16) return 3;
        if (stackCount > 1) return 2;
        return 1;
    }

    /** Clears the batch state. */
    public static void endItemBatch() {
        currentPoseStack = null;
        currentCollector = null;
    }
}
