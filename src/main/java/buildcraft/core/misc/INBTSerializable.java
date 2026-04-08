package buildcraft.core.misc;

import net.minecraft.nbt.Tag;

/**
 * Compatibility stub for the removed
 * net.neoforged.neoforge.common.util.INBTSerializable.
 * In NeoForge 1.21.x this interface was dropped; classes now use custom
 * serialization patterns.
 */
public interface INBTSerializable<T extends Tag> {
    T serializeNBT();

    void deserializeNBT(T nbt);
}
