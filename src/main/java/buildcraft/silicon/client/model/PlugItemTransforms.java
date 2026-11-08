/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.client.model;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

//? if <1.21.10 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;*/
//?}
//? if >=1.21.10 {
import net.minecraft.client.renderer.item.ItemStackRenderState;
//?}
//? if >=26.1 {
import net.minecraft.client.resources.model.cuboid.ItemTransform;
//?} else {
/*import net.minecraft.client.renderer.block.model.ItemTransform;*/
//?}
import net.minecraft.world.item.ItemDisplayContext;

/**
 * How the gate and lens items sit in a hand, on the ground, on a head, in an item frame and on a shelf: 1.12.2's
 * {@code ModelItemSimple.TRANSFORM_PLUG_AS_ITEM} (the lens, and 1.12.2's other pluggable items) and
 * {@code TRANSFORM_PLUG_AS_ITEM_BIGGER} (the gate: every scale times 1.8), on quads baked the way 1.12.2 baked them,
 * WEST-facing (the plate against the cell's WEST side, its face looking down -X). The engine applies the rotations in
 * X, Y, Z order and mirrors the left-hand entries, exactly as 1.12.2's did, so the numbers are 1.12.2's verbatim
 * (issue #179) except in two places:
 * <ul>
 * <li>The inventory icon has none. It keeps the face-on NORTH quads its model bakes for the GUI alone, which is what
 * 1.12.2's GUI entry ({@code (0, 90, 0)}) showed, at the size the icon has always had.</li>
 * <li>The item frame turns the plug a quarter ({@code (0, -90, 0)}, where 1.12.2 had none), so its face, not its 2/16
 * edge, meets the viewer. 1.12.2's frame entry left the WEST plate edge-on.</li>
 * </ul>
 * A shelf (newer than 1.12.2) takes 1.12.2's GUI entry: it shows an item's SOUTH side, as the inventory does.
 *
 * <p>Rotations are degrees, translations are blocks (1.12.2's {@code def()} took 1/16), scale is uniform.
 */
final class PlugItemTransforms {
    /** 1.12.2's lens (and every other plug item but the gate). */
    static final PlugItemTransforms PLUG_AS_ITEM = new PlugItemTransforms(1f);
    /** 1.12.2's gate: {@link #PLUG_AS_ITEM} with every scale times 1.8. */
    static final PlugItemTransforms PLUG_AS_ITEM_BIGGER = new PlugItemTransforms(1.8f);

    private final ItemTransform thirdPersonLeft, thirdPersonRight, firstPersonLeft, firstPersonRight, head, ground,
        inFrame, onShelf;

    private PlugItemTransforms(float by) {
        thirdPersonLeft = of(75, 45, 0, 0, 2.5f, 0, 0.375f * by);
        thirdPersonRight = of(75, 225, 0, 0, 2.5f, 0, 0.375f * by);
        firstPersonLeft = of(0, 225, 0, 0, 0, -4, 0.4f * by);
        firstPersonRight = of(0, 45, 0, 0, 0, -4, 0.4f * by);
        head = of(0, 0, 0, 0, 0, 0, 1f * by);
        ground = of(0, 0, 0, 0, 3, 0, 0.5f * by);
        inFrame = of(0, -90, 0, 0, 0, 0, 0.85f * by);
        onShelf = of(0, 90, 0, 0, 0, 0, 1f * by);
        //? if <1.21.10 {
        /*groundOnly = groundOnly(ground);*/
        //?}
    }

    private static ItemTransform of(float rotX, float rotY, float rotZ, float transX16, float transY16, float transZ16,
                                    float scale) {
        return new ItemTransform(new Vector3f(rotX, rotY, rotZ),
            new Vector3f(transX16 / 16f, transY16 / 16f, transZ16 / 16f), new Vector3f(scale, scale, scale));
    }

    /** {@code context}'s transform, or null for the inventory (its own quads) and for contexts with none. */
    @Nullable ItemTransform forContext(ItemDisplayContext context) {
        return switch (context) {
            case THIRD_PERSON_LEFT_HAND -> thirdPersonLeft;
            case THIRD_PERSON_RIGHT_HAND -> thirdPersonRight;
            case FIRST_PERSON_LEFT_HAND -> firstPersonLeft;
            case FIRST_PERSON_RIGHT_HAND -> firstPersonRight;
            case HEAD -> head;
            case GROUND -> ground;
            case FIXED -> inFrame;
            //? if >=1.21.10 {
            case ON_SHELF -> onShelf;
            //?}
            default -> null;
        };
    }

    //? if >=1.21.10 {
    /** Sets {@code context}'s transform on the item layer (none for contexts without one). */
    void apply(ItemStackRenderState.LayerRenderState layer, ItemDisplayContext context) {
        ItemTransform transform = forContext(context);
        if (transform != null) {
            //? if >=26.1 {
            layer.setItemTransform(transform);
            //?} else {
            /*layer.setTransform(transform);*/
            //?}
        }
    }
    //?} else {
    /*/^* Applies {@code context}'s transform to {@code pose}, where vanilla would apply a model's own (none for contexts without one). ^/
    void apply(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        ItemTransform transform = forContext(context);
        if (transform != null) {
            transform.apply(leftHand, pose);
        }
    }

    // What the item model reports as its transforms. Vanilla's ItemEntityRenderer reads the ground entry off the model it
    // resolved (it lifts a dropped item by 0.25 * the ground scale, and spaces a stack by it). Nothing else is listed:
    // apply() has already put the real transform on the pose, and the model applyTransform answers reports none, so no
    // transform is applied twice.
    @SuppressWarnings("deprecation") // NeoForge deprecates this constructor for its modded-transforms one; vanilla (Fabric) does not
    private static ItemTransforms groundOnly(ItemTransform ground) {
        ItemTransform none = ItemTransform.NO_TRANSFORM;
        return new ItemTransforms(none, none, none, none, none, none, ground, none);
    }

    final ItemTransforms groundOnly;*/
    //?}
}
