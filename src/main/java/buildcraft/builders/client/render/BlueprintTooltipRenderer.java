package buildcraft.builders.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.ClientSnapshots;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.snapshot.Snapshot.Header;

import java.util.List;

/**
 * Renders a 3D isometric schematic preview when hovering over a blueprint item in the inventory.
 * Mirrors the 1.12.2 behavior where the blueprint content preview appears as an overlay on the tooltip.
 */
public class BlueprintTooltipRenderer {

    // Preview dimensions (matching 1.12.2 visual style)
    private static final int PREVIEW_WIDTH = 177;
    private static final int PREVIEW_HEIGHT = 177;

    // Track which blueprint is being hovered and its tooltip position
    private static Blueprint hoveredBlueprint = null;
    private static int hoveredTooltipX = 0;
    private static int hoveredTooltipY = 0;
    private static int hoveredTooltipWidth = 0;
    private static int hoveredTooltipHeight = 0;

    public static void init() {
        NeoForge.EVENT_BUS.register(BlueprintTooltipRenderer.class);
    }

    /**
     * Called during tooltip rendering to detect blueprint items under the mouse cursor.
     * This fires after tooltips are assembled but before they are drawn.
     */
    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onTooltipText(RenderGuiLayerEvent.Pre event) {
        // Reset state
        hoveredBlueprint = null;

        String layerName = event.getName();
        // Only process tooltip layers
        if (!layerName.equals("tooltip")) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            return;
        }

        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        double mouseX = mc.mouse.x;
        double mouseY = mc.mouse.y;

        // Get tooltip position and content from the event
        int tooltipX = (int) event.getPos().x();
        int tooltipY = (int) event.getPos().y();

        // Find which item the mouse is hovering over in the screen
        Slot hoveredSlot = null;
        for (Slot slot : screen.getMenu().slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (mouseX >= slot.x && mouseX < slot.x + 16 &&
                mouseY >= slot.y && mouseY < slot.y + 16) {
                hoveredSlot = slot;
                break;
            }
        }

        if (hoveredSlot == null) {
            return;
        }

        ItemStack itemStack = hoveredSlot.getItem();
        if (itemStack.getItem() instanceof ItemSnapshot snapshotItem) {
            Header header = snapshotItem.getHeader(itemStack);
            if (header != null) {
                Snapshot snapshot = ClientSnapshots.INSTANCE.getSnapshot(header.key);
                if (snapshot instanceof Blueprint blueprint) {
                    hoveredBlueprint = blueprint;
                    hoveredTooltipX = tooltipX;
                    hoveredTooltipY = tooltipY;

                    // Estimate tooltip size
                    Font font = mc.font;
                    int maxWidth = 0;
                    for (Component comp : screen.getTooltipLinesFromTooltipPos(
                            (float) mouseX, (float) mouseY)) {
                        int w = font.width(comp);
                        if (w > maxWidth) maxWidth = w;
                    }
                    hoveredTooltipWidth = maxWidth + 8; // icon width + padding
                    hoveredTooltipHeight = (int) (screen.getTooltipLinesFromTooltipPos(
                            (float) mouseX, (float) mouseY).size() * (font.lineHeight + 1));

                    // Cancel the default tooltip rendering - we'll draw our own
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * Renders the tooltip and 3D schematic preview after other GUI layers.
     */
    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onTooltipPost(RenderGuiLayerEvent.Post event) {
        String layerName = event.getName();
        if (!layerName.equals("tooltip")) {
            return;
        }

        if (hoveredBlueprint == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouse.x;
        double mouseY = mc.mouse.y;

        // Check if mouse is still hovering over the original tooltip area
        int tooltipLeft = hoveredTooltipX;
        int tooltipRight = hoveredTooltipX + hoveredTooltipWidth;
        int tooltipTop = hoveredTooltipY;
        int tooltipBottom = hoveredTooltipY + hoveredTooltipHeight;

        if (mouseX < tooltipLeft || mouseX > tooltipRight ||
            mouseY < tooltipTop || mouseY > tooltipBottom) {
            return;
        }

        // Calculate preview position - render to the right of the tooltip
        int previewX = hoveredTooltipX + hoveredTooltipWidth + 4;
        int previewY = hoveredTooltipY;

        // Adjust position if preview would go off-screen
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        if (previewX + PREVIEW_WIDTH > screenWidth) {
            previewX = hoveredTooltipX - PREVIEW_WIDTH - 4;
        }
        if (previewX < 0) {
            previewX = hoveredTooltipX + 4;
        }
        if (previewY + PREVIEW_HEIGHT > screenHeight) {
            previewY = screenHeight - PREVIEW_HEIGHT - 4;
        }
        if (previewY < 0) {
            previewY = 4;
        }

        // Get the graphics context
        var poseStack = event.getGraphics().getPoseStack();
        poseStack.pushPose();
        poseStack.translate((float) previewX, (float) previewY, 0);

        try {
            BlueprintRenderer.renderSnapshot(
                new TooltipGraphicsHelper(poseStack, previewX, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT),
                hoveredBlueprint,
                previewX, previewY, PREVIEW_WIDTH, PREVIEW_HEIGHT
            );
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Wrapper that provides a GuiGraphicsExtractor-like interface over a PoseStack.
     * Allows reusing BlueprintRenderer's renderSnapshot method.
     */
    private static class TooltipGraphicsHelper {
        private final com.mojang.blaze3d.vertex.PoseStack poseStack;
        private final int x, y, w, h;

        TooltipGraphicsHelper(com.mojang.blaze3d.vertex.PoseStack poseStack, int x, int y, int w, int h) {
            this.poseStack = poseStack;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        void enableScissor(int x1, int y1, int x2, int y2) {
            // Minecraft doesn't directly expose scissor in the same way
            // The BlueprintRenderer uses scissor to limit rendering to the preview area
        }

        void disableScissor() {
        }

        void fakeItem(net.minecraft.world.item.ItemStack stack, int itemX, int itemY) {
            if (stack.isEmpty()) return;
            Minecraft.getInstance().getItemRenderer().renderItem(
                stack,
                net.minecraft.client.renderer.ItemRenderer.ItemLayerType.FIXED,
                false,
                poseStack,
                Minecraft.getInstance().levelRenderer().getLevel().getChunkSource().getLightEngine(),
                net.minecraft.client.renderer.LightTexture.FULL_SCREEN,
                poseStack.last().pose(),
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight(),
                stack.getId()
            );
        }
    }
}