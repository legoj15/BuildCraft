/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.robots;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.BCLog;
import buildcraft.api.mj.MjAPI;

import buildcraft.api.core.NbtApiUtil;

public class AIRobot {
    public IRobotAccess robot;

    private AIRobot delegateAI;
    private AIRobot parentAI;

    private boolean success;

    public AIRobot(IRobotAccess iRobot) {
        robot = iRobot;
        success = true;
    }

    public void start() {

    }

    public void preempt(AIRobot ai) {

    }

    public void update() {
        // Update should always handle terminate. Some AI are not using update
        // at all, their code being in start() and end(). In these case,
        // calling update is a malfunction, the ai should be terminated.
        terminate();
    }

    public void end() {

    }

    /** This gets called when a delegate AI ends work naturally.
     * 
     * @param ai The delegate AI which ended work. */
    public void delegateAIEnded(AIRobot ai) {

    }

    /** This gets called when a delegate AI is forcibly aborted.
     * 
     * @param ai The delegate AI which was aborted. */
    public void delegateAIAborted(AIRobot ai) {

    }

    public void writeSelfToNBT(CompoundTag nbt) {

    }

    public void loadSelfFromNBT(CompoundTag nbt) {

    }

    /** Runs at the very end of {@link #loadFromNBT}, once {@link #loadSelfFromNBT} has run AND the saved delegate
     *  (if any) is attached. The place for state that has to be resolved against the restored delegate, which
     *  {@code loadSelfFromNBT} cannot see. */
    protected void onLoadedFromNbt() {

    }

    public boolean success() {
        return success;
    }

    protected void setSuccess(boolean iSuccess) {
        success = iSuccess;
    }

    public long getPowerCost() {
        return MjAPI.MJ / 10;
    }

    public boolean canLoadFromNBT() {
        return false;
    }

    /** Tries to receive items in parameters, return items that are left after the operation. */
    public ItemStack receiveItem(ItemStack stack) {
        return stack;
    }

    public final void terminate() {
        abortDelegateAI();
        end();

        if (parentAI != null) {
            parentAI.delegateAI = null;
            parentAI.delegateAIEnded(this);
        }
    }

    public final void abort() {
        abortDelegateAI();

        try {
            end();

            if (parentAI != null) {
                parentAI.delegateAI = null;
                parentAI.delegateAIAborted(this);
            }
        } catch (Throwable e) {
            BCLog.logger.warn("[robots] Robot delegate AI threw; aborting it", e);
            delegateAI = null;

            if (parentAI != null) {
                parentAI.delegateAI = null;
            }
        }
    }

    public final void cycle() {
        try {
            preempt(delegateAI);

            if (delegateAI != null) {
                delegateAI.cycle();
            } else {
                robot.getBattery().extractPower(1, getPowerCost());
                update();
            }
        } catch (Throwable e) {
            BCLog.logger.warn("[robots] Robot AI update threw; aborting", e);
            abort();
        }
    }

    public final void startDelegateAI(AIRobot ai) {
        abortDelegateAI();
        delegateAI = ai;
        ai.parentAI = this;
        delegateAI.start();
    }

    public final void abortDelegateAI() {
        if (delegateAI != null) {
            delegateAI.abort();
        }
    }

    public final AIRobot getActiveAI() {
        if (delegateAI != null) {
            return delegateAI.getActiveAI();
        } else {
            return this;
        }
    }

    public final AIRobot getDelegateAI() {
        return delegateAI;
    }

    public final void writeToNbt(CompoundTag nbt) {
        String aiName = RobotManager.getAIRobotName(getClass());
        if (aiName == null) {
            // An unregistered AI class (a removed addon's board, say) must not crash the whole save: with no
            // aiName the tag is unreadable and loadFromNBT yields nothing, which every consumer already
            // null-guards. 7.1.x wrote null here and NPE'd on the very first save of such a robot.
            return;
        }
        nbt.putString("aiName", aiName);

        CompoundTag data = new CompoundTag();
        writeSelfToNBT(data);
        nbt.put("data", data);

        if (delegateAI != null && delegateAI.canLoadFromNBT()) {
            CompoundTag sub = new CompoundTag();

            delegateAI.writeToNbt(sub);
            nbt.put("delegateAI", sub);
        }
    }

    public final void loadFromNBT(CompoundTag nbt) {
        loadSelfFromNBT(NbtApiUtil.getCompound(nbt, "data"));

        if (nbt.contains("delegateAI")) {
            CompoundTag sub = NbtApiUtil.getCompound(nbt, "delegateAI");

            try {
                Class<?> aiRobotClass;
                if (sub.contains("class")) {
                    // Migration support for 6.4.x
                    aiRobotClass = RobotManager.getAIRobotByLegacyClassName(NbtApiUtil.getString(sub, "class", ""));
                } else {
                    aiRobotClass = RobotManager.getAIRobotByName(NbtApiUtil.getString(sub, "aiName", ""));
                }
                if (aiRobotClass != null) {
                    // Loaded into a local and installed only once it has loaded: a delegate whose load throws would
                    // otherwise stay attached half-built (the throw is swallowed below) and run from its defaults.
                    AIRobot loaded = (AIRobot) aiRobotClass.getConstructor(IRobotAccess.class).newInstance(robot);
                    loaded.parentAI = this;

                    if (loaded.canLoadFromNBT()) {
                        loaded.loadFromNBT(sub);
                    }
                    delegateAI = loaded;
                }
            } catch (Throwable e) {
                // No delegate: this AI goes on without it, as it does for a delegate whose class is gone.
                delegateAI = null;
                BCLog.logger.warn("[robots] Failed to load robot delegate AI from NBT", e);
            }
        }

        onLoadedFromNbt();
    }

    /** Rebuilds the AI saved in {@code nbt}, or returns null when there is none to build: an unregistered name, or a
     *  load that threw. The AI is returned only after it has loaded, so a caller never installs a half-loaded one. */
    public static AIRobot loadAI(CompoundTag nbt, IRobotAccess robot) {
        AIRobot ai = null;

        try {
            Class<?> aiRobotClass;
            if (nbt.contains("class")) {
                // Migration support for 6.4.x
                aiRobotClass = RobotManager.getAIRobotByLegacyClassName(NbtApiUtil.getString(nbt, "class", ""));
            } else {
                aiRobotClass = RobotManager.getAIRobotByName(NbtApiUtil.getString(nbt, "aiName", ""));
            }
            if (aiRobotClass != null) {
                // Loaded into a local and returned only once it has loaded: a throwing load is swallowed here, and the
                // half-built AI was returned anyway - and installed by the caller as a board running from its defaults.
                AIRobot loaded = (AIRobot) aiRobotClass.getConstructor(IRobotAccess.class).newInstance(robot);
                loaded.loadFromNBT(nbt);
                ai = loaded;
            }
        } catch (Throwable e) {
            BCLog.logger.warn("[robots] Failed to instantiate robot AI from NBT", e);
        }

        return ai;
    }
}

