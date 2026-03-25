package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.builders.container.ContainerFiller;

public class GuiFiller extends AbstractContainerScreen<ContainerFiller> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftbuilders:textures/gui/filler.png");

    public GuiFiller(ContainerFiller container, Inventory playerInv, Component title) {
        super(container, playerInv, title, 176, 235);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos,
                0f, 0f,
                imageWidth, imageHeight,
                256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }
}
