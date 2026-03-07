package buildcraft.lib.client.guide.parts;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.item.ItemStack;

import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntryItemStack;
import buildcraft.lib.client.guide.entry.PageValue;

@Deprecated
public class GuidePageStandInRecipes extends GuidePage {
    public GuidePageStandInRecipes(GuiGuide gui, List<GuidePart> parts, ItemStack stack) {
        super(gui, parts, new PageValue<>(PageEntryItemStack.INSTANCE, new ItemStackValueFilter(stack)));
    }

    @Nonnull
    public static GuidePageFactory createFactory(@Nonnull ItemStack stack) {
        // XmlPageLoader.loadAllCrafting not ported — return simple "No recipes" page
        return (gui) -> new GuidePageStandInRecipes(gui, ImmutableList.of(new GuideText(gui, "No recipes!")), stack);
    }

    @Override
    public boolean shouldPersistHistory() {
        return false;
    }

    @Override
    public GuidePageBase createReloaded() {
        return this;
    }
}
