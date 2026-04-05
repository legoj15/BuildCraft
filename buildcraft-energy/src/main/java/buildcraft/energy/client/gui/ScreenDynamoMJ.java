package buildcraft.energy.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerDynamoMJ;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.misc.LocaleUtil;

public class ScreenDynamoMJ extends GuiBC8<ContainerDynamoMJ> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/mj_dynamo_gui.png");
    private static final int SIZE_X = 176, SIZE_Y = 166;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public ScreenDynamoMJ(ContainerDynamoMJ menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.dynamo != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.dynamo != null ? menu.dynamo.getOwner() : null,
                true
            ));

            mainGui.shownElements.add(new LedgerEngine(mainGui,
                menu::getSyncedCurrentOutput,
                menu::getSyncedPower, // For Dynamo this is the mj battery stored
                menu::getSyncedHeat,
                menu::getSyncedPowerStage,
                menu::isSyncedBurningEngine,
                true
            ));

            mainGui.shownElements.add(new LedgerHelp(mainGui, false));
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        String str = title.getString();
        int strWidth = font.width(str);
        int titleX = (imageWidth - strWidth) / 2;
        graphics.text(font, str, titleX, 6, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }
}
