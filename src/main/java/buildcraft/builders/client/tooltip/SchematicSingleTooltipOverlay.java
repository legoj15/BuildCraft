/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.tooltip;

import java.util.List;

import org.joml.Vector2ic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

import buildcraft.platform.client.BCTooltipPreContext;

import buildcraft.api.schematics.ISchematicBlock;

import buildcraft.builders.client.render.BlueprintRenderer;
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.snapshot.Blueprint;

/**
 * Draws a 3D preview tooltip panel below the main tooltip when a "used" single-block schematic
 * is hovered. 1.12.2 never did this — you could tell two single-schematic items apart only by
 * right-clicking them, which was terrible UX. We now reuse {@link BlueprintRenderer}'s PiP
 * pipeline by synthesizing a throwaway 1×1×1 {@link Blueprint} from the captured schematic NBT.
 * <p>
 * The layout math (frame padding, positioner invocation, visible-gap spacing) is a direct
 * copy of {@link BlueprintTooltipOverlay}'s — see that class for the derivation. This duplication
 * is intentional for now: factoring the shared positioner logic out requires a small static
 * helper, and both overlays are short enough that the parallel structure reads more clearly
 * than a shared utility would.
 */
public final class SchematicSingleTooltipOverlay {

    private SchematicSingleTooltipOverlay() {}

    /** Side length of the 3D preview, in GUI pixels. Matches the blueprint overlay. */
    public static final int PREVIEW_SIZE = 100;

    /** See {@link BlueprintTooltipOverlay#VISIBLE_GAP}. */
    private static final int VISIBLE_GAP = 4;
    /** See {@link BlueprintTooltipOverlay#FRAME_PADDING}. */
    private static final int FRAME_PADDING = 3;

    /**
     * Single-entry cache: the last schematic we rendered and the {@link Blueprint} we wrapped it
     * in. Reused across frames so the PiP renderer sees a stable {@code System.identityHashCode}
     * and its log-once guard works — without this, every tooltip frame allocated a new Blueprint
     * and spammed {@code renderToTexture: size=1x1x1...} into the log at INFO every frame.
     * <p>
     * Render-thread only; no synchronization. A single-item cache is sufficient because the
     * player can hover one item at a time.
     */
    @Nullable
    private static ISchematicBlock cachedSchematic;
    @Nullable
    private static Blueprint cachedSynthetic;

    /**
     * Entry point — registered through {@link buildcraft.platform.client.ClientHooks#onTooltipPre}
     * alongside {@link BlueprintTooltipOverlay#onPreTooltip}.
     */
    public static void onPreTooltip(BCTooltipPreContext ctx) {
        ItemStack stack = ctx.stack();
        if (!(stack.getItem() instanceof ItemSchematicSingle schemItem) || !schemItem.isUsed()) {
            return;
        }
        ISchematicBlock schematic = ItemSchematicSingle.getSchematicSafe(stack);
        if (schematic == null) {
            return;
        }

        // Run the same vanilla tooltip-layout math BlueprintTooltipOverlay does so we land
        // exactly below the main tooltip, regardless of where on screen it was positioned.
        Font font = ctx.font();
        List<ClientTooltipComponent> components = ctx.components();
        if (components.isEmpty()) {
            return;
        }
        int textWidth = 0;
        int contentHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : components) {
            int w = c.getWidth(font);
            if (w > textWidth) {
                textWidth = w;
            }
            //? if >=1.21.10 {
            contentHeight += c.getHeight(font);
            //?} else {
            /*contentHeight += c.getHeight();*/
            //?}
        }
        ClientTooltipPositioner positioner = ctx.positioner();
        Vector2ic finalPos = positioner.positionTooltip(
                ctx.screenWidth(), ctx.screenHeight(),
                ctx.x(), ctx.y(), textWidth, contentHeight);
        int finalX = finalPos.x();
        int finalY = finalPos.y();
        int pX = finalX;
        int pY = finalY + contentHeight + FRAME_PADDING + VISIBLE_GAP + FRAME_PADDING;

        // Draw the frame first so the PiP draws over it.
        //? if >=26.1 {
        TooltipRenderUtil.extractTooltipBackground(
                ctx.graphics(), pX, pY, PREVIEW_SIZE, PREVIEW_SIZE, null);
        //?} elif >=1.21.10 {
        /*TooltipRenderUtil.renderTooltipBackground(
                ctx.graphics(), pX, pY, PREVIEW_SIZE, PREVIEW_SIZE, null);*/
        //?} else {
        /*TooltipRenderUtil.renderTooltipBackground(
                ctx.graphics(), pX, pY, PREVIEW_SIZE, PREVIEW_SIZE, 400);*/
        //?}

        // Synthesize a 1×1×1 blueprint so BlueprintRenderer's existing pipeline just works.
        // Cached across frames — see cachedSynthetic javadoc. ISchematicBlock value-equality on
        // SchematicBlockDefault (the common case) ensures fresh-deserialized instances from the
        // item NBT match the cached key without rebuilding.
        Blueprint synthetic = getOrBuildSynthetic(schematic);

        BlueprintRenderer.renderSnapshot(
                new buildcraft.lib.gui.BCGraphics(ctx.graphics()), synthetic, pX, pY, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    private static Blueprint getOrBuildSynthetic(ISchematicBlock schematic) {
        Blueprint cached = cachedSynthetic;
        if (cached != null && schematic.equals(cachedSchematic)) {
            return cached;
        }
        Blueprint synthetic = new Blueprint();
        synthetic.size = new BlockPos(1, 1, 1);
        synthetic.offset = BlockPos.ZERO;
        synthetic.facing = Direction.NORTH;
        synthetic.data = new int[] { 0 };
        synthetic.palette.add(schematic);
        cachedSchematic = schematic;
        cachedSynthetic = synthetic;
        return synthetic;
    }
}
