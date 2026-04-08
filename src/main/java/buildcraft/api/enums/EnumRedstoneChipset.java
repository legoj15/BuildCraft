package buildcraft.api.enums;

import java.util.Locale;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.StringRepresentable;

import buildcraft.api.BCItems;

public enum EnumRedstoneChipset implements StringRepresentable {
    RED,
    IRON,
    GOLD,
    QUARTZ,
    DIAMOND;

    private final String name = name().toLowerCase(Locale.ROOT);

    public ItemStack getStack(int stackSize) {
        Item chipset = BCItems.Silicon.REDSTONE_CHIPSET;
        if (chipset == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(chipset, stackSize);
    }

    public ItemStack getStack() {
        return getStack(1);
    }

    public static EnumRedstoneChipset fromStack(ItemStack stack) {
        if (stack == null) {
            return RED;
        }
        return fromOrdinal(0);
    }

    public static EnumRedstoneChipset fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return RED;
        }
        return values()[ordinal];
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
