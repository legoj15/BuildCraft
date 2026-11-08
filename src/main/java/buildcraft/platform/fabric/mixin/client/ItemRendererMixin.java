/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.client.ClassicItemModel;
import buildcraft.platform.client.ClassicItemRenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
^///?}
//? if >=1.21.10 && <1.21.11 {
/^import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.ARGB;
^///?}

/^*
 * The item-draw patches NeoForge ships and Fabric's vanilla lacks, one per line that needs one.
 * <p>
 * 1.21.1: NeoForge's two item-model hooks on {@code ItemRenderer.render}: a {@link ClassicItemModel} applies its own
 * transform and picks the model to draw in place of vanilla's item transforms ({@code applyTransform}), and a
 * {@link ClassicItemRenderType} names its sheet in place of the one vanilla picks for the stack
 * ({@code getRenderTypes}; BuildCraft's name one, so its first is the one NeoForge would draw). A third patch of
 * NeoForge's is carried for BuildCraft's {@link ClassicItemRenderType} models only: their quads draw with their own
 * vertex colours (the coloured lens glass), baked normals (what their shading is set by) and baked light, all of which
 * vanilla's item draw drops. Every other model is drawn as vanilla draws it.
 * <p>
 * 1.21.10: the same third patch, for every item quad, as NeoForge applies it. A 1.21.10 quad still keeps its colour in
 * its own vertex data, but the item draw ({@code ItemRenderer.renderQuadList}) goes through vanilla's eight-argument
 * {@code putBulkData}, which reads the tint and ignores that colour: BuildCraft's lens, filter and clear lens draw as
 * their greyscale glass textures, and the gate and facade lose theirs. Vanilla bakes every quad white, fully opaque,
 * with no light and no normal, so a quad that sets none of them draws as vanilla draws it.
 * <p>
 * 1.21.11 and later carry nothing: a 1.21.11 quad has no colour slot at all (BuildCraft's ride in a side table and
 * reach the draw through the layer's Fabric mesh, {@code ClientModels#setItemQuads}, so no coloured quad reaches the
 * vanilla draw to be patched) and 26.x has no {@code ItemRenderer}. It is an empty mixin on {@code Minecraft} there.
 *^/
//? if <1.21.11 {
/^@Mixin(ItemRenderer.class)
^///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
//?}
abstract class ItemRendererMixin {

    //? if <1.21.10 {
    /^// Inside the item's pose push, where NeoForge's handleCameraTransforms replaces the transform; vanilla then applies
    // the returned model's own transforms (empty on BuildCraft's per-context models; the chosen view's on a
    // separate-transforms model, which applies none itself).
    @ModifyVariable(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER), argsOnly = true)
    private BakedModel buildcraft$applyTransform(BakedModel model,
            @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) boolean leftHand,
            @Local(argsOnly = true) PoseStack pose) {
        return model instanceof ClassicItemModel classic ? classic.applyTransform(context, pose, leftHand) : model;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderType(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType buildcraft$renderType(ItemStack stack, boolean fabulous, Operation<RenderType> original,
            @Local(argsOnly = true) BakedModel model) {
        return model instanceof ClassicItemRenderType classic ? classic.getRenderTypes(stack, fabulous).get(0)
                : original.call(stack, fabulous);
    }

    // Whether the model renderModelLists is drawing is one of BuildCraft's (render thread only).
    @Unique
    private boolean buildcraft$readQuadColours;

    @Inject(method = "renderModelLists", at = @At("HEAD"))
    private void buildcraft$startModel(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose,
            VertexConsumer consumer, CallbackInfo ci) {
        buildcraft$readQuadColours = model instanceof ClassicItemRenderType;
    }

    @Inject(method = "renderModelLists", at = @At("RETURN"))
    private void buildcraft$endModel(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose,
            VertexConsumer consumer, CallbackInfo ci) {
        buildcraft$readQuadColours = false;
    }

    // NeoForge's item draw for BuildCraft's quads (its patched VertexConsumer.putBulkData, readExistingColor=true).
    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V"))
    private void buildcraft$quadColours(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float red,
            float green, float blue, float alpha, int light, int overlay, Operation<Void> original) {
        if (buildcraft$readQuadColours) {
            buildcraft$putQuad(consumer, pose, quad, red, green, blue, alpha, light, overlay);
        } else {
            original.call(consumer, pose, quad, red, green, blue, alpha, light, overlay);
        }
    }

    // One quad as NeoForge 1.21.1 draws it: each vertex's own colour and alpha times the tint, its baked normal (when it
    // has one) in place of the face's, and its baked light where that is brighter.
    @Unique
    private static void buildcraft$putQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float red,
            float green, float blue, float alpha, int light, int overlay) {
        int[] vertices = quad.getVertices();
        Vec3i face = quad.getDirection().getNormal();
        Vector3f normal = pose.transformNormal(face.getX(), face.getY(), face.getZ(), new Vector3f());
        Vector3f position = new Vector3f();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer ints = data.asIntBuffer();
            for (int v = 0; v < vertices.length / 8; v++) {
                ints.clear();
                ints.put(vertices, v * 8, 8);
                int colour = FastColor.ARGB32.color((int) (alpha * (data.get(15) & 255)),
                        (int) ((data.get(12) & 255) * red), (int) ((data.get(13) & 255) * green),
                        (int) ((data.get(14) & 255) * blue));
                int blockLight = Math.max(light & 0xFFFF, Short.toUnsignedInt(data.getShort(24)));
                int skyLight = Math.max(light >>> 16, Short.toUnsignedInt(data.getShort(26)));
                byte nx = data.get(28);
                byte ny = data.get(29);
                byte nz = data.get(30);
                if (nx != 0 || ny != 0 || nz != 0) {
                    normal.set(nx / 127f, ny / 127f, nz / 127f).mul(pose.normal());
                }
                pose.pose().transformPosition(data.getFloat(0), data.getFloat(4), data.getFloat(8), position);
                consumer.addVertex(position.x(), position.y(), position.z(), colour, data.getFloat(16),
                        data.getFloat(20), overlay, blockLight | skyLight << 16, normal.x(), normal.y(), normal.z());
            }
        }
    }
    ^///?}

    //? if >=1.21.10 && <1.21.11 {
    /^// NeoForge's item draw on this line: its patched ten-argument putBulkData with readExistingColor=true, which is what
    // renderQuadList asks for there. Vanilla's eight-argument overload that renderQuadList calls passes false, so the
    // vertex colours BuildCraft bakes into its quads (the lens glass) never reach the vertex.
    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFII)V"))
    private static void buildcraft$putQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, float red,
            float green, float blue, float alpha, int light, int overlay, Operation<Void> original) {
        int[] vertices = quad.vertices();
        // Each vertex's own colour and alpha times the tint, its baked normal (when it has one) in place of the face's,
        // and its baked light where that is brighter than the item's (after the quad's own emission).
        Vector3f normal = pose.transformNormal(quad.direction().getUnitVec3f(), new Vector3f());
        Vector3f position = new Vector3f();
        int itemLight = LightTexture.lightCoordsWithEmission(light, quad.lightEmission());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer ints = data.asIntBuffer();
            for (int v = 0; v < vertices.length / 8; v++) {
                ints.clear();
                ints.put(vertices, v * 8, 8);
                int colour = ARGB.color((int) (alpha * (data.get(15) & 255)),
                        (int) ((data.get(12) & 255) * red), (int) ((data.get(13) & 255) * green),
                        (int) ((data.get(14) & 255) * blue));
                int blockLight = Math.max(itemLight & 0xFFFF, Short.toUnsignedInt(data.getShort(24)));
                int skyLight = Math.max(itemLight >>> 16, Short.toUnsignedInt(data.getShort(26)));
                byte nx = data.get(28);
                byte ny = data.get(29);
                byte nz = data.get(30);
                if (nx != 0 || ny != 0 || nz != 0) {
                    normal.set(nx / 127f, ny / 127f, nz / 127f).mul(pose.normal());
                }
                pose.pose().transformPosition(data.getFloat(0), data.getFloat(4), data.getFloat(8), position);
                consumer.addVertex(position.x(), position.y(), position.z(), colour, data.getFloat(16),
                        data.getFloat(20), overlay, blockLight | skyLight << 16, normal.x(), normal.y(), normal.z());
            }
        }
    }
    ^///?}
}
*///?}
