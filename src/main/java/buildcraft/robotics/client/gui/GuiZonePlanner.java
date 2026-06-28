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
 * paintbrush to mark/erase that colour's zone. The 1.21.1 line has no PiP pipeline, so it keeps a
 * non-rendered placeholder map panel (the slot-based paintbrush&harr;map-location transfer still works).
 */
public class GuiZonePlanner extends GuiBC8<ContainerZonePlanner> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/zone_planner.png");
    private static final int SIZE_X = 256, SIZE_Y = 228;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    /** Map viewport window within the GUI texture (GUI-local coords). */
    private static final int MAP_X = 17, MAP_Y = 17, MAP_W = 213, MAP_H = 119;

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
    }

    @Override
    protected void initGuiElements() {
        //? if >=1.21.10 {
        // Terrain is rebuilt from the player's current surroundings each time the screen opens.
        ZonePlannerMapDataClient.INSTANCE.clear();
        // The viewport is drawn directly in drawBackgroundTexture via the PiP pipeline, so no GUI element
        // is needed; the auto-attached help ledger still documents the screen.
        //?} else {
        /*// 1.21.1: no PiP pipeline — keep the non-rendered placeholder map panel + its help text.
        mainGui.shownElements.add(new buildcraft.lib.gui.help.DummyHelpElement(
                new buildcraft.lib.gui.pos.GuiRectangle(MAP_X, MAP_Y, MAP_W, MAP_H).offset(mainGui.rootElement),
                new buildcraft.lib.gui.help.ElementHelpInfo("buildcraft.help.zone_planner.map.title", 0xFF_88_CC_88,
                        "buildcraft.help.zone_planner.map.desc1",
                        "buildcraft.help.zone_planner.map.desc2")));*/
        //?}
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

    /** Maps the live cursor position to the world ground column it sits over, or null if outside the map. */
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
        BlockPos col = groundColumn(mx, my);
        int surf = surfaceAt(col.getX(), col.getZ());
        if (surf == ZonePlannerMapChunk.NO_DATA) {
            return null;
        }
        return new BlockPos(col.getX(), surf, col.getZ());
    }

    private boolean inMap(double mx, double my) {
        return mx >= leftPos + MAP_X && mx < leftPos + MAP_X + MAP_W
                && my >= topPos + MAP_Y && my < topPos + MAP_Y + MAP_H;
    }

    /** World column (X/Z; Y unused) under a screen pixel, via ground-plane projection. */
    private BlockPos groundColumn(double mx, double my) {
        double cx = leftPos + MAP_X + MAP_W / 2.0;
        double cy = topPos + MAP_Y + MAP_H / 2.0;
        double[] world = new double[2];
        camera.pickGround(mx - cx, my - cy, world);
        return new BlockPos((int) Math.floor(world[0]), 0, (int) Math.floor(world[1]));
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
                paintButton = event.button() == 1 ? 1 : 0;
                bufferColorIndex = brushColour(carried).ordinal();
                paintStart = groundColumn(mx, my);
                applyPaintRect(paintStart, paintStart);
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
            applyPaintRect(paintStart, groundColumn(mx, my));
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
