package buildcraft.lib.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.world.InteractionResult;

import buildcraft.lib.misc.BlockUtil;

/**
 * Guards the cross-node mapping of {@link BlockUtil}'s {@code useItemOn} result helpers.
 * <p>
 * The bug this catches: on 1.21.1, {@code itemUsePass()} and {@code itemUseTryWithEmptyHand()} were
 * BOTH mapped to {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}. That collapsed two opposite signals into
 * one, so a non-crouch wrench on a GUI-bearing block (heat exchanger, an engine/dynamo with an
 * alternate receiver, distiller) routed to {@code useWithoutItem} (open GUI) instead of falling
 * through to the wrench's {@code useOn} (rotate/flip). On 26.1 plain {@code PASS} already skips
 * {@code useWithoutItem} (only {@code TRY_WITH_EMPTY_HAND} triggers it), so the correct 1.21.1
 * equivalent of {@code itemUsePass()} is {@code SKIP_DEFAULT_BLOCK_INTERACTION}.
 */
public class BlockUtilInteractionResultTester {

    /**
     * Node-agnostic core guard: the "skip the block's empty-hand interaction" signal
     * ({@code itemUsePass()}) and the "run the block's empty-hand interaction" signal
     * ({@code itemUseTryWithEmptyHand()}) must NOT be the same value. The original 1.21.1 bug made
     * them identical, which is exactly what this rejects.
     */
    @Test
    public void passAndTryEmptyHandAreDistinct() {
        Assertions.assertNotEquals(BlockUtil.itemUsePass(), BlockUtil.itemUseTryWithEmptyHand(),
                "itemUsePass() (skip empty-hand → Item#useOn) must differ from itemUseTryWithEmptyHand() (run useWithoutItem)");
    }

    /**
     * Node-agnostic: a shared helper that returns {@code InteractionResult.PASS} (e.g. a GUI-open
     * helper that couldn't open) must adapt to the same signal as a direct {@code itemUsePass()},
     * so the held item's {@code useOn} still runs.
     */
    @Test
    public void itemUseFromPassMatchesPass() {
        Assertions.assertEquals(BlockUtil.itemUsePass(), BlockUtil.itemUseFrom(InteractionResult.PASS),
                "itemUseFrom(PASS) must map to the same signal as itemUsePass()");
    }

    //? if <1.21.10 {
    /*@Test
    public void exactMapping_1_21_1() {
        Assertions.assertEquals(net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                BlockUtil.itemUsePass(),
                "itemUsePass() must SKIP the default block interaction on 1.21.1 (so a wrench reaches useOn)");
        Assertions.assertEquals(net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                BlockUtil.itemUseTryWithEmptyHand(),
                "itemUseTryWithEmptyHand() must PASS to the default block interaction (run useWithoutItem) on 1.21.1");
        Assertions.assertFalse(BlockUtil.itemUsePass().consumesAction(), "itemUsePass() must not consume the action");
        Assertions.assertEquals(net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                BlockUtil.itemUseFrom(InteractionResult.PASS),
                "itemUseFrom(PASS) must skip the default block interaction on 1.21.1");
    }*/
    //?}

    //? if >=1.21.10 {
    @Test
    public void exactMapping_modern() {
        Assertions.assertEquals(InteractionResult.PASS, BlockUtil.itemUsePass(),
                "itemUsePass() must be PASS on 1.21.10+");
        Assertions.assertEquals(InteractionResult.TRY_WITH_EMPTY_HAND, BlockUtil.itemUseTryWithEmptyHand(),
                "itemUseTryWithEmptyHand() must be TRY_WITH_EMPTY_HAND on 1.21.10+");
    }
    //?}
}
