/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.boards;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//?} else {
/*import net.minecraft.world.entity.animal.horse.AbstractHorse;*/
//?}
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.robotics.ai.AIRobotAttack;
import buildcraft.robotics.ai.AIRobotFetchAndEquipItemStack;
import buildcraft.robotics.ai.AIRobotGotoSleep;
import buildcraft.robotics.ai.AIRobotGotoStationAndUnload;
import buildcraft.robotics.ai.AIRobotSearchEntity;

/** The butcher: keeps a sword equipped (fetching one from the station when none is held, unloading a
 *  broken one), then searches for animals and slaughters them with {@code AIRobotAttack} (the sword
 *  drops are collected by the usual item-fetch paths). Ported from 7.1.x {@code BoardRobotButcher}
 *  (7.1.x's tool check was {@code ItemSword}; the modern equivalent is
 *  {@link RobotToolPredicates#isSword}).
 *
 *  <p>Three documented divergences from 7.1.x. Two match the break-board base: the fetch filter refuses a
 *  WORN sword (7.1.x would fetch a broken sword and spin forever), and the worn-hand check is guarded
 *  by {@code isDamageableItem()} (7.1.x compared {@code damage >= maxDamage} unguarded, so an
 *  undamageable item in the hand — 0 &gt;= 0 — would unload every cycle). The third is the target set: 7.1.x took
 *  any {@code EntityAnimal}, which killed a player's pets and horses and anything they had name-tagged to keep;
 *  {@link #isExpectedTarget} spares those (docs/robotics-resurrection.md, Post-completion amendments). */
public class BoardRobotButcher extends RedstoneBoardRobot {

    /** Counts swings protection mods refused in a row (the attack notes a refused target unreachable, so the search
     *  passes it over, unless the refusal was only the home station's unknown owner), so a robot facing a claim full
     *  of protected mobs goes to sleep. */
    private final WorkRefusals refusals;

    public BoardRobotButcher(IRobotAccess iRobot) {
        super(iRobot);
        refusals = new WorkRefusals(iRobot);
    }

    @Override
    public RedstoneBoardRobotNBT getNBTHandler() {
        return BoardRobotButcherNBT.INSTANCE;
    }

    /** The sword predicate: vanilla swords by identity plus anything the {@code c:tools/sword} tag admits. */
    public boolean isExpectedSword(ItemStack stack) {
        return RobotToolPredicates.isSword(stack);
    }

    /** The target predicate: an animal nobody has claimed. Spared are an animal with an owner (a wolf, cat or parrot
     *  someone tamed, a tamed horse, donkey, mule, llama or camel), a tamed animal whether or not its owner is
     *  recorded, and one with a custom name (a name tag is how a player marks an animal to keep). Not 7.1.x, whose
     *  butcher took every {@code EntityAnimal}.
     *
     *  <p>"Owned" is the owner reference being SET, never the owner being FOUND: the owner is usually offline, and
     *  {@code getOwner()} resolves only to an online player, so asking it would make an absent player's pet look
     *  ownerless. */
    public boolean isExpectedTarget(Entity entity) {
        if (!(entity instanceof Animal) || entity.hasCustomName()) {
            return false;
        }
        if (entity instanceof OwnableEntity ownable && hasOwner(ownable)) {
            return false;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        return !(entity instanceof AbstractHorse horse && horse.isTamed());
    }

    private static boolean hasOwner(OwnableEntity ownable) {
        //? if >=1.21.10 {
        return ownable.getOwnerReference() != null;
        //?} else {
        /*return ownable.getOwnerUUID() != null;*/
        //?}
    }

    @Override
    public final void update() {
        ItemStack held = robot.getHeldItem();
        if (held.isEmpty() && !isExpectedSword(ItemStack.EMPTY)) {
            startDelegateAI(new AIRobotFetchAndEquipItemStack(robot,
                    stack -> !stack.isEmpty() && stack.getDamageValue() < stack.getMaxDamage()
                            && isExpectedSword(stack)));
        } else if (!held.isEmpty() && held.isDamageableItem()
                && held.getDamageValue() >= held.getMaxDamage()) {
            startDelegateAI(AIRobotGotoStationAndUnload.forHeldItem(robot));
        } else {
            startDelegateAI(new AIRobotSearchEntity(robot, this::isExpectedTarget, 250f, robot.getZoneToWork()));
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        // A failed fetch AND a failed unload of the broken sword both send the robot to sleep: without the second,
        // the board started the same unload again on the next tick, forever (the break-board base handles both).
        if (ai instanceof AIRobotFetchAndEquipItemStack || ai instanceof AIRobotGotoStationAndUnload) {
            if (!ai.success()) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotSearchEntity search) {
            if (search.success()) {
                startDelegateAI(new AIRobotAttack(robot, search.target));
            } else {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        } else if (ai instanceof AIRobotAttack attack) {
            if (!attack.refused()) {
                refusals.succeeded();
            } else if (refusals.failed(null, null)) {
                startDelegateAI(new AIRobotGotoSleep(robot));
            }
        }
    }
}
