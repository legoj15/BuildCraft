package buildcraft.builders.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.input.MouseButtonEvent;

import buildcraft.api.filler.IFillerPattern;


import buildcraft.builders.container.ContainerFiller;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.statement.GuiElementStatement;
import buildcraft.lib.gui.statement.GuiElementStatementParam;
import buildcraft.lib.gui.statement.GuiElementStatementSource;
import buildcraft.lib.gui.statement.GuiElementStatementDrag;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition;
import buildcraft.lib.misc.LocaleUtil;

import java.util.List;

public class GuiFiller extends GuiBC8<ContainerFiller> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/filler.png");

    public GuiFiller(ContainerFiller container, Inventory playerInv, Component title) {
        super(container, playerInv, Component.translatable("block.buildcraftunofficial.filler"), 176, 241);
    }

    @Override
    protected void initGuiElements() {
        mainGui.shownElements.add(new GuiElementStatementDrag(mainGui));

        if (menu.tile != null) {
            mainGui.shownElements.add(new buildcraft.lib.gui.ledger.LedgerOwnership(mainGui,
                () -> menu.tile != null ? menu.tile.getOwner() : null,
                true
            ));
        }

        mainGui.shownElements.add(new LedgerFillerProgress(mainGui, menu));

        mainGui.shownElements.add(new GuiElementStatementSource<>(mainGui, true, menu.possiblePatternsContext));

        // Pattern slot — use patternStatementClient so set() triggers onStatementChange() → NET_STATEMENT
        IGuiArea patternArea = new GuiRectangle(12, 32, 32, 32).offset(mainGui.rootElement);
        mainGui.shownElements.add(new GuiElementStatement<>(mainGui, patternArea, menu.getPatternStatementClient(), menu.possiblePatternsContext, true) {
            @Override
            public void drawBackground(float partialTicks) {
                IFillerPattern statement = this.get();
                double x = getX();
                double y = getY();
                if (statement != null) {
                    buildcraft.api.core.render.ISprite sprite = statement.getSprite();
                    if (sprite != null) {
                        GuiIcon.drawAt(sprite, x, y, 32);
                    }
                } else {
                    // Draw the "not set" slot background at 32x32
                    GuiElementStatement.ICON_SLOT_NOT_SET.drawAt(x, y);
                }

                // Draw control mode icon (Loop/Off) from gates
                buildcraft.api.tiles.IControllable.Mode mode = menu.getSyncedMode();
                if (mode != buildcraft.api.tiles.IControllable.Mode.ON) {
                    buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder holder = buildcraft.core.BCCoreSprites.ACTION_MACHINE_CONTROL.get(mode);
                    if (holder != null) {
                        GuiIcon.drawAt(holder, x + 16, y - 16, 16);
                    }
                }
            }
        });

        // Parameter slots
        buildcraft.api.statements.IStatementContainer fakeContainer = new buildcraft.api.statements.IStatementContainer() {
            @Override public net.minecraft.world.level.block.entity.BlockEntity getTile() { return null; }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getNeighbourTile(net.minecraft.core.Direction side) { return null; }
        };

        for (int i = 0; i < 4; i++) {
            IGuiArea paramArea = new GuiRectangle(53 + 18 * i, 39, 18, 18).offset(mainGui.rootElement);
            mainGui.shownElements.add(new GuiElementStatementParam(mainGui, paramArea, fakeContainer, menu.getPatternStatementClient(), i, true));
        }

        // Excavate button tooltip element
        IGuiArea excavateArea = new GuiRectangle(130, 40, 16, 16).offset(mainGui.rootElement);
        mainGui.shownElements.add(new buildcraft.lib.gui.GuiElementSimple(mainGui, excavateArea) {
            @Override
            public void addToolTips(List<ToolTip> tooltips) {
                if (contains(mainGui.mouse)) {
                    String key = menu.getSyncedCanExcavate() ? "tip.filler.excavate.on" : "tip.filler.excavate.off";
                    tooltips.add(new ToolTip(LocaleUtil.localize(key)));
                }
            }

            @Override
            public void addHelpElements(List<HelpPosition> elements) {
                elements.add(new ElementHelpInfo("buildcraft.help.filler.excavate.title", 0xFFCCAA88, "buildcraft.help.filler.excavate.desc").target(this));
            }
        });

        // Invert button tooltip element
        IGuiArea invertArea = new GuiRectangle(152, 40, 16, 16).offset(mainGui.rootElement);
        mainGui.shownElements.add(new buildcraft.lib.gui.GuiElementSimple(mainGui, invertArea) {
            @Override
            public void addToolTips(List<ToolTip> tooltips) {
                if (contains(mainGui.mouse)) {
                    String key = menu.isInverted() ? "tip.filler.invert.on" : "tip.filler.invert.off";
                    tooltips.add(new ToolTip(LocaleUtil.localize(key)));
                }
            }

            @Override
            public void addHelpElements(List<HelpPosition> elements) {
                elements.add(new ElementHelpInfo("buildcraft.help.filler.invert.title", 0xFFCCAA88, "buildcraft.help.filler.invert.desc").target(this));
            }
        });

        // Mode icon tooltip element
        IGuiArea controlModeArea = new GuiRectangle(28, 16, 16, 16).offset(mainGui.rootElement);
        mainGui.shownElements.add(new buildcraft.lib.gui.GuiElementSimple(mainGui, controlModeArea) {
            @Override
            public void addToolTips(List<ToolTip> tooltips) {
                if (contains(mainGui.mouse)) {
                    buildcraft.api.tiles.IControllable.Mode mode = menu.getSyncedMode();
                    if (mode != buildcraft.api.tiles.IControllable.Mode.ON) {
                        String key = "gate.action.machine." + mode.name().toLowerCase(java.util.Locale.ROOT);
                        tooltips.add(new ToolTip(buildcraft.lib.misc.LocaleUtil.localize(key)));
                    }
                }
            }

            @Override
            public void addHelpElements(List<HelpPosition> elements) {
                elements.add(new ElementHelpInfo("buildcraft.help.filler.mode.title", 0xFF33BBFF, "buildcraft.help.filler.mode.desc1", "buildcraft.help.filler.mode.desc2").target(this));
            }
        });

        // Lock icon tooltip element
        IGuiArea lockArea = new GuiRectangle(12, 16, 16, 16).offset(mainGui.rootElement);
        mainGui.shownElements.add(new buildcraft.lib.gui.GuiElementSimple(mainGui, lockArea) {
            @Override
            public void addToolTips(List<ToolTip> tooltips) {
                if (contains(mainGui.mouse)) {
                    if (menu.getSyncedLocked()) {
                        tooltips.add(new ToolTip("Locked"));
                    }
                }
            }

            @Override
            public void addHelpElements(List<HelpPosition> elements) {
                elements.add(new ElementHelpInfo("buildcraft.help.filler.locked.title", 0xFFFFBB33, "buildcraft.help.filler.locked.desc").target(this));
            }
        });
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        menu.getPatternStatementClient().canInteract = !menu.getSyncedLocked();
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int mx = (int) this.mainGui.mouse.getX() - leftPos;
        int my = (int) this.mainGui.mouse.getY() - topPos;

        boolean excavateHover = mx >= 130 && mx < 146 && my >= 40 && my < 56;
        boolean invertHover = mx >= 152 && mx < 168 && my >= 40 && my < 56;

        // Excavate button: u_start=192, active adds +16 to U, hover adds +16 to V
        int excavateU = menu.getSyncedCanExcavate() ? 208 : 192;
        int excavateV = excavateHover ? 16 : 0;
        new GuiIcon(TEXTURE, excavateU, excavateV, 16, 16).drawAt(leftPos + 130, topPos + 40);

        // Invert button: u_start=224, active adds +16 to U, hover adds +16 to V
        int invertU = menu.isInverted() ? 240 : 224;
        int invertV = invertHover ? 16 : 0;
        new GuiIcon(TEXTURE, invertU, invertV, 16, 16).drawAt(leftPos + 152, topPos + 40);

        if (menu.getSyncedLocked()) {
            new GuiIcon(Identifier.parse("buildcraftunofficial:textures/icons/lock.png"), 0, 0, 16, 16, 16).drawAt(leftPos + 12, topPos + 16);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Let GuiBC8 handle element foregrounds (tooltips, ledgers, menus)
        super.extractLabels(graphics, mouseX, mouseY);
        // After super, the pose is back to ACS-translated space (0,0 = GUI top-left)
        if (mainGui.currentMenu == null || !mainGui.currentMenu.shouldFullyOverride()) {
            String titleStr = Component.translatable("block.buildcraftunofficial.filler").getString();
            graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 10, 0xFF404040, false);
            graphics.text(font, Component.translatable("gui.filling.resources").getString(), 7, 74, 0xFF404040, false);
            graphics.text(font, Component.translatable("container.inventory").getString(), 7, 141, 0xFF404040, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Handle button clicks BEFORE super so they aren't consumed by ACS slot handling
        if (event.button() == 0) {
            int mx = (int) event.x() - leftPos;
            int my = (int) event.y() - topPos;

            if (mx >= 130 && mx < 146 && my >= 40 && my < 56) {
                menu.sendMessage(ContainerFiller.NET_EXCAVATE, (buf) -> {});
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }

            if (mx >= 152 && mx < 168 && my >= 40 && my < 56) {
                menu.sendMessage(ContainerFiller.NET_INVERT, (buf) -> {});
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
