package buildcraft.lib.tile.item;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class WrappedItemHandlerExtract extends DelegateItemHandler {
    public WrappedItemHandlerExtract(ResourceHandler<ItemResource> delegate) {
        super(delegate);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        return 0;
    }
}
