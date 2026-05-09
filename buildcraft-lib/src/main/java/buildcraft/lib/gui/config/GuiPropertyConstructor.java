package buildcraft.lib.gui.config;

import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface GuiPropertyConstructor {
    GuiProperty create(String name);
}
