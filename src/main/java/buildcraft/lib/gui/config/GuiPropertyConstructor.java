package buildcraft.lib.gui.config;

import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface GuiPropertyConstructor {
    GuiProperty create(String name);
}
