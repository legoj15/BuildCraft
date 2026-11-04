/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.pipe.behaviour;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.lib.test.EntityArenaUtil;
import buildcraft.lib.test.LoaderTestEvents;
import buildcraft.lib.test.LoaderTestEvents.InteractAnswer;
import buildcraft.lib.test.WorkClaimMod;
import buildcraft.platform.event.HookOrder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStripesDropTester.StripesRig;

/**
 * The stripes pipe's entity-interaction refusal, against the loader's entity-interaction event (NeoForge's cancellable
 * {@code EntityInteract}, Fabric's {@code UseEntityCallback}, which BuildCraft asks for a fake player's click), its
 * refusal results and its listener order ({@link LoaderTestEvents#claimEntityInteractions}). Split from
 * {@link PipeBehaviourStripesDropTester}, which shares the rig.
 */
public class PipeBehaviourStripesEntityClaimTester {

    private static final int ENTITY_POLL_TICKS = 60;

    /**
     * A claim that refuses the interaction with a mob in front of the pipe ends the whole dispatch: the entity
     * handler's refusal is a cancelled entity-interaction event, which comes back from {@code Player#interactOn} on
     * NeoForge as the default PASS, indistinguishable from a mob that simply ignored the item. The dispatch used to go on to the
     * dispenser fallback and right-click the mob's block with the same item. The control shows the fallback is
     * reached when nothing refuses, so the absence of the click is the refusal and not an unreachable item.
     */
    public static void testRefusedEntityInteractionEndsTheDispatch(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos targetRel = pipeRel.east();
        BlockPos target = helper.absolutePos(targetRel);
        EntityArenaUtil.forceLoadEntityArena(helper, targetRel);
        StripesRig rig = StripesRig.build(helper, pipeRel, Direction.EAST);
        Mob[] cow = new Mob[1];
        EntityArenaUtil.tickUntil(helper, ENTITY_POLL_TICKS,
                () -> EntityArenaUtil.isEntityTicking(helper, targetRel), () -> {
            //? if >=26.2 {
            cow[0] = helper.spawn(net.minecraft.world.entity.EntityTypes.COW, targetRel);
            //?} else {
            /*cow[0] = helper.spawn(net.minecraft.world.entity.EntityType.COW, targetRel);*/
            //?}
            cow[0].setNoAi(true);
            cow[0].setNoGravity(true);
            Set<BlockPos> watched = Set.of(target);
            int[] interactions = new int[1];
            // A claim refusing the interaction: on NeoForge the cancellation result is left at its default, PASS.
            try (LoaderTestEvents.Observer protectCow = LoaderTestEvents.claimEntityInteractions(HookOrder.DEFAULT,
                    (player, entity) -> {
                        if (entity == cow[0]) {
                            interactions[0]++;
                            return InteractAnswer.REFUSE;
                        }
                        return InteractAnswer.ALLOW;
                    });
                    WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes().onDrop(refused);

                helper.assertTrue(interactions[0] >= 1, "the pipe tried to interact with the cow");
                helper.assertTrue(claim.toolAsks(target) == 0,
                        "a refused interaction ends the use: the cow's block is never right-clicked, got "
                                + claim.toolAsks(target));
                helper.assertTrue(refused.getStack().is(Items.STICK) && refused.getStack().getCount() == 3,
                        "...and the refused item is not consumed, got " + refused.getStack());
            }

            // The same refusal from a claim that leaves a consuming result (NeoForge's cancelled event answering
            // CONSUME, a Fabric callback answering CONSUME): Player#interactOn then answers "used", yet nothing was done
            // to the cow, so the item must come back unused all the same.
            int[] swallowed = new int[1];
            try (LoaderTestEvents.Observer swallowCow = LoaderTestEvents.claimEntityInteractions(HookOrder.DEFAULT,
                    (player, entity) -> {
                        if (entity == cow[0]) {
                            swallowed[0]++;
                            return InteractAnswer.REFUSE_CONSUMING;
                        }
                        return InteractAnswer.ALLOW;
                    });
                    WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes().onDrop(refused);

                helper.assertTrue(swallowed[0] >= 1, "the pipe tried to interact with the cow again");
                helper.assertTrue(claim.toolAsks(target) == 0,
                        "a refused interaction ends the use whatever result the claim left: the cow's block is never"
                                + " right-clicked, got " + claim.toolAsks(target));
                helper.assertTrue(refused.getStack().is(Items.STICK) && refused.getStack().getCount() == 3,
                        "...and the item is not taken as used, got " + refused.getStack());
            }

            // The same refusal from a claim that cancels at LOWEST and registered its listener while the mods
            // loaded, after BuildCraft was constructed: the refusal recorder registers last, so it still sees it.
            LoadTimeLowestClaim.target = cow[0];
            LoadTimeLowestClaim.cancels = 0;
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop refused = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes().onDrop(refused);

                helper.assertTrue(LoadTimeLowestClaim.cancels >= 1,
                        "the load-time LOWEST claim was asked about the cow, got " + LoadTimeLowestClaim.cancels);
                helper.assertTrue(claim.toolAsks(target) == 0,
                        "a refusal from a LOWEST listener registered after BuildCraft ends the use too: the cow's"
                                + " block is never right-clicked, got " + claim.toolAsks(target));
                helper.assertTrue(refused.getStack().is(Items.STICK) && refused.getStack().getCount() == 3,
                        "...and the item is not consumed, got " + refused.getStack());
            } finally {
                LoadTimeLowestClaim.target = null;
            }

            // Control: nothing refuses, so the item reaches the dispenser fallback, which clicks the block.
            try (WorkClaimMod claim = new WorkClaimMod(watched::contains, pos -> false)) {
                PipeEventItem.Drop allowed = rig.drop(new ItemStack(Items.STICK, 3));
                rig.stripes().onDrop(allowed);

                helper.assertTrue(claim.toolAsks(target) >= 1,
                        "control: without a refusal the dispenser fallback right-clicks the block");
            }
            helper.succeed();
        }, "the cow's chunk never became entity-ticking", () -> {
            EntityArenaUtil.discard(cow[0]);
            rig.close();
        });
    }

    /**
     * A claim mod that refuses mob interactions LATE (NeoForge's LOWEST) and registers its listener after BuildCraft's
     * own: on NeoForge during mod loading (common setup), after BuildCraft's construction-time listeners, the case a
     * recorder registered with BuildCraft misses, since one priority's listeners run in registration order; on Fabric
     * the first time a test arms it, long after BuildCraft's init, in the same phase as BuildCraft's LATE recorder.
     * Inert unless a test arms {@link #target}.
     */
    //? if neoforge {
    @net.neoforged.fml.common.EventBusSubscriber(modid = "buildcraftunofficial")
    //?}
    public static final class LoadTimeLowestClaim {
        static volatile Entity target;
        static int cancels;

        //? if neoforge {
        @net.neoforged.bus.api.SubscribeEvent
        public static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
            event.enqueueWork(LoadTimeLowestClaim::register);
        }
        //?} else {
        /*static {
            register();
        }*/
        //?}

        private static void register() {
            LoaderTestEvents.claimEntityInteractions(HookOrder.LATE, (player, entity) -> {
                if (target != null && entity == target) {
                    cancels++;
                    return InteractAnswer.REFUSE;
                }
                return InteractAnswer.ALLOW;
            });
        }
    }
}
