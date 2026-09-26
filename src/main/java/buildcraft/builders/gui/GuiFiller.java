package buildcraft.builders.gui;

import buildcraft.lib.gui.BCGraphics;
//? if >=1.21.10 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.api.filler.IFillerPattern;


import buildcraft.builders.container.ContainerFiller;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.button.BCButton;
import buildcraft.lib.gui.button.ButtonIcon;
import buildcraft.lib.gui.button.ButtonSprite;
import buildcraft.lib.gui.elem.ToolTip;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.gui.pos.IGuiArea;
import buildcraft.lib.gui.statement.GuiElementStatement;
import buildcraft.lib.gui.statement.GuiElementStatementParam;
import buildcraft.lib.gui.statement.GuiElementStatementSource;
import buildcraft.lib.gui.statement.GuiElementStatementDrag;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.help.ElementHelpInfo.HelpPosition;
import buildcraft.lib.misc.LocaleUtil;

import java.util.List;
import java.util.function.BooleanSupplier;

public class GuiFiller extends GuiBC8<ContainerFiller> {
    private static final Identifier TEXTURE = Identifier.parse("buildcraftunofficial:textures/gui/filler.png");
    /** Excavate and invert toggles: 16×16, GUI-local, on the pattern row (shared with {@link GuiFillerPlanner}). */
    static final int TOGGLE_SIZE = 16, TOGGLE_Y = 40, EXCAVATE_X = 130, INVERT_X = 152;

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

        // Pattern slot help. The statement element itself is interactive (drag / popup), so the help
        // info hangs off a non-drawing overlay on the same rect, matching GuiGate's statement slots.
        mainGui.shownElements.add(new DummyHelpElement(patternArea,
            new ElementHelpInfo("buildcraft.help.filler.pattern.title", 0xFF88CC88,
                "buildcraft.help.filler.pattern.desc1",
                "buildcraft.help.filler.pattern.desc2")));

        // Parameter slots
        buildcraft.api.statements.IStatementContainer fakeContainer = new buildcraft.api.statements.IStatementContainer() {
            @Override public net.minecraft.world.level.block.entity.BlockEntity getTile() { return null; }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getNeighbourTile(net.minecraft.core.Direction side) { return null; }
        };

        for (int i = 0; i < 4; i++) {
            IGuiArea paramArea = new GuiRectangle(53 + 18 * i, 39, 18, 18).offset(mainGui.rootElement);
            mainGui.shownElements.add(new GuiElementStatementParam(mainGui, paramArea, fakeContainer, menu.getPatternStatementClient(), i, true));
        }

        // One help region framing the whole 4-slot parameter row (not one per slot), matching how
        // GuiGate frames its trigger/action parameter runs.
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(53, 39, 4 * 18, 18).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.filler.params.title", 0xFFDDAAFF,
                "buildcraft.help.filler.params.desc")));

        // Excavate and invert toggles: the icon is the state, the tooltip names it.
        addRenderableWidget(BCButton.builder(leftPos + EXCAVATE_X, topPos + TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE)
            .icon(ButtonIcon.either(menu::getSyncedCanExcavate, ButtonSprite.EXCAVATE_ON, ButtonSprite.EXCAVATE_OFF))
            .tooltip(() -> Component.translatable(menu.getSyncedCanExcavate()
                ? "tip.filler.excavate.on" : "tip.filler.excavate.off"))
            .onPress(() -> menu.sendMessage(ContainerFiller.NET_EXCAVATE, buf -> {}))
            .build());
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(EXCAVATE_X, TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.filler.excavate.title", 0xFFCCAA88, "buildcraft.help.filler.excavate.desc")));

        addRenderableWidget(invertButton(leftPos + INVERT_X, topPos + TOGGLE_Y, menu::isInverted,
            () -> menu.sendMessage(ContainerFiller.NET_INVERT, buf -> {})));
        mainGui.shownElements.add(new DummyHelpElement(
            new GuiRectangle(INVERT_X, TOGGLE_Y, TOGGLE_SIZE, TOGGLE_SIZE).offset(mainGui.rootElement),
            new ElementHelpInfo("buildcraft.help.filler.invert.title", 0xFFCCAA88, "buildcraft.help.filler.invert.desc")));

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
                        // Same key as this element's help title below — the tooltip and the ledger
                        // header name the same state, so they must never drift apart again.
                        tooltips.add(new ToolTip(LocaleUtil.localize("buildcraft.help.filler.locked.title")));
                    }
                }
            }

            @Override
            public void addHelpElements(List<HelpPosition> elements) {
                elements.add(new ElementHelpInfo("buildcraft.help.filler.locked.title", 0xFFFFBB33, "buildcraft.help.filler.locked.desc").target(this));
            }
        });
    }

    /** The Filler's and Filler Planner's shared invert toggle: solid square = pattern as drawn, hollow = inverted. */
    static BCButton invertButton(int x, int y, BooleanSupplier inverted, Runnable onToggle) {
        return BCButton.builder(x, y, TOGGLE_SIZE, TOGGLE_SIZE)
            .icon(ButtonIcon.either(inverted, ButtonSprite.INVERT_ON, ButtonSprite.INVERT_OFF))
            .tooltip(() -> Component.translatable(inverted.getAsBoolean() ? "tip.filler.invert.on" : "tip.filler.invert.off"))
            .onPress(onToggle)
            .build();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        menu.getPatternStatementClient().canInteract = !menu.getSyncedLocked();
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        //? if >=1.21.10 {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        //?} else {
        /*graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);*/
        //?}

        if (menu.getSyncedLocked()) {
            new GuiIcon(Identifier.parse("buildcraftunofficial:textures/icons/lock.png"), 0, 0, 16, 16, 16).drawAt(leftPos + 12, topPos + 16);
        }
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();
        // After super, the pose is back to ACS-translated space (0,0 = GUI top-left).
        // Always draw the labels: the full-override pattern popup renders at a higher stratum
        // (drawMenuOverlayLayer) and dims this text on top, matching 1.12.2's layered look.
        String titleStr = Component.translatable("block.buildcraftunofficial.filler").getString();
        graphics.text(font, titleStr, (imageWidth - font.width(titleStr)) / 2, 10, 0xFF404040, false);
        graphics.text(font, Component.translatable("gui.filling.resources").getString(), 7, 74, 0xFF404040, false);
        graphics.text(font, Component.translatable("container.inventory").getString(), 7, 141, 0xFF404040, false);
    }
}
