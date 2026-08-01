/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.render;

// Whole-file >=1.21.10: the render-state model (createRenderState/extractRenderState/submit) arrived at
// 1.21.10. On 1.21.1 RenderRobot still uses the classic render(...) path and never constructs this, so the
// file collapses to a bare package declaration there rather than carrying a dead stub class.
//? if >=1.21.10 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Per-frame snapshot of everything {@link RenderRobot} draws.
 *
 * <p><b>This class may only ADD fields.</b> {@code EntityRenderState} already declares
 * {@code entityType, x, y, z, ageInTicks, boundingBoxWidth, boundingBoxHeight, eyeHeight,
 * distanceToCameraSq, isInvisible, isDiscrete, displayFireAnimation, lightCoords, outlineColor,
 * passengerOffset, nameTag, nameTagAttachment, leashStates, shadowRadius, shadowPieces, partialTick} on
 * every node, plus {@code hitboxesRenderState}/{@code serverHitboxesRenderState} on 1.21.10 and
 * {@code scoreText} on 26.2 — re-declaring any of them here would shadow the populated inherited field
 * behind static-typed access and read null/zero. A 26.2-only name is the nastiest case: it compiles clean
 * on four nodes and only misbehaves on the fifth. Method names {@code getRenderData}/{@code setRenderData}
 * are likewise reserved (NeoForge's {@code BaseRenderState}).
 *
 * <p>The engine creates one of these per renderer and re-fills it every frame, so <b>every field below
 * must be written unconditionally</b> in {@code RenderRobot.extractRenderState} — a field left untouched
 * keeps last frame's value (or another robot's).
 */
public class RobotRenderState extends EntityRenderState {

    /** The board-resolved base skin, already defaulted to {@code robot_base.png} when the board id
     *  resolves to nothing. Never null once extracted. */
    public Identifier skin;

    /** Stored energy as a 0..1 fraction of {@code MAX_POWER}; drives the side overlay's vertex alpha. */
    public float chargeFraction;

    /** True while the robot is shut down AND not taking a charge — suppresses both overlays. */
    public boolean sleeping;

    /** Ticks left on the damage flash; drives the red tint and the small Z wobble. */
    public int hurtTime;

    /** Body yaw in degrees, already shortest-arc interpolated between the previous and current tick. */
    public float bodyYaw;

    /** Aim pitch in degrees, applied to the held item only (the cube itself does not pitch). */
    public float aimPitch;

    /** The extra spin applied to an actively-used held item, in degrees, cycling 0..45. */
    public float itemSpin;

    /** Pre-resolved model for the stack held out in front (7.1.x {@code itemInUse}). Cleared to empty
     *  when there is nothing held — check {@code isEmpty()} rather than a separate flag. */
    public final ItemStackRenderState heldItem = new ItemStackRenderState();

    /** Pre-resolved models for the four transfer slots, drawn as small floating items at the cube
     *  corners. Same emptiness convention as {@link #heldItem}. */
    public final ItemStackRenderState[] inventory = {
        new ItemStackRenderState(), new ItemStackRenderState(),
        new ItemStackRenderState(), new ItemStackRenderState()
    };

    /** Laser beam visibility. Dormant for the whole of Ph3 — see {@code RenderRobot.extractRenderState}. */
    public boolean laserVisible;

    /** Laser beam destination, <b>entity-local</b> (i.e. relative to the robot's own origin). */
    public Vec3 laserEnd = Vec3.ZERO;
}
//?}
