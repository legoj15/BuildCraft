/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// Directive site 1 of 4 — imports. Three-way rather than two-way for exactly one reason:
// CameraRenderState moved package at 26.1 (net.minecraft.client.renderer.state -> ...state.level).
// Everything else here is the plain "render-state model exists from 1.21.10" split.
//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} elif >=1.21.10 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;*/
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;*/
//?}

import buildcraft.api.mj.MjAPI;
import buildcraft.api.robots.EntityRobotBase;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.render.BCLibRenderTypes;
import buildcraft.lib.client.render.LightUtil;
import buildcraft.lib.client.render.laser.LaserData_BC8;
import buildcraft.lib.client.render.laser.LaserRenderer_BC8;

import buildcraft.robotics.entity.EntityRobot;

/**
 * Entity renderer for the robot: one 8&times;8&times;8-unit cube at 1/16 scale, three texture passes over
 * it, the held item out front, and the four transfer slots floating at the cube's corners.
 *
 * <p><b>Two renderer generations, not three.</b> 1.21.1 uses the classic
 * {@code EntityRenderer<T>} + {@code render(...)} + {@code getTextureLocation(...)}; 1.21.10, 1.21.11,
 * 26.1 and 26.2 all use the render-state model
 * ({@code createRenderState}/{@code extractRenderState}/{@code submit}) and are byte-for-byte identical
 * to each other for entity rendering. Only four things fork in this file: the imports (three-way, because
 * {@code CameraRenderState} changed package at 26.1), the class declaration, the block of
 * generation-specific methods, and the laser wrapper (which forks at <b>26.1</b>, not 1.21.10 — 26.1 is
 * where immediate-mode rendering was removed).
 *
 * <p><b>Geometry provenance.</b> The UV net is 8.0.x's, not 7.1.x's: the robot skins shipped in this repo
 * are 8.0.x's 32&times;32 re-cut of 7.1.x's 64&times;32 originals, so 7.1.x UV maths does not fit them.
 * Faces are emitted straight through {@link ModelUtil#createFace} with <b>raw 0..1 UVs</b> — these are
 * standalone entity textures bound as whole files, never atlas sprites, so there is no sprite
 * interpolation to apply (and {@code SpriteHolderRegistry} must not be used on them).
 *
 * <p><b>Render types.</b> Never call vanilla's {@code entityCutout} directly: it culls back faces on
 * &le;1.21.11 but does <em>not</em> on 26.x. {@link BCLibRenderTypes#entityCutoutCull} is the
 * cross-node-correct culled cutout and carries the opaque passes.
 * {@link BCLibRenderTypes#entityTranslucent} carries the charge overlay, which needs real alpha blending
 * (a cutout pipeline has no blend function, so a fractional vertex alpha there would either be fully
 * opaque or fully discarded — the charge bar would stop reading as a charge bar). {@code entityTranslucent}
 * happens to be the no-cull variant on every node, so it has no inversion trap of its own; its back faces
 * are simply depth-rejected behind the opaque cube drawn immediately before it.
 *
 * <p><b>No shadow.</b> Neither 7.1.x nor 8.0.x ever gave the robot one, so {@code shadowRadius} is left
 * at the base class's 0 on every node.
 */
//? if >=1.21.10 {
public class RenderRobot extends EntityRenderer<EntityRobot, RobotRenderState> {
//?} else {
/*public class RenderRobot extends EntityRenderer<EntityRobot> {*/
//?}

    // ── Textures ────────────────────────────────────────────────────────────
    // Entity textures are direct file paths — full "textures/..." prefix and ".png" suffix — bound as
    // whole textures by the render type. They are NOT atlas sprites, so SpriteHolderRegistry does not
    // apply (misusing it here fails silently to the missing-texture checkerboard). Written in the
    // canonical Identifier form; the build rewrites it to ResourceLocation on <1.21.11.

