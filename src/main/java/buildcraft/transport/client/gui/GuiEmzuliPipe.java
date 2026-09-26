package buildcraft.transport.client.gui;

import java.util.EnumMap;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import buildcraft.core.BCCoreItems;
import buildcraft.core.item.ItemPaintbrush_BC8;
import buildcraft.lib.gui.BCGraphics;
import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.button.BCButton;
import buildcraft.lib.gui.button.ButtonIcon;
import buildcraft.lib.gui.button.ButtonSprite;
import buildcraft.lib.gui.button.MouseButtons;
import buildcraft.lib.gui.help.DummyHelpElement;
import buildcraft.lib.gui.help.ElementHelpInfo;
import buildcraft.lib.gui.pos.GuiRectangle;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.container.ContainerEmzuliPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

public class GuiEmzuliPipe extends GuiBC8<ContainerEmzuliPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/gui/pipe_emzuli.png");
    private static final int SIZE_X = 176, SIZE_Y = 166;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);
    private static final int PAINT_BUTTON = 20;
    private static final ButtonIcon NO_PAINT_ICON = ButtonIcon.sprite(ButtonSprite.NO_PAINT);

    /** Pre-coloured paintbrush stacks for the paint buttons' icons, made on first use (item components are bound
     *  by then) — the brush is drawn through its item model, so resource packs apply. */
    private final EnumMap<DyeColor, ItemStack> brushIcons = new EnumMap<>(DyeColor.class);

    public GuiEmzuliPipe(ContainerEmzuliPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(BCGraphics graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    // Filter slot positions (16×16) — match ContainerEmzuliPipe.
    private static final int[][] FILTER_SLOTS = { {25, 21}, {25, 49}, {134, 21}, {134, 49} };
    // Paint button positions (20×20), indexed by SlotIndex ordinal (SQUARE, CIRCLE, TRIANGLE, CROSS).
    private static final int[][] PAINT_BUTTONS = { {49, 19}, {49, 47}, {106, 19}, {106, 47} };

    @Override
    protected void initGuiElements() {
        for (SlotIndex index : SlotIndex.VALUES) {
            addPaintButton(index, PAINT_BUTTONS[index.ordinal()][0], PAINT_BUTTONS[index.ordinal()][1]);
        }

        for (int[] pos : FILTER_SLOTS) {
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(pos[0], pos[1], 16, 16).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.emzuli.filter.title", 0xFF_88_CC_FF,
                            "buildcraft.help.emzuli.filter.desc")));
        }
        for (int[] pos : PAINT_BUTTONS) {
            mainGui.shownElements.add(new DummyHelpElement(
                    new GuiRectangle(pos[0], pos[1], 20, 20).offset(mainGui.rootElement),
                    new ElementHelpInfo("buildcraft.help.emzuli.paint.title", 0xFF_DD_AA_FF,
                            "buildcraft.help.emzuli.paint.desc1",
                            "buildcraft.help.emzuli.paint.desc2")));
        }
    }

    /** Left click steps the slot's colour forward (no colour → white → … → black → no colour), right click steps
     *  it back, middle click clears it — 1.12.2's paint buttons. */
    private void addPaintButton(SlotIndex index, int x, int y) {
        addRenderableWidget(BCButton.builder(leftPos + x, topPos + y, PAINT_BUTTON, PAINT_BUTTON)
            .icon(ButtonIcon.item(() -> brushIcon(menu.behaviour.slotColours.get(index)))
                .with(ButtonIcon.dynamic(() -> menu.behaviour.slotColours.get(index) == null ? NO_PAINT_ICON : null)))
            .tooltip(() -> {
                DyeColor colour = menu.behaviour.slotColours.get(index);
                return colour == null
                    ? Component.translatable("gui.pipes.emzuli.nopaint")
                    : Component.translatable("gui.pipes.emzuli.paint", ColourUtil.getTextFullTooltip(colour));
            })
            .onPress(MouseButtons.ALL, mouseButton -> paint(index, mouseButton))
            .build());
    }

    private void paint(SlotIndex index, int mouseButton) {
        DyeColor current = menu.behaviour.slotColours.get(index);
        DyeColor next = switch (mouseButton) {
            case 0 -> ColourUtil.getNextOrNull(current);
            case 1 -> ColourUtil.getPrevOrNull(current);
            default -> null;
        };
        menu.paintWidgets.get(index).setColour(next);
        // Optimistic local copy so the icon and tooltip change this frame; the server's resync agrees.
        if (next == null) {
            menu.behaviour.slotColours.remove(index);
        } else {
            menu.behaviour.slotColours.put(index, next);
        }
    }

    private ItemStack brushIcon(@Nullable DyeColor colour) {
        if (colour == null) {
            return ItemStack.EMPTY;
        }
        return brushIcons.computeIfAbsent(colour,
            c -> ItemPaintbrush_BC8.createColoredStack(BCCoreItems.PAINTBRUSH.get(), c));
    }

    @Override
    protected void drawForegroundLayer() {
        BCGraphics graphics = GuiIcon.getGuiGraphics();

        String titleStr = Component.translatable("gui.pipes.emzuli.title").getString();
        int titleX = (imageWidth - font.width(titleStr)) / 2;
        graphics.text(font, titleStr, titleX, 6, 0xFF404040, false);

        graphics.text(font, playerInventoryTitle, 8, imageHeight - 93, 0xFF404040, false);

        // Draw active slot indicators
        SlotIndex currentSlot = menu.behaviour.getCurrentSlot();
        for (SlotIndex index : menu.behaviour.getActiveSlots()) {
            boolean current = index == currentSlot;
            int ix = (index.ordinal() < 2 ? 4 : 155);
            int iy = (index.ordinal() % 2 == 0 ? 21 : 49);
            int colour = current ? 0xFF00FF00 : 0xFFFFFF00;
            graphics.fill(ix, iy, ix + 4, iy + 16, colour);
        }
    }
}
