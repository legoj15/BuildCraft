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

import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;

//? if >=1.21.10 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.robotics.client.render.ZoneMapPipRenderState;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePlannerMapChunk;
import buildcraft.robotics.client.zone.ZonePlannerMapDataClient;
import buildcraft.robotics.zone.ZonePlan;
//?}

/**
 * The Zone Planner screen. On the modern lines (&gt;=1.21.10) it hosts an interactive isometric 3D map of
 * the surrounding terrain rendered through the picture-in-picture pipeline ({@link ZoneMapPipRenderState}
 * / {@code ZoneMapPipRenderer}): drag an empty hand to pan, scroll to zoom, and drag a coloured
 * paintbrush to mark/erase that colour's zone. The 1.21.1 line lacks the vanilla picture-in-picture
 * <i>class</i>, so this viewport is gated out there and the planner keeps a non-rendered placeholder map
 * panel (the slot-based paintbrush&harr;map-location transfer still works). That is a deferred follow-up,
 * not a hard limit: the same map could be drawn straight into the GUI on 1.21.1 the way
 * {@code BlueprintGuiRenderer} draws the blueprint preview there (see todos.md).
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

    //? if >=1.21.10 {
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
    //?}

    public GuiZonePlanner(ContainerZonePlanner menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
        //? if >=1.21.10 {
        BlockPos p = menu.tile != null ? menu.tile.getBlockPos() : BlockPos.ZERO;
        camera = new ZoneMapCamera(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        //?}
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
        //? if >=1.21.10 {
        submitViewport(graphics);
        //?}
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

    //? if >=1.21.10 {
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
    //?}

    @Override
    protected void initGuiElements() {
        //? if >=1.21.10 {
        // Terrain is rebuilt from the player's current surroundings each time the screen opens.
        ZonePlannerMapDataClient.INSTANCE.clear();
        //?}
        // Invisible help region over the map window: documents the screen in the help ledger and keeps the
        // ledger's hover highlight aligned with the viewport. It is not an IInteractionElement, so it never
        // intercepts the viewport's drag/paint mouse input (and on 1.21.1, where there is no live viewport,
        // it's the placeholder map panel's help).
        mainGui.shownElements.add(new DummyHelpElement(
                new GuiRectangle(MAP_X, MAP_Y, MAP_W, MAP_H).offset(mainGui.rootElement),
                new ElementHelpInfo("buildcraft.help.zone_planner.map.title", 0xFF_88_CC_88,
                        "buildcraft.help.zone_planner.map.desc1",
                        "buildcraft.help.zone_planner.map.desc2")));
    }

    //? if >=1.21.10 {

    private void submitViewport(BCGraphics graphics) {
        if (menu.tile == null) {
            return;
        }
        hoverPos = computeHover();
        int x0 = leftPos + MAP_X;
        int y0 = topPos + MAP_Y;
        int x1 = x0 + MAP_W;
        int y1 = y0 + MAP_H;
        ScreenRectangle scissor = new ScreenRectangle(x0, y0, MAP_W, MAP_H);
        ZoneMapPipRenderState state = new ZoneMapPipRenderState(
                x0, y0, x1, y1, scissor,
                menu.tile.getBlockPos(), camera, menu.tile.layers,
                bufferLayer, bufferColorIndex, hoverPos);
        graphics.raw.submitPictureInPictureRenderState(state);
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (menu.tile != null && inMap(mx, my)) {
            ItemStack carried = menu.getCarried();
            if (isColouredBrush(carried)) {
                BlockPos start = pickColumn(mx, my);
                if (start != null) {
                    paintButton = event.button() == 1 ? 1 : 0;
                    bufferColorIndex = brushColour(carried).ordinal();
                    paintStart = start;
                    applyPaintRect(paintStart, paintStart);
                }
                return true;
            } else if (carried.isEmpty() && event.button() == 0) {
                panning = true;
                lastDragX = mx;
                lastDragY = my;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mx = event.x(), my = event.y();
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
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
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scrollY != 0) {
            camera.zoomBy(scrollY > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?}

}
