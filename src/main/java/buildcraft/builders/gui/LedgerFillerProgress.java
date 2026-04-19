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
    }

    @Override
    protected void calculateMaxSize() {
        super.calculateMaxSize();
        this.maxHeight = 60;
        this.maxWidth = 100;
    }

    @Override
    protected void drawIcon(double x, double y, GuiGraphicsExtractor graphics) {
        graphics.fakeItem(new ItemStack(Items.IRON_INGOT), (int) x, (int) y);
    }

    @Override
    public void drawBackground(float partialTicks) {
        super.drawBackground(partialTicks);

        GuiGraphicsExtractor graphics = GuiIcon.getGuiGraphics();
        if (graphics == null) return;

        if (interpWidth > CLOSED_WIDTH + 10) {
            int scissorX = (int) getX() + 2; 
            int scissorY = (int) getY() + 4;
            int scissorW = (int) (interpWidth - LEDGER_GAP);
            int scissorH = (int) (interpHeight - LEDGER_GAP * 2);
            graphics.enableScissor(scissorX, scissorY, scissorX + scissorW, scissorY + scissorH);

            double iconX = getX() + 2;
            double iconY = getY() + 4;
            
            int textX = (int) iconX + 16 + LEDGER_GAP;
            int textY = (int) iconY + 1;

            Font font = Minecraft.getInstance().font;
            textY += font.lineHeight + 3; // Skip title

            // First entry: To Break (Iron Pickaxe)
            graphics.fakeItem(new ItemStack(Items.IRON_PICKAXE), textX, textY);
            graphics.text(font, String.valueOf(container.getSyncedToBreak()), textX + 20, textY + 4, 0xFF333333 | 0xFF000000, false);

            textY += 18;

            // Second entry: To Place (Brick block)
            graphics.fakeItem(new ItemStack(Items.BRICKS), textX, textY);
            graphics.text(font, String.valueOf(container.getSyncedToPlace()), textX + 20, textY + 4, 0xFF333333 | 0xFF000000, false);

            graphics.disableScissor();
        }
    }
}
