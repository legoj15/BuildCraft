package buildcraft.api.transport.pipe;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Called in
 * {@link Block#onBlockPlacedBy(net.minecraft.world.level.Level, net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, LivingEntity, ItemStack)} */
public class PipeEventPlaced extends PipeEvent {

    public final LivingEntity placer;
    public final ItemStack placeStack;

    public PipeEventPlaced(IPipeHolder holder, LivingEntity placer, ItemStack placeStack) {
        super(holder);
        this.placer = placer;
        this.placeStack = placeStack;
    }
}


