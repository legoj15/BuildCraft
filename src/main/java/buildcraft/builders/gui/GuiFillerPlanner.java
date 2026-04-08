package buildcraft.builders.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import buildcraft.lib.gui.GuiBC8;
import buildcraft.builders.container.ContainerFillerPlanner;

public class GuiFillerPlanner extends GuiBC8<ContainerFillerPlanner> {
    public GuiFillerPlanner(ContainerFillerPlanner container, Inventory inv, Component title) {
        super(container, inv, title);
        // TODO: The full JSON-based GUI from 1.12 requires BuildCraftJsonGui,
        // FunctionContext, SpriteDelegate, TypedKeyMap, IButtonBehaviour, etc.
        // These are not yet ported. For now this is a minimal stub.
    }

    @Override
    protected void initGuiElements() {
        // TODO: Add filler planner GUI elements when JSON GUI framework is ported
    }
}
