package buildcraft.silicon.gate;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import buildcraft.lib.misc.NBTUtilBC;

public class GateVariant {
    public final EnumGateLogic logic;
    public final EnumGateMaterial material;
    public final EnumGateModifier modifier;
    public final int numSlots;
    public final int numTriggerArgs, numActionArgs;
    private final int hash;

    public GateVariant(EnumGateLogic logic, EnumGateMaterial material, EnumGateModifier modifier) {
        this.logic = logic;
        this.material = material;
        this.modifier = modifier;
        this.numSlots = material.numSlots / modifier.slotDivisor;
        this.numTriggerArgs = modifier.triggerParams;
        this.numActionArgs = modifier.actionParams;
        this.hash = Objects.hash(logic, material, modifier);
    }

    public GateVariant(CompoundTag nbt) {
        this.logic = EnumGateLogic.getByOrdinal(NBTUtilBC.getByte(nbt, "logic", (byte) 0));
        this.material = EnumGateMaterial.getByOrdinal(NBTUtilBC.getByte(nbt, "material", (byte) 0));
        this.modifier = EnumGateModifier.getByOrdinal(NBTUtilBC.getByte(nbt, "modifier", (byte) 0));
        this.numSlots = material.numSlots / modifier.slotDivisor;
        this.numTriggerArgs = modifier.triggerParams;
        this.numActionArgs = modifier.actionParams;
        this.hash = Objects.hash(logic, material, modifier);
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putByte("logic", (byte) logic.ordinal());
        nbt.putByte("material", (byte) material.ordinal());
        nbt.putByte("modifier", (byte) modifier.ordinal());
        return nbt;
    }

    public GateVariant(FriendlyByteBuf buffer) {
        this.logic = EnumGateLogic.getByOrdinal(buffer.readUnsignedByte());
        this.material = EnumGateMaterial.getByOrdinal(buffer.readUnsignedByte());
        this.modifier = EnumGateModifier.getByOrdinal(buffer.readUnsignedByte());
        this.numSlots = material.numSlots / modifier.slotDivisor;
        this.numTriggerArgs = modifier.triggerParams;
        this.numActionArgs = modifier.actionParams;
        this.hash = Objects.hash(logic, material, modifier);
    }

    public void writeToBuffer(FriendlyByteBuf buffer) {
        buffer.writeByte(logic.ordinal());
        buffer.writeByte(material.ordinal());
        buffer.writeByte(modifier.ordinal());
    }

    public String getVariantName() {
        if (material.canBeModified) {
            return material.tag + "_" + logic.tag + "_" + modifier.tag;
        } else {
            return material.tag;
        }
    }

    public Component getLocalizedName() {
        if (material == EnumGateMaterial.CLAY_BRICK) {
            return Component.translatable("gate.name.basic");
        } else {
            return Component.translatable("gate.name",
                    Component.translatable("gate.material." + material.tag),
                    Component.translatable("gate.logic." + logic.tag));
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        GateVariant other = (GateVariant) obj;
        return other.logic == logic//
            && other.material == material//
            && other.modifier == modifier;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
