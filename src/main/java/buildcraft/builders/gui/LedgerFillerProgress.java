package buildcraft.builders.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import buildcraft.builders.container.ContainerFiller;
import buildcraft.lib.gui.BuildCraftGui;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.Ledger_Neptune;

public class LedgerFillerProgress extends Ledger_Neptune {
    private final ContainerFiller container;

    public LedgerFillerProgress(BuildCraftGui gui, ContainerFiller container) {
        super(gui, 0x6CD41F, true);
        this.title = "gui.progress";
        this.container = container;

        // Reserve space and draw the text
        // In 1.12.2, icon was on the left, then text to the right.
        // We will draw the number offset, using spaces or just by relying on the manual item render below.
        appendText(() -> "    " + container.getSyncedToBreak(), 0xFF333333);
        appendText(() -> "    " + container.getSyncedToPlace(), 0xFF333333);
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphicsExtractor graphics) {
        // Closed icon is an iron ingot in 1.12.2
        graphics.fakeItem(new ItemStack(Items.IRON_INGOT), (int) x, (int) y);
    }

    @Override
    public void drawBackground(float partialTicks) {
        super.drawBackground(partialTicks);

        GuiGraphicsExtractor graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        if (interpWidth > CLOSED_WIDTH + 10) {
            // Draw the items left of the numbers
            // Coordinates matching Ledger_Neptune text drawing exactly
            // We use standard Minecraft GUI render Item to get the items
            
            int scissorX = (int) getX() + 2; // Approximate start area for icon start
            int scissorY = (int) getY() + 4;
            int scissorW = (int) (interpWidth - LEDGER_GAP);
            int scissorH = (int) (interpHeight - LEDGER_GAP * 2);
            graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

            double iconX = getX() + 2; // positionLedgerIconStart.getX()
            double iconY = getY() + 4; // positionLedgerIconStart.getY()
            
            int textX = (int) iconX + 16 + LEDGER_GAP;
            int textY = (int) iconY + 1;

            Font font = Minecraft.getInstance().font;
            textY += font.lineHeight + 3; // Skip title

            // First entry: To Break (Iron Pickaxe)
            // Render item expects standard scale, it draws 16x16
            // We shift it up slightly to align with the text line
            graphics.fakeItem(new ItemStack(Items.IRON_PICKAXE), textX - 2, textY - 4);
            textY += font.lineHeight + 3;

            // Second entry: To Place (Brick block)
            graphics.fakeItem(new ItemStack(Items.BRICKS), textX - 2, textY - 4);

            graphics.disableScissor();
        }
    }
}
