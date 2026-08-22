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

    @Override
    public StatementParameterItemStackExact onClick(
            IStatementContainer source, IStatement stmt, ItemStack clickedStack, StatementMouseClick mouseClick) {
        // Red-baseline degenerate: no count mutation yet. Ph6-green implements the upstream ±1/±16
        // slot-stepping against availableSlots, functionally.
        return this;
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
