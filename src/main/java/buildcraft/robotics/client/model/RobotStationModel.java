/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.model;

import org.joml.Vector3f;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import buildcraft.lib.client.model.ModelUtil;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.misc.SpriteUtil;

/**
 * The docking station's 1.7.10 pedestal — an 8×8×1px plate hugging the pipe hull plus a ~2.2px-square
 * post running from the plate out to the block edge, WEST-canonical to match
 * {@code PlugBakerSimple}'s rotation convention.
 *
 * <p>This is the single geometry source for BOTH render paths: the chunk-baked idle pedestal and
 * {@code PlugRobotStationRenderer}'s per-frame re-emit for the reserved/linked states. Sharing it is
 * not tidiness — the state sprites carry one asymmetric texel, so a mirror or flip mismatch between
 * the two paths would make the whole pedestal visibly jump the instant a robot reserved the station.
 * {@code RobotStationModelTester} pins the numbers against 7.1.x.
 *
 * <p>UVs come from {@link ModelUtil#mapBoxToUvs}, which is the same world-axis box projection
 * 1.7.10's {@code RenderBlocks.renderStandardBlock} applied. 1.7.10 projected AFTER rotating each box
 * to its side, so its six sides showed six texture orientations; rotating the quads with their UVs
 * attached gives one orientation everywhere, which is invisible on a sprite this symmetric and avoids
 * six baked variants.
 */
public class RobotStationModel {
    private RobotStationModel() {}

    /** 7.1.x's {@code zFightOffset}, applied to each box's hull-ward extent. The plate therefore ends
     *  at 4/16 + ZF, i.e. poking very slightly THROUGH the pipe hull rather than sitting coplanar with
     *  it — the same trick {@code blocker.json}'s 4.01 uses. The hull has glass windows, so that face
     *  is genuinely visible and must not z-fight. */
    private static final float ZF = 1f / 4096f;

    /** Exact 7.1.x floats — deliberately not rounded to 6.92/16 and 9.08/16. */
    private static final float POST_LO = 0.4325f;
    private static final float POST_HI = 0.5675f;

    /** x runs outward: 0 is the block edge, 4/16 the pipe hull. (7.1.x states these DOWN-canonical,
     *  where the same span is y.) */
    private static final AABB PLATE =
        new AABB(3 / 16f, 4 / 16f, 4 / 16f, 4 / 16f + ZF, 12 / 16f, 12 / 16f);
    private static final AABB POST =
        new AABB(0, POST_LO, POST_LO, 3 / 16f + ZF, POST_HI, POST_HI);

    private static final Direction[] PLATE_FACES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    /** EAST omitted: it abuts the plate at x = 3/16 and is never visible. */
    private static final Direction[] POST_FACES = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST
    };

    /** Index of the post's outward tip face in {@link #buildGeometry}'s output. It is the one quad
     *  that sits on the block boundary, so the chunk builder lights the baked copy of it from the
     *  NEIGHBOURING block — the dynamic overlay has to sample the same place or the tip reads at a
     *  different brightness from the pedestal it caps. */
    public static final int TIP_QUAD_INDEX = 10;

    /** 1.7.10 drew both {@code None} and {@code Available} with this one sprite (its
     *  {@code pipeRobotStationBase} was shipped but referenced by no renderer), so the baked pedestal
     *  covers both idle states and the dynamic overlay only ever runs for reserved/linked. */
    public static final String SPRITE_AVAILABLE = "buildcraftunofficial:pipes/robot_station_available";

    private static MutableQuad[] baked;

    /**
     * Pure geometry + UVs, no sprite and no atlas access.
     *
     * @param proud outward expansion applied to POSITIONS only, on every axis. The overlay uses a
     *              small value to clear the baked pedestal underneath it; UVs stay projected from the
     *              unexpanded box, since a 1/1024-block shift is 1/64 of a texel and would only add
     *              float noise.
     * @return 11 quads: the plate's 6 faces then the post's 5, in {@link #TIP_QUAD_INDEX} order.
     */
    public static MutableQuad[] buildGeometry(float proud) {
        MutableQuad[] quads = new MutableQuad[PLATE_FACES.length + POST_FACES.length];
        int i = 0;
        for (Direction face : PLATE_FACES) {
            quads[i++] = createFace(PLATE, face, proud);
        }
        for (Direction face : POST_FACES) {
            quads[i++] = createFace(POST, face, proud);
        }
        return quads;
    }

    /** {@link #buildGeometry} with a sprite applied. Shade is set so that a later
     *  {@code multShade()} — which both {@code PlugBakerSimple} and the dynamic renderer call — is
     *  not a silent no-op. */
    public static MutableQuad[] buildPedestal(TextureAtlasSprite sprite, float proud) {
        MutableQuad[] quads = buildGeometry(proud);
        for (MutableQuad q : quads) {
            q.texFromSprite(sprite);
            q.setTint(-1);
            q.setShade(true);
        }
        return quads;
    }

    /** The chunk-baked idle pedestal, resolved lazily: sprite lookup is client- and bake-time only,
     *  never class-init. {@code PlugBakerSimple} re-bakes on ARRAY IDENTITY, so
     *  {@link #onModelBake()} dropping the cache is what makes a resource reload take effect. */
    public static MutableQuad[] bakedQuads() {
        if (baked == null) {
            baked = buildPedestal(SpriteUtil.getSprite(SPRITE_AVAILABLE), 0f);
        }
        return baked;
    }

    /** Drops the cached atlas references on a resource reload. */
    public static void onModelBake() {
        baked = null;
    }

    private static MutableQuad createFace(AABB box, Direction face, float proud) {
        ModelUtil.UvFaceData uv = new ModelUtil.UvFaceData();
        ModelUtil.mapBoxToUvs(box, face, uv);
        Vector3f center = new Vector3f(
            (float) ((box.minX + box.maxX) / 2),
            (float) ((box.minY + box.maxY) / 2),
            (float) ((box.minZ + box.maxZ) / 2)
        );
        Vector3f radius = new Vector3f(
            (float) ((box.maxX - box.minX) / 2) + proud,
            (float) ((box.maxY - box.minY) / 2) + proud,
            (float) ((box.maxZ - box.minZ) / 2) + proud
        );
        return ModelUtil.createFace(face, center, radius, uv);
    }
}
