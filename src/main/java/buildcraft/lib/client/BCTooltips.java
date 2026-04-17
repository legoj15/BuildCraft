package buildcraft.lib.client;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class BCTooltips {

    private static final Map<net.minecraft.world.item.Item, String> TOOLTIPS = new IdentityHashMap<>();

    public static void addTooltip(ItemLike item, String translationKey) {
        TOOLTIPS.put(item.asItem(), translationKey);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        String key = TOOLTIPS.get(event.getItemStack().getItem());
        if (key != null) {
            event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
