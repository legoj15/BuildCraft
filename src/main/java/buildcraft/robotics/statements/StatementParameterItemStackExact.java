package buildcraft.robotics.statements;

import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.core.render.ISprite;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.StatementMouseClick;
import buildcraft.lib.misc.StackUtil;

/** An exact-stack statement parameter used by the request-items actions: the stack is decremented by
 *  one slot per click until the machine's available slots run out. */
public class StatementParameterItemStackExact implements IStatementParameter {

    @Nonnull
    protected ItemStack stack = ItemStack.EMPTY;
    protected int availableSlots;

    public StatementParameterItemStackExact() {
        this(ItemStack.EMPTY, 0);
    }

    public StatementParameterItemStackExact(int availableSlots) {
        this(ItemStack.EMPTY, availableSlots);
    }

    public StatementParameterItemStackExact(ItemStack stack, int availableSlots) {
        this.stack = stack;
        this.availableSlots = availableSlots;
    }

    public static StatementParameterItemStackExact readFromNbt(CompoundTag nbt) {
        StatementParameterItemStackExact param = new StatementParameterItemStackExact();
        param.stack = nbt.contains("stack")
                ? ItemStack.CODEC.parse(NbtApiUtil.registryAwareOps(), nbt.get("stack"))
                        .resultOrPartial().orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        //? if >=1.21.10 {
        param.availableSlots = nbt.getIntOr("availableSlots", 0);
        //?} else {
        /*param.availableSlots = nbt.getInt("availableSlots");*/
        //?}
        return param;
    }

    @Override
    @Nonnull
    public ItemStack getItemStack() {
        return stack;
    }

    /** 7.1.x slot-stepping on a copy: left/right ±1 (±16 with shift), clamped to what
     *  {@code availableSlots} can hold; an over-stepped count returns {@link ItemStack#EMPTY}. */
    private ItemStack step(ItemStack base, StatementMouseClick mouseClick) {
        ItemStack stepped = base.copy();
        if (mouseClick.getButton() == 0) {
            stepped.setCount(stepped.getCount() + (mouseClick.isShift() ? 16 : 1));
            int maxSize = availableSlots < 0 ? 64 : Math.min(64, stepped.getMaxStackSize() * availableSlots);
            if (stepped.getCount() > maxSize) {
                stepped.setCount(maxSize);
            }
        } else {
            stepped.setCount(stepped.getCount() - (mouseClick.isShift() ? 16 : 1));
            if (stepped.getCount() <= 0) {
                return ItemStack.EMPTY;
            }
        }
        return stepped;
    }

    @Override
    public StatementParameterItemStackExact onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        if (clickedStack.isEmpty()) {
            if (stack.isEmpty()) {
                return this;
            }
            return new StatementParameterItemStackExact(step(stack, mouseClick), availableSlots);
        }
        if (!stack.isEmpty() && StackUtil.isMatchingItem(stack, clickedStack)) {
            return new StatementParameterItemStackExact(step(stack, mouseClick), availableSlots);
        }
        // A different item clicked: adopt it (7.1.x copies the clicked stack as-is).
        return new StatementParameterItemStackExact(clickedStack.copy(), availableSlots);
    }

    @Override
    public void writeToNbt(CompoundTag nbt) {
        if (!stack.isEmpty()) {
            ItemStack.CODEC.encodeStart(NbtApiUtil.registryAwareOps(), stack)
                    .resultOrPartial()
                    .ifPresent(payload -> nbt.put("stack", payload));
        }
        nbt.putInt("availableSlots", availableSlots);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:stackExact";
    }

    @Override
    public IStatementParameter rotateLeft() {
        return this;
    }

    @Override
    public IStatementParameter[] getPossible(IStatementContainer source) {
        return null;
    }

    @Override
    public ISprite getSprite() {
        return null;
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
        return ImmutableList.of(stack.getHoverName().getString());
    }
}
