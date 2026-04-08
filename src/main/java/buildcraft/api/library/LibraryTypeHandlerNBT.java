package buildcraft.api.library;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public abstract class LibraryTypeHandlerNBT extends LibraryTypeHandler {
    public LibraryTypeHandlerNBT(String extension) {
        super(extension);
    }

    public abstract ItemStack load(ItemStack stack, CompoundTag nbt);

    public abstract boolean store(ItemStack stack, CompoundTag nbt);
}

