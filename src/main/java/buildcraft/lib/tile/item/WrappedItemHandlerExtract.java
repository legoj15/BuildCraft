package buildcraft.lib.tile.item;

//? if <1.21.10 {
/*import net.minecraft.world.item.ItemStack;*/
//?}

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

public class WrappedItemHandlerExtract extends DelegateItemHandler {
    public WrappedItemHandlerExtract(IBCItemHandler delegate) {
        super(delegate);
    }

    //? if >=1.21.10 {
    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        return 0;
    }
    //?} else {
    /*@Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }*/
    //?}
}
