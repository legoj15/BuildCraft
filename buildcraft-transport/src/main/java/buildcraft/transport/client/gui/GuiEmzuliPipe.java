package buildcraft.transport.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.misc.ColourUtil;

import buildcraft.transport.container.ContainerEmzuliPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourEmzuli.SlotIndex;

public class GuiEmzuliPipe extends GuiBC8<ContainerEmzuliPipe> {
    private static final Identifier TEXTURE =
            Identifier.parse("buildcrafttransport:textures/gui/pipe_emzuli.png");
    private static final int SIZE_X = 176, SIZE_Y = 166;
    private static final GuiIcon ICON_GUI = new GuiIcon(TEXTURE, 0, 0, SIZE_X, SIZE_Y);

    public GuiEmzuliPipe(ContainerEmzuliPipe menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, SIZE_X, SIZE_Y);
    }

    @Override
    protected void drawBackgroundTexture(GuiGraphicsExtractor graphics) {
        ICON_GUI.drawAt(mainGui.rootElement);
    }

    @Override
    protected void initGuiElements() {
        addPaintButton(SlotIndex.SQUARE, 49, 19);
        addPaintButton(SlotIndex.CIRCLE, 49, 47);
        addPaintButton(SlotIndex.TRIANGLE, 106, 19);
        addPaintButton(SlotIndex.CROSS, 106, 47);
    }

    private void addPaintButton(SlotIndex index, int x, int y) {
        int bx = leftPos + x;
        int by = topPos + y;
        Button button = Button.builder(Component.literal(getColourLabel(index)), b -> {
            DyeColor current = menu.behaviour.slotColours.get(index);
            DyeColor next = cycleColour(current);
            menu.paintWidgets.get(index).setColour(next);
            // Update local display immediately
            if (next == null) {
                menu.behaviour.slotColours.remove(index);
            } else {
                menu.behaviour.slotColours.put(index, next);
            }
            b.setMessage(Component.literal(getColourLabel(index)));
        }).bounds(bx, by, 20, 20).build();
        addRenderableWidget(button);
    }

    /** Cycle to the next colour (or null for "no paint"). null → WHITE → ORANGE → ... → BLACK → null */
    private static DyeColor cycleColour(DyeColor current) {
        if (current == null) return DyeColor.WHITE;
        int next = current.ordinal() + 1;
        if (next >= 16) return null;
        return DyeColor.byId(next);
    }

    /** Short label for the paint button showing the current colour. */
    private String getColourLabel(SlotIndex index) {
        DyeColor colour = menu.behaviour.slotColours.get(index);
        if (colour == null) return "—";
        // First letter of the colour name
        String name = ColourUtil.getTextFullTooltip(colour);
        return name.length() > 2 ? name.substring(0, 2) : name;
    }

    @Override
    protected void renderLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String title = Component.translatable("gui.pipes.emzuli.title").getString();
        int titleX = (imageWidth - font.width(title)) / 2;
        graphics.text(font, title, titleX, 6, 0x404040, false);

        graphics.text(font, Component.translatable("gui.inventory").getString(),
            8, imageHeight - 93, 0x404040, false);

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