    /** Fallback skin, used whenever the robot's board id resolves to no texture (all of Ph3). */
    private static final Identifier TEX_BASE =
        Identifier.parse("buildcraftunofficial:textures/entity/robot_base.png");
    /** Charge indicator — drawn at vertex alpha = charge fraction, fullbright. */
    private static final Identifier TEX_OVERLAY_SIDE =
        Identifier.parse("buildcraftunofficial:textures/entity/overlay_side.png");
    /** Always-on "powered" decal — opaque, fullbright. */
    private static final Identifier TEX_OVERLAY_BOTTOM =
        Identifier.parse("buildcraftunofficial:textures/entity/overlay_bottom.png");

    // ── Geometry ────────────────────────────────────────────────────────────

    /** One cube, centred on the entity's own origin. */
    private static final Vector3f CUBE_CENTRE = new Vector3f(0, 0, 0);
    /** Half-extent: 4 texels at 1/16 scale, i.e. a 0.5-block cube spanning -0.25..+0.25 on each axis —
     *  the same box as the entity's synthetic pick box, so what you see is what you can click. */
    private static final Vector3f CUBE_RADIUS = new Vector3f(4 / 16F, 4 / 16F, 4 / 16F);

    private static final Direction[] FACES = Direction.values();

    /**
     * The 8.0.x skin net, in 32&times;32 texels, indexed by {@link Direction#ordinal()}. Identical to
     * 8.0.x's {@code models/robot.json} (whose numbers are the same net expressed in model-JSON 0..16
     * UV units) — which is why {@code models/item/robot.json} can reuse it verbatim.
     */
    private static final ModelUtil.UvFaceData[] UVS = new ModelUtil.UvFaceData[FACES.length];

    static {
        UVS[Direction.UP.ordinal()] = texels(16, 0, 24, 8);
        UVS[Direction.DOWN.ordinal()] = texels(8, 0, 16, 8);
        UVS[Direction.NORTH.ordinal()] = texels(8, 8, 16, 16);
        UVS[Direction.SOUTH.ordinal()] = texels(24, 8, 32, 16);
        UVS[Direction.WEST.ordinal()] = texels(16, 8, 24, 16);
        UVS[Direction.EAST.ordinal()] = texels(0, 8, 8, 16);
    }

    /** Corner offsets (x, z) for the four transfer slots, in 8.0.x's slot order. */
    private static final float[][] SLOT_OFFSETS = {
        { -0.125F, -0.125F }, { +0.125F, -0.125F }, { +0.125F, +0.125F }, { -0.125F, +0.125F }
    };

    /** Height the slot items float at, and the scale they are drawn at (8.0.x values). */
    private static final float SLOT_Y = 0.28F;
    private static final float SLOT_SCALE = 0.5F;

    /** The active-use spin cycles through this many degrees... */
    private static final float SPIN_CYCLE_DEGREES = 45F;
    /** ...at this rate. 8.0.x advanced it by one degree per 10 ms of wall clock, i.e. 5 degrees per
     *  tick; driving it off {@code tickCount + partialTick} instead keeps it frame-rate independent
     *  and — unlike 8.0.x — does not mutate entity state from inside the renderer. */
    private static final float SPIN_DEGREES_PER_TICK = 5F;

    // ── Shared helpers (node-neutral) ───────────────────────────────────────

    private static ModelUtil.UvFaceData texels(int u0, int v0, int u1, int v1) {
        return new ModelUtil.UvFaceData(u0 / 32F, v0 / 32F, u1 / 32F, v1 / 32F);
    }

    /** Emits the six faces of the robot cube. Called from inside the deferred custom-geometry lambda on
     *  the modern path, so it must only touch the {@code pose} snapshot it is handed and its own
     *  primitive arguments — never the caller's live {@code PoseStack}. */
    private static void emitCube(PoseStack.Pose pose, VertexConsumer buffer, int light,
                                 float red, float green, float blue, float alpha) {
        for (Direction face : FACES) {
            ModelUtil.createFace(face, CUBE_CENTRE, CUBE_RADIUS, UVS[face.ordinal()])
                .lighti(light)
                .colourf(red, green, blue, alpha)
                .render(pose, buffer);
        }
    }

