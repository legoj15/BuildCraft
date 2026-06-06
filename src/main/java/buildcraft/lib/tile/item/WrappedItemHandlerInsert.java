package buildcraft.lib.tile.item;

//? if <1.21.10 {
/*import net.minecraft.world.item.ItemStack;*/
//?}

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

public class WrappedItemHandlerInsert extends DelegateItemHandler {
    public WrappedItemHandlerInsert(IBCItemHandler delegate) {
        super(delegate);
    }

    //? if >=1.21.10 {
    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        return 0;
    }
    //?} else {
    /*@Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }*/
    //?}
}
