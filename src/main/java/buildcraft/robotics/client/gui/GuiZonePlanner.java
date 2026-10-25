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

//? if >=26.3 {
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.system.MemoryStack;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.container.ContainerZonePlanner;
import buildcraft.robotics.client.render.ZoneMapGeometry;
import buildcraft.robotics.client.zone.ZoneMapCamera;
import buildcraft.robotics.client.zone.ZonePaintInput;
import buildcraft.robotics.client.zone.ZonePaintStroke;
import buildcraft.robotics.client.zone.ZonePanDrag;
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

    /** Zoom step per scroll notch. Zooming out steps harder (1.35×) than zooming in (1.15×) so the
     *  survey range (down to 0.125 px/block, ~1,700 blocks across — 1.7.10's limit) is a comfortable
     *  handful of notches away, while close-up work keeps its fine control. */
    private static final double ZOOM_STEP_IN = 1.15;
    private static final double ZOOM_STEP_OUT = 1.35;

    private final ZoneMapCamera camera;
    /** The terrain caches have been reset and the far map asked for: once per opening, not on every re-init (a
     *  window resize), which would blank the far map until the server's send interval let it through again. */
    private boolean mapRequested;
    /** The pan drag in progress, if any: owned by the left button that began it and moved by cursor moves, not drag
     *  events (see {@link ZonePanDrag} for why). */
    private final ZonePanDrag pan = new ZonePanDrag();
    /** In-progress paint preview (null when not painting). Display only: on release the stroke itself (the
     *  rectangle {@link #stroke} holds) is applied to the layer as it is then and sent. */
    private ZonePlan bufferLayer = null;
    private int bufferColorIndex = -1;
    /** The stroke being drawn, if any: owned by the mouse button that began it (see {@link ZonePaintStroke}). */
    private final ZonePaintStroke stroke = new ZonePaintStroke();
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
        // Terrain is rebuilt from the player's current surroundings each time the screen opens, and what the server
        // has recorded past them is asked for afresh.
        if (!mapRequested) {
            mapRequested = true;
            ZonePlannerMapDataClient.INSTANCE.clear();
            ZoneMapGeometry.clearOverlayCache();
            menu.requestFarMap();
        }
        addHelpRegions();
    }

    /** Documents the screen in the help ledger: one highlighted, hoverable region per functional zone
     *  (map viewport, brush rack, input row, output column). The elements are {@link DummyHelpElement}s —
     *  help carriers only, never {@code IInteractionElement}s — so the viewport's drag/paint mouse input
     *  passes through untouched. */
    private void addHelpRegions() {
        // The map window itself: gestures and the survey range.
        mainGui.shownElements.add(helpRegion(
                new GuiRectangle(MAP_X, MAP_Y, MAP_W, MAP_H),
                "buildcraft.help.zone_planner.map.title", 0xFF_88_CC_88,
                "buildcraft.help.zone_planner.map.desc1",
                "buildcraft.help.zone_planner.map.desc2",
                "buildcraft.help.zone_planner.map.desc3"));

        // The 4×4 paintbrush rack.
        mainGui.shownElements.add(helpRegion(
                new GuiRectangle(8, 146, 70, 70),
                "buildcraft.help.zone_planner.brushes.title", 0xFF_FF_BB_33,
                "buildcraft.help.zone_planner.brushes.desc"));

        // Input row: brush + written map location, progress arrow, blank-map result.
        mainGui.shownElements.add(helpRegion(
                new GuiRectangle(8, 125, 82, 16),
                "buildcraft.help.zone_planner.input.title", 0xFF_66_AA_FF,
                "buildcraft.help.zone_planner.input.desc1",
                "buildcraft.help.zone_planner.input.desc2"));

        // Output column: brush + blank map location, progress bar, written-map result.
        mainGui.shownElements.add(helpRegion(
                new GuiRectangle(233, 9, 16, 82),
                "buildcraft.help.zone_planner.output.title", 0xFF_DD_66_FF,
                "buildcraft.help.zone_planner.output.desc1",
                "buildcraft.help.zone_planner.output.desc2"));
    }

    /** A help-ledger carrier over the given GUI-local area — ledger info only, no hover tooltip. */
    private DummyHelpElement helpRegion(GuiRectangle area, String titleKey, int colour, String... descKeys) {
        return new DummyHelpElement(area.offset(mainGui.rootElement), new ElementHelpInfo(titleKey, colour, descKeys));
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        double cx = leftPos + MAP_X + MAP_W / 2.0;
        double cy = topPos + MAP_Y + MAP_H / 2.0;
        // As deep as the renderer draws (the build floor), so terrain far below the planner is hoverable too.
        int[] hit = camera.pickTerrain(mx - cx, my - cy, this::surfaceAt, RobotUtils.minBuildY(mc.level));
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

    /** Rebuilds the in-progress paint preview as the saved layer plus the dragged rectangle set/cleared,
     *  clipped to the survey square the map outlines (strokes cannot reach beyond it). */
    private void applyPaintRect() {
        ZonePaintStroke.Stroke current = stroke.current();
        if (current == null) {
            return;
        }
        BlockPos tile = menu.tile.getBlockPos();
        bufferColorIndex = current.colorIndex();
        bufferLayer = ZonePlan.withRect(menu.tile.layers[current.colorIndex()],
                current.startX() - tile.getX(), current.startZ() - tile.getZ(),
                current.endX() - tile.getX(), current.endZ() - tile.getZ(),
                current.paints());
    }

    // ── Map interaction (shared logic; the per-node mouse overrides below delegate here) ──────────

    /** @return true if the click started a paint or pan on the map (caller should not pass it on). */
    private boolean onMapClicked(double mx, double my, int button) {
        if (menu.tile != null && inMap(mx, my)) {
            ItemStack carried = menu.getCarried();
            if (isColouredBrush(carried)) {
                // Left paints, right erases; any other button (middle, a side button) starts no stroke, and none
                // starts while one is open (the press is ignored: it neither restarts nor changes the stroke). The
                // click is still ours either way, as it always was with a brush over the map.
                if (!stroke.isOpen() && ZonePaintInput.strokeFor(button) != ZonePaintInput.NONE) {
                    BlockPos start = pickColumn(mx, my);
                    if (start != null
                            && stroke.press(button, brushColour(carried).ordinal(), start.getX(), start.getZ(),
                                    GuiZonePlanner::isMouseButtonHeld)) {
                        applyPaintRect();
                    }
                }
                return true;
            } else if (carried.isEmpty() && pan.press(button, mx, my)) {
                return true;
            }
        }
        return false;
    }

    /** @return true if the drag is panning or painting the map. */
    private boolean onMapDragged() {
        // Ours, but mouseMoved does the panning and the painting: drags stop arriving once any second button is
        // released, although the one that began the pan or the stroke may still be down.
        return pan.isActive() || stroke.isOpen();
    }

    /** @return true if a pan ended, a paint was committed to its layer, or a stroke is still open. */
    private boolean onMapReleased(int button) {
        if (pan.isActive()) {
            // Only the button that began the pan ends it; letting go of another leaves it running.
            pan.release(button);
            return true;
        }
        if (stroke.isOpen()) {
            // Only the button that began the stroke ends it; letting go of another commits nothing.
            ZonePaintStroke.Stroke done = stroke.release(button);
            if (done != null) {
                // The stroke, not the preview: the preview was built on the layer as it stood at the last drag event,
                // and a sync may have landed since. paintRect applies the rectangle to the layer as it is now.
                BlockPos tile = menu.tile.getBlockPos();
                menu.paintRect(done.colorIndex(),
                        done.startX() - tile.getX(), done.startZ() - tile.getZ(),
                        done.endX() - tile.getX(), done.endZ() - tile.getZ(),
                        done.paints());
                bufferLayer = null;
                bufferColorIndex = -1;
            }
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        // Minecraft sends this on every cursor movement, a held button or not, unlike mouseDragged (see ZonePanDrag).
        ZonePanDrag.Delta delta = pan.move(mouseX, mouseY, isMouseButtonHeld(ZonePanDrag.PAN_BUTTON));
        if (delta != null) {
            camera.panByPixels(delta.dx(), delta.dy());
        }
        if (stroke.isOpen()) {
            // The stroke is the starting button's: it follows the cursor while THAT button is physically down,
            // whichever other button was tapped meanwhile (see ZonePaintStroke#move), and the left one for a macOS
            // Ctrl+left erase, which arrives as a simulated right click (see ZonePaintStroke#press).
            BlockPos cur = pickColumn(mouseX, mouseY);
            if (cur != null && stroke.move(cur.getX(), cur.getZ(), GuiZonePlanner::isMouseButtonHeld)) {
                applyPaintRect();
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    /** Whether {@code button} (a Minecraft button number, which is the window system's own) is physically down now,
     *  asked of the window system: Minecraft tracks only the most recently pressed one, and clears it when any is
     *  released. */
    private static boolean isMouseButtonHeld(int button) {
        //? if >=26.3 {
        // 26.3 swapped GLFW for SDL: no window handle is needed, and SDL numbers the buttons from 1 exactly as
        // Minecraft does there, so the state mask has bit (button - 1) set while that button is down.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return (SDLMouse.SDL_GetMouseState(stack.mallocFloat(1), stack.mallocFloat(1)) & (1 << (button - 1))) != 0;
        }
        //?} elif >=1.21.10 {
        /*long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
        *///?} else {
        /*long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;*/
        //?}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scrollY != 0) {
            double step = scrollY > 0 ? ZOOM_STEP_IN : 1.0 / ZOOM_STEP_OUT;
            camera.zoomBy(step);
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
        if (onMapDragged()) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (onMapReleased(event.button())) {
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
        if (onMapDragged()) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (onMapReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }*/
    //?}

}
