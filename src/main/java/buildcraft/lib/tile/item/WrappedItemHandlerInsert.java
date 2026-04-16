package buildcraft.lib.tile.item;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class WrappedItemHandlerInsert extends DelegateItemHandler {
    public WrappedItemHandlerInsert(ResourceHandler<ItemResource> delegate) {
        super(delegate);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        return 0;
    }
}
