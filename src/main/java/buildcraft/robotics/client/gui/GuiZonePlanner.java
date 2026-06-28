/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.gui;

import buildcraft.lib.gui.BCGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.zone.ZonePlan;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

//? if >=1.21.10 {
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

import buildcraft.robotics.client.render.ZoneMapPipRenderState;
//?} else {
/*import buildcraft.robotics.client.render.ZoneMapGuiRenderer;*/
//?}

/**
 * The Zone Planner screen. It hosts an interactive top-down 3D map of the surrounding terrain: drag an
 * empty hand to pan, scroll to zoom, and drag a coloured paintbrush to mark (right-drag to erase) that
 * colour's zone. On the modern lines (&gt;=1.21.10) the map is painted through the picture-in-picture
 * pipeline ({@link ZoneMapPipRenderState} / {@code ZoneMapPipRenderer}); on 1.21.1, which lacks that
 * pipeline, the same map is drawn straight into the GUI by {@code ZoneMapGuiRenderer} (both share
 * {@code ZoneMapGeometry}). The interaction layer — camera, pan/zoom, paint, ray-pick hover — is shared
 * across all lines.
 */
public class GuiZonePlanner extends GuiBC8<ContainerZonePlanner> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/zone_planner.png");
    private static final int SIZE_X = 256, SIZE_Y = 228;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    /** Map viewport window within the GUI texture (GUI-local coords) — the recessed frame the BC8
     *  layout reserves for the map ({@code offsetX=8, offsetY=9, sizeX=213, sizeY=100}). */
    private static final int MAP_X = 8, MAP_Y = 9, MAP_W = 213, MAP_H = 100;

    // Furnace-style transfer progress bars: fill sprites live at v=228 in the texture (off the GUI panel),
    // blitted partially over the arrow outlines baked into the background. Coords match the BC8 layout.
    private static final GuiIcon ICON_PROGRESS_INPUT = new GuiIcon(TEXTURE, 9, 228, 28, 9);
    private static final GuiIcon ICON_PROGRESS_OUTPUT = new GuiIcon(TEXTURE, 0, 228, 9, 28);
    private static final GuiRectangle RECT_PROGRESS_INPUT = new GuiRectangle(44, 128, 28, 9);
    private static final GuiRectangle RECT_PROGRESS_OUTPUT = new GuiRectangle(236, 45, 9, 28);

    /** Zoom step per scroll notch. */
    private static final double ZOOM_STEP = 1.15;

    private final ZoneMapCamera camera;
    private boolean panning = false;
    private double lastDragX, lastDragY;
    /** In-progress paint preview (null when not painting); committed to the layer on release. */
    private ZonePlan bufferLayer = null;
    private int bufferColorIndex = -1;
    /** 0 = paint (left button), 1 = erase (right button). */
    private int paintButton = -1;
    private BlockPos paintStart = null;
    private BlockPos hoverPos = null;

    public GuiZonePlanner(ContainerZonePlanner menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
        BlockPos p = menu.tile != null ? menu.tile.getBlockPos() : BlockPos.ZERO;
        camera = new ZoneMapCamera(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
        submitViewport(graphics);
        drawProgressBars();
    }

    /** Draws the two transfer-progress bars over their baked arrow outlines: input fills left→right,
     *  output fills top→bottom (furnace style). Works on every node — independent of the viewport. */
    private void drawProgressBars() {
        float in = menu.getProgressInputFraction();
        if (in > 0) {
            ICON_PROGRESS_INPUT.drawCutInside(new GuiRectangle(
                    RECT_PROGRESS_INPUT.x, RECT_PROGRESS_INPUT.y,
                    RECT_PROGRESS_INPUT.width * in, RECT_PROGRESS_INPUT.height).offset(mainGui.rootElement));
        }
        float out = menu.getProgressOutputFraction();
        if (out > 0) {
            ICON_PROGRESS_OUTPUT.drawCutInside(new GuiRectangle(
                    RECT_PROGRESS_OUTPUT.x, RECT_PROGRESS_OUTPUT.y,
                    RECT_PROGRESS_OUTPUT.width, RECT_PROGRESS_OUTPUT.height * out).offset(mainGui.rootElement));
        }
    }

    /** Shows the world coordinates of the column under the cursor, at the 1.12.2 position — GUI-local
     *  (130, 130), dark grey, no drop shadow — matching BC8's {@code GuiZonePlanner}. */
    @Override
    protected void drawForegroundLayer() {
        if (hoverPos != null) {
            BCGraphics g = GuiIcon.getGuiGraphics();
            String txt = "X: " + hoverPos.getX() + " Y: " + hoverPos.getY() + " Z: " + hoverPos.getZ();
            g.text(font, txt, 130, 130, 0xFF_40_40_40, false);
        }
    }

    @Override
    protected void initGuiElements() {
        // Terrain is rebuilt from the player's current surroundings each time the screen opens.
        ZonePlannerMapDataClient.INSTANCE.clear();
        // Invisible help region over the map window: documents the screen in the help ledger and keeps the
        // ledger's hover highlight aligned with the viewport. It is not an IInteractionElement, so it never
        // intercepts the viewport's drag/paint mouse input.
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(MAP_X, MAP_Y, MAP_W, MAP_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.zone_planner.map.title", 0xFF_88_CC_88,
                        "buildcraft.help.zone_planner.map.desc1",
                        "buildcraft.help.zone_planner.map.desc2")));
    }

    /** Draws the live map for this frame. On &gt;=1.21.10 it submits a PiP render state (painted into an
     *  offscreen texture and blitted); on 1.21.1 it draws straight into the GUI. Both sample the camera,
     *  zones, and hovered column fresh each frame. */
    private void submitViewport(BCGraphics graphics) {
        if (menu.tile == null) {
            return;
        }
        hoverPos = computeHover();
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;
        //? if >=1.21.10 {
        int x1 = x0 + MAP_W;
        int y1 = y0 + MAP_H;
        ScreenRectangle scissor = new ScreenRectangle(x0, y0, MAP_W, MAP_H);
        ZoneMapPipRenderState state = new ZoneMapPipRenderState(
                x0, y0, x1, y1, scissor,
                menu.tile.getBlockPos(), camera, menu.tile.layers,
                bufferLayer, bufferColorIndex, hoverPos);
        graphics.raw.submitPictureInPictureRenderState(state);
        //?} else {
        /*ZoneMapGuiRenderer.render(graphics, camera, menu.tile.getBlockPos(), menu.tile.layers,
                bufferLayer, bufferColorIndex, hoverPos, x0, y0, MAP_W, MAP_H);*/
        //?}
    }

    /** The terrain column under the live cursor (perspective ray-pick), or null if outside the map / a miss. */
    private BlockPos computeHover() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        if (!inMap(mx, my)) {
            return null;
        }
        return pickColumn(mx, my);
    }

    private boolean inMap(double mx, double my) {
        return mx >= leftPos + MAP_X && mx < leftPos + MAP_X + MAP_W
                && my >= topPos + MAP_Y && my < topPos + MAP_Y + MAP_H;
    }

    /** Terrain column under a screen pixel via the perspective ray-pick, as {@code (worldX, surfaceY,
     *  worldZ)}, or null on a miss. Used for both the hover box and painting so the cursor, the highlight,
     *  and the painted cell all agree even at the zoomed-out edges where a flat-plane pick drifts. */
    private BlockPos pickColumn(double mx, double my) {
        double cx = leftPos + MAP_X + MAP_W / 2.0;
        double cy = topPos + MAP_Y + MAP_H / 2.0;
        int[] hit = camera.pickTerrain(mx - cx, my - cy, this::surfaceAt);
        return hit == null ? null : new BlockPos(hit[0], hit[1], hit[2]);
    }

    private int surfaceAt(int wx, int wz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return ZonePlannerMapChunk.NO_DATA;
        }
        ZonePlannerMapChunk chunk = ZonePlannerMapDataClient.INSTANCE.getChunk(mc.level, wx >> 4, wz >> 4);
        return chunk == null ? ZonePlannerMapChunk.NO_DATA : chunk.getSurfaceY(wx & 15, wz & 15);
    }

    private static boolean isColouredBrush(ItemStack stack) {
        return stack.getItem() instanceof ItemPaintbrush_BC8 brush
                && brush.getBrushFromStack(stack).colour != null;
    }

    private static DyeColor brushColour(ItemStack stack) {
        return ((ItemPaintbrush_BC8) stack.getItem()).getBrushFromStack(stack).colour;
    }

    /** Rebuilds the in-progress paint preview as the saved layer plus the dragged rectangle set/cleared. */
    private void applyPaintRect(BlockPos a, BlockPos b) {
        if (a == null || b == null || bufferColorIndex < 0) {
            return;
        }
        bufferLayer = new ZonePlan(menu.tile.layers[bufferColorIndex]);
        BlockPos tile = menu.tile.getBlockPos();
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        boolean val = paintButton == 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                bufferLayer.set(x - tile.getX(), z - tile.getZ(), val);
            }
        }
    }

    // ── Map interaction (shared logic; the per-node mouse overrides below delegate here) ──────────

    /** @return true if the click started a paint or pan on the map (caller should not pass it on). */
    private boolean onMapClicked(double mx, double my, int button) {
        if (menu.tile != null && inMap(mx, my)) {
            ItemStack carried = menu.getCarried();
            if (isColouredBrush(carried)) {
                BlockPos start = pickColumn(mx, my);
                if (start != null) {
                    paintButton = button == 1 ? 1 : 0;
                    bufferColorIndex = brushColour(carried).ordinal();
                    paintStart = start;
                    applyPaintRect(paintStart, paintStart);
                }
                return true;
            } else if (carried.isEmpty() && button == 0) {
                panning = true;
                lastDragX = mx;
                lastDragY = my;
                return true;
            }
        }
        return false;
    }

    /** @return true if the drag is panning or painting the map. */
    private boolean onMapDragged(double mx, double my) {
        if (panning) {
            camera.panByPixels(mx - lastDragX, my - lastDragY);
            lastDragX = mx;
            lastDragY = my;
            return true;
        }
        if (bufferLayer != null && paintStart != null) {
            BlockPos cur = pickColumn(mx, my);
            if (cur != null) {
                applyPaintRect(paintStart, cur);
            }
            return true;
        }
        return false;
    }

    /** @return true if a pan ended or a paint was committed to its layer. */
    private boolean onMapReleased() {
        if (panning) {
            panning = false;
            return true;
        }
        if (bufferLayer != null && bufferColorIndex >= 0) {
            menu.tile.layers[bufferColorIndex] = bufferLayer;
            menu.sendPaint(bufferColorIndex, bufferLayer);
            bufferLayer = null;
            bufferColorIndex = -1;
            paintStart = null;
            paintButton = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scrollY != 0) {
            camera.zoomBy(scrollY > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    //? if >=1.21.10 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (onMapClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (onMapDragged(event.x(), event.y())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (onMapReleased()) {
            return true;
        }
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (onMapClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (onMapDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (onMapReleased()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }*/
    //?}

}
