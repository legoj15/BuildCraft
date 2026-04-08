package buildcraft.lib.client.guide.parts.contents;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.lib.client.guide.PageLine;
import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntryItemStack;
import buildcraft.lib.client.guide.entry.PageValue;
import buildcraft.lib.client.guide.parts.GuidePage;
import buildcraft.lib.client.guide.parts.GuidePageFactory;
import buildcraft.lib.client.guide.parts.GuidePart;
import buildcraft.lib.misc.ItemStackKey;

/** Like {@link PageLinkItemStack} but contains hundreds of different permutations of different items. */
public final class PageLinkItemPermutations extends PageLink {

    private final List<ItemStack> permutations;

    private PageLinkItemPermutations(PageLine text, boolean startVisible, List<ItemStack> permutations) {
        super(text, startVisible);
        this.permutations = permutations;
    }

    @Override
    public GuidePageFactory getFactoryLink() {
        return gui -> {
            List<GuidePart> parts = new ArrayList<>();
            // ProfilerFiller constructor not directly instantiable in 1.21 — use stub
            // Will be properly profiled when GuideManager is ported
            for (ItemStack stack : permutations) {
                // Skip profiling for now
            }
            ItemStackValueFilter filter = new ItemStackValueFilter(new ItemStackKey(permutations.get(0)), false, false);
            return new GuidePage(gui, parts, new PageValue<>(PageEntryItemStack.INSTANCE, filter));
        };
    }

    public static PageLinkItemPermutations create(boolean startVisible, List<ItemStack> stacks, ProfilerFiller prof) {
        PageLinkItemStack link = PageLinkItemStack.create(startVisible, stacks.get(0), prof);
        return new PageLinkItemPermutations(link.text, startVisible, stacks);
    }
}
