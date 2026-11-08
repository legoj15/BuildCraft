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
 * How the facade item sits in a hand, on the ground and in an item frame.
 *
 * <p>The item's JSON model ({@code models/item/plug_facade.json}) has {@code block/block} for its parent, whose display
 * numbers vanilla gives every block item, but {@link FacadeItemModel} replaces that model and builds its own quads, so
 * none of them reach the render state; without a transform a dropped or held facade was a full block-sized panel (issue
 * #172; 1.12.2 drew it at a quarter on the ground). These are {@code block/block}'s numbers, which on the quads
 * {@link FacadeItemModel} bakes (the slab hugs the EAST side of its cell, its plug stub on the inner side) show what
 * 1.12.2's {@code TRANSFORM_PLUG_AS_BLOCK} showed on its WEST-baked slab: in a hand the plug side faces the player. Two
 * changes:
 * <ul>
 * <li>Each translation also carries the slab's offset from the cell centre ({@code R * S * (7/16, 0, 0)} subtracted),
 * so the slab swings about the hand, the ground spot and the frame centre instead of orbiting them.</li>
 * <li>The item frame turns the slab a quarter, so its broad face (not its 2/16 edge) meets the viewer.</li>
 * </ul>
 * The inventory icon has none: it stays the full-square NORTH panel {@link FacadeItemModel} bakes for it, face on.
 * No transform for the head slot (vanilla blocks have none either), so it stays block-sized there.
 *
 * <p>The numbers belong to the quads {@link FacadeItemModel} bakes (EAST for hand, ground and frame):
 * move a bake to another side and they have to be worked out again.
 *
 * <p>Rotations are degrees, translations are blocks (1.12.2's {@code def()} took 1/16), scale is uniform.
 */
final class FacadeItemTransforms {
    private FacadeItemTransforms() {}

    private static ItemTransform of(float rotX, float rotY, float rotZ, float transX16, float transY16, float transZ16,
                                    float scale) {
        return new ItemTransform(new Vector3f(rotX, rotY, rotZ),
            new Vector3f(transX16 / 16f, transY16 / 16f, transZ16 / 16f), new Vector3f(scale, scale, scale));
    }

    private static final ItemTransform THIRD_PERSON_RIGHT = of(75, 45, 0, -1.86f, 0.71f, 0.48f, 0.375f);
    // The engine mirrors a left-hand transform (rotation Y and Z, translation X), so the slab's offset lands elsewhere
    // than for the right hand and its centring term is worked out for the mirrored pose.
    private static final ItemTransform THIRD_PERSON_LEFT = of(75, 45, 0, 1.86f, 4.29f, -0.48f, 0.375f);
    private static final ItemTransform FIRST_PERSON_RIGHT = of(0, 45, 0, -1.98f, 0, 1.98f, 0.4f);
    private static final ItemTransform FIRST_PERSON_LEFT = of(0, 225, 0, -1.98f, 0, 1.98f, 0.4f);
    private static final ItemTransform DROPPED = of(0, 0, 0, -1.75f, 3, 0, 0.25f);
    private static final ItemTransform IN_FRAME = of(0, 90, 0, 0, 0, 3.5f, 0.5f);

    static @Nullable ItemTransform forContext(ItemDisplayContext context) {
        return switch (context) {
            case THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON_RIGHT;
            case THIRD_PERSON_LEFT_HAND -> THIRD_PERSON_LEFT;
            case FIRST_PERSON_RIGHT_HAND -> FIRST_PERSON_RIGHT;
            case FIRST_PERSON_LEFT_HAND -> FIRST_PERSON_LEFT;
            case GROUND -> DROPPED;
            case FIXED -> IN_FRAME;
            default -> null;
        };
    }

    //? if >=1.21.10 {
    /** Sets {@code context}'s transform on the item layer (none for contexts without one). */
    static void apply(ItemStackRenderState.LayerRenderState layer, ItemDisplayContext context) {
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
    static void apply(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        ItemTransform transform = forContext(context);
        if (transform != null) {
            transform.apply(leftHand, pose);
        }
    }

    // What the item model reports as its transforms. Vanilla's ItemEntityRenderer reads the ground entry off the model it
    // resolved (it lifts a dropped item by 0.25 * the ground scale, and spaces a stack by it), so a model that reports none
    // floats its quarter-size panel a hand above the floor. Nothing else is listed: apply() has already put the real transform
    // on the pose, and the model apply() answers reports none, so no transform is applied twice.
    @SuppressWarnings("deprecation") // NeoForge deprecates this constructor for its modded-transforms one; vanilla (Fabric) does not
    private static ItemTransforms groundOnly() {
        ItemTransform none = ItemTransform.NO_TRANSFORM;
        return new ItemTransforms(none, none, none, none, none, none, DROPPED, none);
    }

    static final ItemTransforms GROUND_ONLY = groundOnly();*/
    //?}
}
