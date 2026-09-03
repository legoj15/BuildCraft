/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. */
package buildcraft.api.statements;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import buildcraft.api.core.render.ISprite;

public class StatementParameterItemStack implements IStatementParameter {
    @Nonnull
    private static final ItemStack EMPTY_STACK;
    public static final StatementParameterItemStack EMPTY;

    static {
        ItemStack stack = ItemStack.EMPTY;
        if (stack == null)
            throw new Error("Somehow ItemStack.EMPTY was null!");
        EMPTY_STACK = stack;
        EMPTY = new StatementParameterItemStack();
    }

    @Nonnull
    protected final ItemStack stack;

    public StatementParameterItemStack() {
        stack = EMPTY_STACK;
    }

    public StatementParameterItemStack(@Nonnull ItemStack stack) {
        this.stack = stack;
    }

    public StatementParameterItemStack(CompoundTag nbt) {
        ItemStack read = ItemStack.EMPTY;
        Tag stackPayload = nbt.get("stack");
        if (stackPayload != null) {
            read = ItemStack.CODEC.parse(buildcraft.api.core.NbtApiUtil.registryAwareOps(), stackPayload)
                    .resultOrPartial()
                    .orElse(ItemStack.EMPTY);
        }
        stack = read.isEmpty() ? EMPTY_STACK : read;
    }

    @Override
    public void writeToNbt(CompoundTag compound) {
        if (!stack.isEmpty()) {
            ItemStack.CODEC.encodeStart(buildcraft.api.core.NbtApiUtil.registryAwareOps(), stack)
                    .resultOrPartial()
                    .ifPresent(payload -> compound.put("stack", payload));
        }
    }

    @Override
    public ISprite getSprite() {
        return null;
    }

    @Override
    @Nonnull
    public ItemStack getItemStack() {
        return stack;
    }

    /** 7.1.x verbatim, in the immutable-return style the port uses: the stack the player is HOLDING
     *  becomes the parameter, as a count-1 copy; an empty hand clears it. Reading {@code clickedStack}
     *  is the whole point of this widget — an earlier port copied {@code this.stack} instead, so a
     *  plain item-stack parameter (Filter, Filter Tool, Provide/Accept Items, Provide/Accept Fluids,
     *  Goto Station …) could be neither set nor cleared from the gate GUI. */
    @Override
    public StatementParameterItemStack onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        if (clickedStack == null || clickedStack.isEmpty()) {
            return EMPTY;
        }
        ItemStack newStack = clickedStack.copy();
        newStack.setCount(1);
        return new StatementParameterItemStack(newStack);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof StatementParameterItemStack) {
            StatementParameterItemStack param = (StatementParameterItemStack) object;
            return ItemStack.isSameItem(stack, param.stack)
                    && ItemStack.isSameItemSameComponents(stack, param.stack);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stack);
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("Don't call getDescription directly!");
    }

    @Override
    public List<String> getTooltip() {
        if (stack.isEmpty()) {
            return ImmutableList.of();
        }
        // Tooltip access stub — proper implementation requires player context
        return ImmutableList.of(stack.getHoverName().getString());
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:stack";
    }

    @Override
    public IStatementParameter rotateLeft() {
        return this;
    }

    @Override
    public IStatementParameter[] getPossible(IStatementContainer source) {
        return null;
    }
}
