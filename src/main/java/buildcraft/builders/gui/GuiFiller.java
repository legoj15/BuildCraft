package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.input.MouseButtonEvent;

import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.core.render.ISprite;

import buildcraft.builders.container.ContainerFiller;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.statement.GuiElementStatement;
import buildcraft.lib.gui.statement.GuiElementStatementParam;
import buildcraft.lib.gui.statement.GuiElementStatementSource;
import buildcraft.lib.gui.statement.GuiElementStatementDrag;
import buildcraft.lib.gui.ledger.LedgerHelp;

public class GuiFiller extends GuiBC8<ContainerFiller> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/filler.png");

    private static final GuiIcon BUTTON_EXCAVATE_ON = new GuiIcon(TEXTURE, 192, 0, 16, 16);
    private static final GuiIcon BUTTON_EXCAVATE_OFF = new GuiIcon(TEXTURE, 208, 0, 16, 16);
    private static final GuiIcon BUTTON_INVERT_ON = new GuiIcon(TEXTURE, 224, 0, 16, 16);
    private static final GuiIcon BUTTON_INVERT_OFF = new GuiIcon(TEXTURE, 240, 0, 16, 16);

    public GuiFiller(ContainerFiller container, Inventory playerInv, Component title) {
        super(container, playerInv, Component.translatable("tile.fillerBlock.name"), 176, 241);
    }

    @Override
    protected void initGuiElements() {
        mainGui.shownElements.add(new GuiElementStatementDrag(mainGui));
        
        mainGui.shownElements.add(new LedgerHelp(mainGui, false));
        
        mainGui.shownElements.add(new LedgerFillerProgress(mainGui, menu));

        mainGui.shownElements.add(new GuiElementStatementSource<>(mainGui, true, menu.possiblePatternsContext));

        IGuiArea patternArea = new GuiRectangle(12, 32, 32, 32).offset(mainGui.rootElement);
        mainGui.shownElements.add(new GuiElementStatement<>(mainGui, patternArea, menu.getPatternStatement(), menu.possiblePatternsContext, true) {
            @Override
            public void drawBackground(float partialTicks) {
                IFillerPattern statement = this.get();
                if (statement != null) {
                    ISprite sprite = statement.getSprite();
                    if (sprite != null) {
                        double x = getX();
                        double y = getY();
                        GuiIcon.drawAt(sprite, x, y, 32);
                    }
                }
            }
        });

        buildcraft.api.statements.IStatementContainer fakeContainer = new buildcraft.api.statements.IStatementContainer() {
            @Override public net.minecraft.world.level.block.entity.BlockEntity getTile() { return null; }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getNeighbourTile(net.minecraft.core.Direction side) { return null; }
        };

        for (int i = 0; i < 4; i++) {
            IGuiArea paramArea = new GuiRectangle(53 + 18 * i, 39, 18, 18).offset(mainGui.rootElement);
            mainGui.shownElements.add(new GuiElementStatementParam(mainGui, paramArea, fakeContainer, menu.getPatternStatementClient(), i, true));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int mx = (int) this.mainGui.mouse.getX() - leftPos;
        int my = (int) this.mainGui.mouse.getY() - topPos;

        boolean excavateHover = mx >= 130 && mx < 146 && my >= 40 && my < 56;
        boolean invertHover = mx >= 152 && mx < 168 && my >= 40 && my < 56;

        int excavateU = 192 + (menu.getSyncedCanExcavate() ? 16 : 0);
        int excavateV = excavateHover ? 16 : 0;
        int invertU = 224 + (menu.isInverted() ? 16 : 0);
        int invertV = invertHover ? 16 : 0;

        new GuiIcon(TEXTURE, excavateU, excavateV, 16, 16).drawAt(leftPos + 130, topPos + 40);
        new GuiIcon(TEXTURE, invertU, invertV, 16, 16).drawAt(leftPos + 152, topPos + 40);
        
        if (menu.getSyncedLocked()) {
            new GuiIcon(Identifier.parse("buildcraftunofficial:textures/gui/icons/lock.png"), 0, 0, 16, 16).drawAt(leftPos + 12, topPos + 16);
        }
    }
    
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (mainGui.currentMenu == null || !mainGui.currentMenu.shouldFullyOverride()) {
            String titleStr = Component.translatable("tile.fillerBlock.name").getString();
            graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 10, 0xFF404040, false);
            graphics.text(font, Component.translatable("gui.filling.resources").getString(), 7, 74, 0xFF404040, false);
            graphics.text(font, Component.translatable("gui.inventory").getString(), 7, 141, 0xFF404040, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() == 0) {
            int mx = (int) event.x() - leftPos;
            int my = (int) event.y() - topPos;

            if (mx >= 130 && mx < 146 && my >= 40 && my < 56) {
                menu.sendMessage(ContainerFiller.NET_EXCAVATE, (buf) -> {});
                
                if (this.minecraft.player != null) {
                    this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                }
                return true;
            }

            if (mx >= 152 && mx < 168 && my >= 40 && my < 56) {
                menu.sendMessage(ContainerFiller.NET_INVERT, (buf) -> {});
                
                if (this.minecraft.player != null) {
                    this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                }
                return true;
            }
        }
        return false;
    }
}
