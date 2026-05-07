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
            // Prefer an RF-naming sibling (key + ".rf") if the user has the toggle on
            // AND that variant exists in the lang file. Falls back to the base key otherwise.
            String rfFeKey = buildcraft.energy.BCEnergyConfig.rfFeKey(key);
            String resolved = !rfFeKey.equals(key)
                    && net.minecraft.client.resources.language.I18n.exists(rfFeKey)
                    ? rfFeKey
                    : key;
            if (net.minecraft.client.resources.language.I18n.exists(resolved)) {
                String translated = net.minecraft.client.resources.language.I18n.get(resolved);
                for (String line : translated.split("\n")) {
                    event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
            } else {
                event.getToolTip().add(Component.translatable(resolved).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
