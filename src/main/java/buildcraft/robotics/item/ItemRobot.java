/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The robot item. Ported from 7.1.x {@code buildcraft.robotics.ItemRobot}.
 *
 * <p>Board and charge ride in {@code DataComponents.CUSTOM_DATA} as {@code {board:{id:String}, energy:long}};
 * a bare stack with no blob at all is treated as an empty-board robot at zero charge, so a
 * {@code /give}n or creative-tab stack always behaves. Every read of that blob must go through
 * {@code NbtApiUtil} — the plain {@code CompoundTag} getters return {@code Optional}s from 1.21.10 onward and
 * raw values before that.
 *
 * <p>Unlike 7.1.x this item does <em>not</em> refuse to place an empty-board robot: Ph3 ships only the empty
 * board, so that guard would make the item unplaceable and there would be nothing to test. An empty-board robot
 * places, docks and idles — that is the Ph3 minimum. Ph4 revisits it once real boards exist. The item also
 * ships recipe-less on purpose: boards arrive in Ph4, and a craftable do-nothing robot would just be an
 * expensive mistake.
 *
 * <p><b>Ph3 skeleton — signatures only.</b>
 */
public class ItemRobot extends Item {

    /** The CUSTOM_DATA sub-compound holding the board, keyed exactly as 7.1.x did. */
    public static final String TAG_BOARD = "board";

    /** The CUSTOM_DATA key holding stored energy, in micro-MJ. 7.1.x stored an RF int here. */
    public static final String TAG_ENERGY = "energy";

    public ItemRobot(Item.Properties properties) {
        super(properties);
    }

    /** Builds a robot stack carrying the given board id and charge.
     *
     * @param boardId The registered board's id (e.g. {@code buildcraftunofficial:empty_robot_board}).
     * @param energy Stored energy in micro-MJ, clamped to {@code EntityRobotBase.MAX_POWER} on use.
     * @return A single robot stack. Ph3 stub — returns {@link ItemStack#EMPTY}. */
    public static ItemStack createRobotStack(String boardId, long energy) {
        return ItemStack.EMPTY;
    }

    /** @return Stored energy in micro-MJ, 0 for a stack with no blob. Ph3 stub. */
    public static long getEnergy(ItemStack stack) {
        return 0;
    }

    /** @return The board id, or null for a stack with no blob (caller treats null as the empty board).
     *          Ph3 stub. */
    public static String getBoardId(ItemStack stack) {
        return null;
    }

    /** Ph3 stub. The real one requires the clicked face to carry an untaken {@code RobotStationPluggable},
     *  fires the cancellable {@code RobotEvent.Place}, assigns an id from the registry, positions the robot at
     *  the station's face centre, takes the station as main, docks, and adds the entity with
     *  {@code level.addFreshEntity} (deliberately not {@code EntityType.spawn}, which forks three ways across
     *  the nodes). */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }
}
