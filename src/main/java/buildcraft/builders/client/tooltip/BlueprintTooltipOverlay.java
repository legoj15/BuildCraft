/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.client.tooltip;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2ic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import buildcraft.builders.client.render.BlueprintRenderer;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.builders.snapshot.Snapshot;

/**
 * Draws a second tooltip-shaped panel <i>below</i> the main item tooltip containing a rotating
 * 3D preview of the snapshot, whenever a Blueprint/Template item is hovered in a GUI.
 * <p>
 * This is the 26.1 port of the 1.12.2 {@code BCBuildersEventDist.onPostText} handler introduced
 * in the May-2017 "Snapshot preview" commit. The 1.12.2 version hooked
 * {@code RenderTooltipEvent.PostText} and drew its own purple-gradient tooltip frame at
 * {@code (event.getX(), event.getY() + event.getHeight() + 10)} — a fixed offset below the main
 * tooltip with no screen-bound check. NeoForge 26.1 <b>removed</b> {@code PostText}; the closest
 * modern equivalent is {@link RenderTooltipEvent.Pre}, at which point we can:
 * <ul>
 *   <li>Reach the raw component list, the font, and the screen dimensions.</li>
 *   <li>Re-run the same layout math {@code GuiGraphicsExtractor#tooltip} does to compute the
 *       content width and height.</li>
 *   <li>Invoke the positioner ourselves to resolve the final on-screen tooltip position (which
 *       is what {@code event.getX()/getY()} <i>would</i> return — except at Pre time those are
 *       the pre-positioning inputs, not the resolved coordinates).</li>
 *   <li>Paint our panel using {@link TooltipRenderUtil#extractTooltipBackground} — matches
 *       whatever background style the user has themed, rather than hardcoding the 1.12.2
 *       purple-on-black frame.</li>
 * </ul>
 * <p>
 * <b>Why drawing at Pre time is safe despite the main tooltip rendering afterward:</b> our
 * panel is positioned strictly below the main tooltip with no vertical overlap, and the GUI
 * layer is a single pass, so draw-order within it doesn't matter for non-overlapping rects.
 * The 3D preview itself goes through the Picture-in-Picture pass, which renders offscreen
 * first and blits over the GUI layer — so the preview content always ends up on top of the
 * panel frame regardless of submission order.
 * <p>
 * <b>Offscreen behavior:</b> matches 1.12.2 — if the main tooltip is tall and hovered near
 * the bottom of the window, the preview falls off the bottom edge. No screen-bound check is
 * applied; callers who find this objectionable can add one later.
 */
public final class BlueprintTooltipOverlay {

    private BlueprintTooltipOverlay() {}

    private static final Logger LOGGER = LogManager.getLogger("BCBlueprintTooltipOverlay");

    /** Side length of the 3D preview, in GUI pixels. Matches the 1.12.2 100×100 panel. */
    public static final int PREVIEW_SIZE = 100;

    /**
     * Pixels of visible gap between the main tooltip frame and the preview frame. Matches
     * the 1.12.2 value: the original code used {@code pY = tooltipBottom + 10}, minus the
     * 3-pixel tooltip border on each side, comes out to a 4-pixel visible gap.
     */
    private static final int VISIBLE_GAP = 4;

    /**
     * Extent of the tooltip's <i>visible</i> frame outside its content rectangle. Modern
     * Minecraft tooltip sprites are drawn as a 9-slice whose drawn rect extends 12 px past
     * content (see {@link TooltipRenderUtil#extractTooltipBackground}: {@code x0 = x - 3 - 9}),
     * but the first 9 px is a transparent margin; the visible border lives in the remaining
     * 3-pixel padding. The positioner uses this same 3-pixel figure when computing whether
     * the tooltip fits on screen ({@link MenuTooltipPositioner}: {@code paddedHeight = h + 3 + 3}),
     * so treating it as the "visible extent" is consistent with vanilla's own layout.
     */
    private static final int FRAME_PADDING = 3;

    /**
     * Hashes we've already logged a diagnostic line for, so hovering the same blueprint
     * frame-after-frame doesn't spam the log. Populated on first successful fetch.
     */
    private static final Set<String> LOGGED_KEYS = Collections.synchronizedSet(new HashSet<>());

