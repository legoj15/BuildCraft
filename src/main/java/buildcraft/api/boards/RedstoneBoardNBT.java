/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.boards;

import java.util.List;
import java.util.Random;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.nbt.Tag;

public abstract class RedstoneBoardNBT<T> {

    private static Random rand = new Random();

    public abstract String getID();

    /** Hover text contributed for stacks carrying this board, one string per tooltip line. 7.1.x pushed
     *  formatting through {@code EnumChatFormatting} prefixes inside these strings (its robot boards led the
     *  name line with {@code BOLD}); those legacy codes render correctly from the literal components the
     *  tooltip consumers wrap these in, so that mechanism is kept verbatim rather than restyled. The
     *  consumers also style every board line with {@code GRAY}: 1.7.10's {@code RenderItem.renderToolTip}
     *  applied it to all lines past the item's name, and modern MC dropped that default. */
    public abstract void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced);

    public abstract String getDisplayName();

    public abstract IRedstoneBoard<T> create(CompoundTag nbt, T object);

    public void createBoard(CompoundTag nbt) {
        nbt.putString("id", getID());
    }

    public int getParameterNumber(CompoundTag nbt) {
        if (!nbt.contains("parameters")) {
            return 0;
        } else {
            return 0;
        }
    }

    public float nextFloat(int difficulty) {
        return 1F - (float) Math.pow(rand.nextFloat(), 1F / difficulty);
    }
}

