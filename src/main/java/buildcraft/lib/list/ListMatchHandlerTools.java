package buildcraft.lib.list;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.lists.ListMatchHandler;

public class ListMatchHandlerTools extends ListMatchHandler {
    
    @SuppressWarnings("unchecked")
    private static final TagKey<Item>[] TOOL_TAGS = new TagKey[]{
        ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS
    };

    @Override
    public boolean matches(Type type, @Nonnull ItemStack stack, @Nonnull ItemStack target, boolean precise) {
        if (type == Type.TYPE) {
            if (!isValidSource(type, stack) || !isValidSource(type, target)) {
                return false;
            }
            
            // In 1.21.11, "tool classes" are represented via Tags rather than string sets.
            for (TagKey<Item> tag : TOOL_TAGS) {
                if (stack.is(tag) && target.is(tag)) {
                    return true;
                }
            }
            
            // If they are tools but don't share a tool tag category, they don't match.
            return false;
        }
        return false;
    }

    @Override
    public boolean isValidSource(Type type, @Nonnull ItemStack stack) {
        for (TagKey<Item> tag : TOOL_TAGS) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    @Nonnull
    @Override
    public List<String> describeMatch(Type type, @Nonnull ItemStack stack) {
        if (type != Type.TYPE) return List.of();
        List<String> out = new ArrayList<>();
        for (TagKey<Item> tag : TOOL_TAGS) {
            if (stack.is(tag)) {
                out.add("#" + tag.location());
            }
        }
        return out;
    }
}
