package buildcraft.silicon.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;

import buildcraft.silicon.container.ContainerGate;

public class GuiGate extends GuiBC8<ContainerGate> {
    private static final Identifier TEXTURE_BASE = Identifier.parse("buildcraftsilicon:textures/gui/gate_interface.png");
    
    // Dynamic height construction
    private static final GuiIcon BACKGROUND_TOP = new GuiIcon(TEXTURE_BASE, 0, 0, 176, 16);
    private static final GuiIcon BACKGROUND_BOTTOM = new GuiIcon(TEXTURE_BASE, 0, 48, 176, 101);
    private static final GuiIcon BACKGROUND_ROW = new GuiIcon(TEXTURE_BASE, 0, 23, 176, 18);
    
    private final int numRows;
    
    public GuiGate(ContainerGate container, Inventory playerInventory, Component title) {
        // Compute total GUI size based on number of slots (slotHeight maps to number of rows drawn)
        super(container, playerInventory, title, 176, 16 + 101 + container.slotHeight * 18);
        this.numRows = container.slotHeight;
    }

    @Override
    protected void initGuiElements() {
    }

    @Override
    protected void init() {
        super.init();
        
        // Request statements from the server
        menu.requestValidStatements();
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        // Draw top header
        BACKGROUND_TOP.drawAt(leftPos, topPos);

        // Draw middle dynamic rows
        for (int i = 0; i < numRows; i++) {
            BACKGROUND_ROW.drawAt(leftPos, topPos + 16 + i * 18);
        }

        // Draw bottom player inventory background
        BACKGROUND_BOTTOM.drawAt(leftPos, topPos + 16 + numRows * 18);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Center the gate name at the top
        String titleStr = menu.gate.variant.getLocalizedName().getString();
        graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 6, 0xFF404040, false);
        
        // Offset the 'Inventory' label down into the correct place
        String invStr = Component.translatable("gui.inventory").getString();
        graphics.text(font, invStr, 8, 16 + numRows * 18 + 4, 0xFF404040, false);

        // TODO: Map in statement triggers / action buttons via GuiButton and statement system
    }
}
