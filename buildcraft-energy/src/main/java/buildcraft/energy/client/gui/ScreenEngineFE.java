package buildcraft.energy.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.energy.container.ContainerEngineFE;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.LedgerEngine;
import buildcraft.lib.gui.ledger.LedgerHelp;
import buildcraft.lib.gui.ledger.LedgerOwnership;
import buildcraft.lib.misc.LocaleUtil;

public class ScreenEngineFE extends GuiBC8<ContainerEngineFE> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftenergy:textures/gui/rf_engine_gui.png");
    private static final int SIZE_X = 176, SIZE_Y = 177;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final buildcraft.lib.gui.pos.GuiRectangle RECT_UPGRADE_TYPES = new buildcraft.lib.gui.pos.GuiRectangle(42, 20, 74, 20);

    public ScreenEngineFE(ContainerEngineFE menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void initGuiElements() {
        if (menu.engine != null) {
            mainGui.shownElements.add(new LedgerOwnership(mainGui,
                () -> menu.engine != null ? menu.engine.getOwner() : null,
                true
            ));

            mainGui.shownElements.add(new LedgerEngine(mainGui,
                menu::getSyncedCurrentOutput,
                menu::getSyncedPower,
                menu::getSyncedHeat,
                menu::getSyncedPowerStage,
                menu::isSyncedBurningEngine,
                true
            ));

            mainGui.shownElements.add(new LedgerHelp(mainGui, false));
            
            mainGui.shownElements.add(new buildcraft.lib.gui.GuiElementSimple(mainGui, RECT_UPGRADE_TYPES.offset(mainGui.rootElement)) {
                @Override
                public void addToolTips(java.util.List<buildcraft.lib.gui.elem.ToolTip> tooltips) {
                    if (contains(mainGui.mouse)) {
                        java.util.List<String> lines = new java.util.ArrayList<>();
                        lines.add(LocaleUtil.localize("buildcraft.gui.rf_engine.upgrade_types"));
                        buildcraft.energy.tile.TileEngineFE.initUpgrades();
                        for (java.util.Map.Entry<net.minecraft.world.item.Item, Long> entry : buildcraft.energy.tile.TileEngineFE.UPGRADE_VALUES.entrySet()) {
                            String itemName = new net.minecraft.world.item.ItemStack(entry.getKey()).getHoverName().getString();
                            long mj = entry.getValue();
                            int rfPerTick = (int) (mj / 100000L); // 100,000 microMJ = 1 RF fallback
                            lines.add(itemName + " = +" + (rfPerTick * 20) + " Redstone Flux per second");
                        }
                        tooltips.add(new buildcraft.lib.gui.elem.ToolTip(lines));
                    }
                }
            });
        }
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
        
        int x = (int) mainGui.rootElement.getX();
        int y = (int) mainGui.rootElement.getY();
        
        graphics.blit(
            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE,
            x + 58, y + 18,
            39.0f, 18.0f,
            80, 23,
            80, 23,
            256, 256,
            0xA5FFFFFF
        );
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