    private static RenderType cubeType(Identifier texture, boolean translucent) {
        return translucent
            ? BCLibRenderTypes.entityTranslucent(texture)
            : BCLibRenderTypes.entityCutoutCull(texture);
    }

    /** Stored charge as a 0..1 fraction. Reads {@code MAX_POWER} rather than hard-coding 10 000 so a
     *  future capacity re-pin keeps the bar honest. */
    private static float chargeFraction(EntityRobot robot) {
        long maxMj = EntityRobotBase.MAX_POWER / MjAPI.MJ;
        if (maxMj <= 0) return 0F;
        return Mth.clamp(robot.getEnergyMj() / (float) maxMj, 0F, 1F);
    }

    /** Degrees of extra spin for an actively-used held item, cycling 0..45. */
    private static float activeSpin(float ageInTicks) {
        return (ageInTicks * SPIN_DEGREES_PER_TICK) % SPIN_CYCLE_DEGREES;
    }

    /**
     * The three cube passes, in 8.0.x's order: base skin, then — unless the robot is asleep — the
     * charge overlay at vertex-alpha and the always-on bottom decal, both fullbright.
     *
     * <p>{@code sink} is the node's draw target, hidden behind {@code Object} so this body stays shared:
     * a {@code SubmitNodeCollector} on 1.21.10+, a {@code MultiBufferSource} on 1.21.1. See
     * {@link #cube}.
     */
    private static void drawBody(PoseStack poseStack, Object sink, Identifier skin, float charge,
                                 boolean sleeping, int hurtTime, int light) {
        poseStack.pushPose();

        float red = 1F;
        float green = 1F;
        float blue = 1F;
        if (hurtTime > 0) {
            // 8.0.x's damage flash: a red tint plus a wobble so small it reads as a shudder rather
            // than a spin (0.01 degrees per remaining hurt tick).
            green = 0.6F;
            blue = 0.6F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(hurtTime * 0.01F));
        }

        cube(poseStack, sink, skin, false, light, red, green, blue, 1F);

        if (!sleeping) {
            cube(poseStack, sink, TEX_OVERLAY_SIDE, true, LightUtil.FULL_BRIGHT, 1F, 1F, 1F, charge);
            cube(poseStack, sink, TEX_OVERLAY_BOTTOM, false, LightUtil.FULL_BRIGHT, 1F, 1F, 1F, 1F);
        }