    /**
     * Entry point — registered on {@code NeoForge.EVENT_BUS} (the runtime/game event bus, not
     * the mod bus; {@link RenderTooltipEvent} is a per-frame rendering event).
     */
    public static void onPreTooltip(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ItemSnapshot)) {
            return;
        }
        Snapshot.Header header = ItemSnapshot.getHeader(stack);
        if (header == null) {
            // Clean (unused) blueprint/template — there is no snapshot payload to preview.
            return;
        }

        // Touch ClientSnapshots on every hover frame. First call for a key fires a server
        // request and returns null; subsequent frames return the snapshot once the response
        // has arrived. We draw the empty frame either way so the panel pops in immediately
        // and fills in once the data lands — rather than appearing only after round-trip.
        Snapshot snapshot = ClientSnapshots.INSTANCE.getSnapshot(header.key);

        Font font = event.getFont();
        List<ClientTooltipComponent> components = event.getComponents();
        if (components.isEmpty()) {
            return;
        }

        // Reproduce the vanilla tooltip-layout math from GuiGraphicsExtractor#tooltip
        // (lines ~1155–1168 of the deobfuscated source). Single-component tooltips start
        // tempHeight at -2 because no inter-line gap is needed; multi-component tooltips
        // start at 0 and accumulate the full getHeight of each component.
        int textWidth = 0;
        int contentHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent c : components) {
            int w = c.getWidth(font);
            if (w > textWidth) {
                textWidth = w;
            }
            contentHeight += c.getHeight(font);
        }

        // Run the positioner to find the final on-screen coordinate. We have to do this
        // ourselves because RenderTooltipEvent.Pre fires BEFORE vanilla runs the positioner,
        // so event.getX()/event.getY() return pre-positioning inputs (typically the raw mouse
        // coordinates), not the final tooltip origin.
        ClientTooltipPositioner positioner = event.getTooltipPositioner();
        Vector2ic finalPos = positioner.positionTooltip(
                event.getScreenWidth(), event.getScreenHeight(),
                event.getX(), event.getY(), textWidth, contentHeight);
        int finalX = finalPos.x();
        int finalY = finalPos.y();

        // Preview content origin: left-aligned with main content, placed a visible gap below
        // the main tooltip's visible frame bottom. Layout stack (all deltas in GUI pixels):
        //
        //   finalY                                  ← main content top
        //   finalY + contentHeight                  ← main content bottom
        //   + FRAME_PADDING (3)                     ← main frame visible bottom
        //   + VISIBLE_GAP   (4)                     ← visible air between frames
        //   + FRAME_PADDING (3)                     ← our frame's visible top
        //   = pY = finalY + contentHeight + 10      ← our content top
        //
        // Matches the 1.12.2 formula `pY = event.getY() + event.getHeight() + 10` exactly.
        int pX = finalX;
        int pY = finalY + contentHeight + FRAME_PADDING + VISIBLE_GAP + FRAME_PADDING;

        // Paint the tooltip-style frame around the preview area so it reads as a second
        // tooltip. Passing null for the style matches the default vanilla background sprite
        // and honors the user's resource-pack tooltip theme, if any.
        TooltipRenderUtil.extractTooltipBackground(
                event.getGraphics(), pX, pY, PREVIEW_SIZE, PREVIEW_SIZE, null);

        // Hand the 3D rendering off to the PiP pipeline. Both snapshot kinds render: Blueprints as
        // 3D block-item cubes, Templates as a translucent green ghost shell (BlueprintPipRenderer
        // branches per cell). A null snapshot means the payload hasn't arrived from the server yet
        // — the frame was already drawn above, so the panel pops in now and fills once data lands.
        if (snapshot != null) {
            BlueprintRenderer.renderSnapshot(
                    event.getGraphics(), snapshot, pX, pY, PREVIEW_SIZE, PREVIEW_SIZE);
        }

        logOnce(header.key, snapshot, pX, pY);
    }

    private static void logOnce(Snapshot.Key key, Snapshot snapshot, int pX, int pY) {
        String hashHex = key.hash == null ? "null"
                : buildcraft.lib.misc.HashUtil.convertHashToString(key.hash);
        if (LOGGED_KEYS.add(hashHex)) {
            LOGGER.info("Overlay: hash={} snapshot={} at ({}, {}) {}x{}",
                    hashHex,
                    snapshot == null ? "pending" : snapshot.getClass().getSimpleName(),
                    pX, pY, PREVIEW_SIZE, PREVIEW_SIZE);
        }
    }
}
