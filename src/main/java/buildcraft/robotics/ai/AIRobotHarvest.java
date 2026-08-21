/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** Harvests a mature crop at {@code blockFound} through the
 *  {@link buildcraft.api.crops.CropManager} (maturity check, crop drops) and drops the result at the robot's
 *  feet. Ported from 7.1.x {@code AIRobotHarvest} (7.1.x dropped items via {@code BlockUtils.dropItem}; the
 *  modern equivalent is an {@code ItemEntity} spawn).
 *
 *  <p>One faithful quirk kept from 7.1.x: a successful harvest does NOT terminate the AI — the next
 *  cycle's re-check finds the (now air) position no longer "harvestable" and ends the AI with
 *  {@code success == false}. The harvester board therefore releases its block reservation
 *  unconditionally rather than on success. */
public class AIRobotHarvest extends AIRobot {

    private BlockPos blockFound;
    private int delay = 0;

    public AIRobotHarvest(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotHarvest(IRobotAccess iRobot, BlockPos iBlockFound) {
        super(iRobot);

        blockFound = iBlockFound;
    }

    @Override
    public void update() {
        if (blockFound == null) {
            setSuccess(false);
            terminate();
            return;
        }

        Level level = robot.level();
        if (level == null) {
            terminate();
            return;
        }

        if (delay++ > 20) {
            // The Level path (not the pure-state seam) so a crop whose neighbours changed (a block placed
            // on it, say) is re-validated before the harvest.
            if (!BuildCraftAPI.getWorldProperty("harvestable").get(level, blockFound)) {
                setSuccess(false);
                terminate();
                return;
            }

            NonNullList<ItemStack> drops = NonNullList.create();
            if (!CropManager.harvestCrop(level, blockFound, drops)) {
                setSuccess(false);
                terminate();
                return;
            }
            // 7.1.x dropped the loot at the robot's feet (a picker board collects it); keep that anchor.
            for (ItemStack stack : drops) {
                level.addFreshEntity(new ItemEntity(level,
                        robot.position().x, robot.position().y, robot.position().z, stack));
            }
            // No terminate on the success path — see the class javadoc for the 7.1.x quirk that follows.
        }
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        super.writeSelfToNBT(nbt);

        if (blockFound != null) {
            int[] arr = {blockFound.getX(), blockFound.getY(), blockFound.getZ()};
            nbt.putIntArray("blockFound", arr);
        }
    }

    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        super.loadSelfFromNBT(nbt);

        int[] arr = NbtApiUtil.getIntArray(nbt, "blockFound", null);
        if (arr != null && arr.length == 3) {
            blockFound = new BlockPos(arr[0], arr[1], arr[2]);
        }
    }
}