        poseStack.popPose();
    }

    /** Positions the held item: aim pitch, then the active-use spin, then 8.0.x's fixed offset. */
    private static void applyHeldItemTransform(PoseStack poseStack, float aimPitch, float spin) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(aimPitch));
        if (spin != 0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        }
        poseStack.translate(-0.4F, 0, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-45F + 180F));
        poseStack.scale(0.8F, 0.8F, 0.8F);
    }

    /** Positions one of the four transfer-slot items at its cube corner. */
    private static void applySlotTransform(PoseStack poseStack, int slot) {
        poseStack.translate(SLOT_OFFSETS[slot][0], SLOT_Y, SLOT_OFFSETS[slot][1]);
        poseStack.scale(SLOT_SCALE, SLOT_SCALE, SLOT_SCALE);
    }

    /**
     * Draws the robot's laser beam. <b>Dormant for the whole of Ph3</b> and deliberately kept wired: the
     * 7.1.x laser DataWatcher slots are ported as API only (the three setters exist on
     * {@code EntityRobot} for addon surface and NBT-shape stability, and nothing reads them yet), so
     * {@code laserVisible} is currently always false. The draw path is written and compiles on all five
     * nodes — including the 26.1 collector fork — so landing the real laser state is a one-line change to
     * where the flag comes from.
     *
     * <p>The beam is emitted in <b>entity-local</b> coordinates with a zero camera position, because the
     * pose handed to an entity renderer is already translated to the entity. One consequence to fix when
     * this goes live: {@code LaserRenderer_BC8} samples world light from the coordinates it is given, so
     * a local-space beam samples light near the world origin. Give the laser its own fullbright light, or
     * hand it world coordinates plus the real camera position, at that point.
     */
    private static void drawLaser(PoseStack poseStack, Object sink, boolean visible, Vec3 localEnd) {
        if (!visible) return;
        laser(poseStack, new LaserData_BC8(BuildCraftLaserManager.POWER_MED, Vec3.ZERO, localEnd, 1 / 16D),
            sink);
    }

    // Directive site 4 of 4 — the laser wrapper. LaserRenderer_BC8.renderLaserStatic forks at 26.1 (where
    // immediate-mode rendering was removed), NOT at 1.21.10 — so on 1.21.10/1.21.11 the "modern" renderer
    // above must still call the immediate-mode arity. The trailing argument is typed Object below 26.1 so
    // every call site reads the same; it carries the real SubmitNodeCollector on 26.1+ and is ignored
    // otherwise (same idiom as BCBuildersEventDist's laser/laserBox wrappers).
    //? if >=26.1 {
    private static void laser(PoseStack poseStack, LaserData_BC8 data, Object sink) {
        LaserRenderer_BC8.renderLaserStatic(poseStack, data, Vec3.ZERO, (SubmitNodeCollector) sink);
    }
    //?} else {
    /*private static void laser(PoseStack poseStack, LaserData_BC8 data, Object sink) {
        LaserRenderer_BC8.renderLaserStatic(poseStack, data, Vec3.ZERO);
    }*/
    //?}

    // Directive site 2 of 4 was the class declaration above. Directive site 3 of 4 follows: everything
    // that differs between the two renderer generations — the item-model seam, the constructor, the
    // entry points, and the one-line cube sink — lives in this single block so the rest of the file
    // stays node-neutral.
    //? if >=1.21.10 {

    /** Resolves item stacks to models during {@code extractRenderState}, exactly as the vanilla
     *  entity/block-entity renderers do. The 1.21.1 branch has no equivalent — it renders stacks
     *  directly through {@code ItemRenderer} at draw time. */
    private final ItemModelResolver itemModelResolver;

    public RenderRobot(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public RobotRenderState createRenderState() {
        return new RobotRenderState();
    }

    @Override
    public void extractRenderState(EntityRobot robot, RobotRenderState state, float partialTick) {
        super.extractRenderState(robot, state, partialTick);

        // Every field below is written unconditionally: the state object is reused frame after frame,
        // so anything left alone would silently keep a stale value.
        Identifier skin = robot.getTexture();
        state.skin = skin != null ? skin : TEX_BASE;
        state.chargeFraction = chargeFraction(robot);
        state.sleeping = robot.isSleepingClient();
        state.hurtTime = robot.getHurtTime();
        state.bodyYaw = Mth.rotLerp(partialTick, robot.yRotO, robot.getYRot());
        state.aimPitch = robot.getAimPitch();
        state.itemSpin = robot.isItemActive() ? activeSpin(robot.tickCount + partialTick) : 0F;

        Level level = robot.level();
        int seed = robot.getId();
        this.itemModelResolver.updateForTopItem(state.heldItem, orEmpty(robot.getHeldItem()),
            ItemDisplayContext.NONE, level, null, seed);
        for (int slot = 0; slot < state.inventory.length; slot++) {
            ItemStack stack = slot < robot.getInventorySize()
                ? orEmpty(robot.getInventoryStack(slot))
                : ItemStack.EMPTY;
            this.itemModelResolver.updateForTopItem(state.inventory[slot], stack,
                ItemDisplayContext.NONE, level, null, seed + 1 + slot);
        }

        state.laserVisible = false;
        state.laserEnd = Vec3.ZERO;
    }

    @Override
    public void submit(RobotRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);

        // Drawn before the body yaw is applied: the destination is a world-axis offset, not a
        // body-relative one.
        drawLaser(poseStack, collector, state.laserVisible, state.laserEnd);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.bodyYaw));

        for (int slot = 0; slot < state.inventory.length; slot++) {
            if (state.inventory[slot].isEmpty()) continue;
            poseStack.pushPose();
            applySlotTransform(poseStack, slot);
            state.inventory[slot].submit(poseStack, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (!state.heldItem.isEmpty()) {
            poseStack.pushPose();
            applyHeldItemTransform(poseStack, state.aimPitch, state.itemSpin);
            state.heldItem.submit(poseStack, collector, state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        drawBody(poseStack, collector, state.skin, state.chargeFraction, state.sleeping,
            state.hurtTime, state.lightCoords);

        poseStack.popPose();
    }

    /** Queues one textured pass over the cube. The collector snapshots {@code poseStack.last()} at
     *  submit time, so the caller is free to pop afterwards. */
    private static void cube(PoseStack poseStack, Object sink, Identifier texture, boolean translucent,
                             int light, float red, float green, float blue, float alpha) {
        ((SubmitNodeCollector) sink).submitCustomGeometry(poseStack, cubeType(texture, translucent),
            (pose, buffer) -> emitCube(pose, buffer, light, red, green, blue, alpha));
    }

    private static ItemStack orEmpty(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }
    //?} else {
    /*// 1.21.1: the classic single-method renderer. There is no render state and no item-model
    // pre-resolution, so what extractRenderState computes above is computed inline here and stacks go
    // straight through ItemRenderer.renderStatic. getTextureLocation is abstract on this line and must
    // be implemented even though every pass binds its own texture through its render type.
    public RenderRobot(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(EntityRobot robot) {
        Identifier skin = robot.getTexture();
        return skin != null ? skin : TEX_BASE;
    }

    @Override
    public void render(EntityRobot robot, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(robot, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        Identifier skin = getTextureLocation(robot);
        float bodyYaw = Mth.rotLerp(partialTick, robot.yRotO, robot.getYRot());
        float spin = robot.isItemActive() ? activeSpin(robot.tickCount + partialTick) : 0F;

        // The laser is dormant in Ph3 (see drawLaser); passing false keeps the call site wired without
        // reading state EntityRobot does not expose yet.
        drawLaser(poseStack, bufferSource, false, Vec3.ZERO);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        Level level = robot.level();
        int seed = robot.getId();

        for (int slot = 0; slot < SLOT_OFFSETS.length && slot < robot.getInventorySize(); slot++) {
            ItemStack stack = robot.getInventoryStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            poseStack.pushPose();
            applySlotTransform(poseStack, slot);
            itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, seed + 1 + slot);
            poseStack.popPose();
        }

        ItemStack held = robot.getHeldItem();
        if (held != null && !held.isEmpty()) {
            poseStack.pushPose();
            applyHeldItemTransform(poseStack, robot.getAimPitch(), spin);
            itemRenderer.renderStatic(held, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, seed);
            poseStack.popPose();
        }

        drawBody(poseStack, bufferSource, skin, chargeFraction(robot), robot.isSleepingClient(),
            robot.getHurtTime(), packedLight);

        poseStack.popPose();
    }

    // Immediate-mode cube pass. The buffer is fetched from the MultiBufferSource the engine handed us
    // and flushed by the engine at the end of the entity batch, so there is no endBatch() here.
    private static void cube(PoseStack poseStack, Object sink, Identifier texture, boolean translucent,
                             int light, float red, float green, float blue, float alpha) {
        VertexConsumer buffer = ((MultiBufferSource) sink).getBuffer(cubeType(texture, translucent));
        emitCube(poseStack.last(), buffer, light, red, green, blue, alpha);
    }*/
    //?}
}
