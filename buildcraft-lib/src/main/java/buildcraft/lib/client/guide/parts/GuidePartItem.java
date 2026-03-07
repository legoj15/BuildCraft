package buildcraft.lib.client.guide.parts;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.ItemStackKey;

public abstract class GuidePartItem extends GuidePart {
    public static final GuiRectangle STACK_RECT = new GuiRectangle(0, 0, 16, 16);

    public GuidePartItem(GuiGuide gui) {
        super(gui);
    }

    protected void drawItemStack(ItemStackKey stack, int x, int y) {
        drawItemStack(stack.baseStack, x, y);
    }

    protected void drawItemStack(ItemStack stack, int x, int y) {
        // Rendering deferred — needs GuiGraphics (1.21 item rendering)
        // In 1.21 this would be:
        //   guiGraphics.renderItem(stack, x, y);
        //   guiGraphics.renderItemDecorations(font, stack, x, y);
        if (stack != null && !stack.isEmpty()) {
            if (STACK_RECT.offset(x, y).contains(gui.mouse)) {
                // gui.tooltipStack = stack — field not exposed yet
            }
        }
    }

    protected void testClickItemStack(ItemStackKey stack, int x, int y) {
        testClickItemStack(stack.baseStack, x, y);
    }

    protected void testClickItemStack(ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty() && STACK_RECT.offset(x, y).contains(gui.mouse)) {
            // gui.openPage(GuideManager.INSTANCE.getPageFor(stack).createNew(gui)) — deferred until full UI port
        }
    }
}
