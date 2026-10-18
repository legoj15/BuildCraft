/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;

/** The robot's top-level controller, held in {@code EntityRobot.mainAI}. Every cycle it first checks the
 *  energy ladder — shutdown at flat, recharge below the safety reserve — and otherwise hands control to the
 *  board, or to a caller's overriding AI. Ported from 7.1.x; the energy references map to the modern API
 *  ({@code getPower()}, {@code IRobotAccess.SHUTDOWN_POWER}/{@code SAFETY_POWER}). */
public class AIRobotMain extends AIRobot {

    /** Cycles {@link #setOverridingAI} refuses new overrides for after one ended unsuccessfully (5 s). */
    static final int FAILED_OVERRIDE_COOLDOWN = 100;

    private AIRobot overridingAI;
    private int rechargeCooldown;
    /** Counts down from {@link #FAILED_OVERRIDE_COOLDOWN} after an override failed; no new override is accepted
     *  until it reaches zero. A gate that holds re-issues its redirect every tick, and each one aborts the board:
     *  a redirect that keeps failing (an unreachable or refused station) would otherwise keep the board from ever
     *  getting a cycle. Not 7.1.x, which re-issued it on the next tick. Not saved: a reload simply lets the gate
     *  try again at once. */
    private int overrideCooldown;
    /** Set by {@link #loadSelfFromNBT}: the saved delegate was the override. The delegate is only attached after
     *  that call, so {@link #onLoadedFromNbt} does the actual restoring. */
    private boolean overrideSaved;

    public AIRobotMain(IRobotAccess iRobot) {
        super(iRobot);
        rechargeCooldown = 0;
    }

    @Override
    public long getPowerCost() {
        return 0;
    }

    @Override
    public void preempt(AIRobot ai) {
        if (overrideCooldown > 0) {
            overrideCooldown--;
        }
        if (robot.getPower() <= IRobotAccess.SHUTDOWN_POWER
                && (robot.getDockingStation() == null || !robot.getDockingStation().providesPower())) {
            if (!(ai instanceof AIRobotShutdown)) {
                BCLog.logger.info("Shutting down robot " + robot + " - no power");
                startDelegateAI(new AIRobotShutdown(robot));
            }
        } else if (robot.getPower() < IRobotAccess.SAFETY_POWER) {
            if (!(ai instanceof AIRobotRecharge) && !(ai instanceof AIRobotShutdown)) {
                if (rechargeCooldown-- <= 0) {
                    startDelegateAI(new AIRobotRecharge(robot));
                }
            }
        } else if (!(ai instanceof AIRobotRecharge)) {
            if (overridingAI != null && ai != overridingAI) {
                startDelegateAI(overridingAI);
            }
        }
    }

    @Override
    public void update() {
        AIRobot board = robot.getBoard();

        if (board != null) {
            startDelegateAI(board);
        }
    }

    @Override
    public void delegateAIEnded(AIRobot ai) {
        if (ai instanceof AIRobotRecharge) {
            if (!ai.success()) {
                rechargeCooldown = 120;
            }
        }
        if (ai == overridingAI) {
            overridingAI = null;
            if (!ai.success()) {
                overrideCooldown = FAILED_OVERRIDE_COOLDOWN;
            }
        }
    }

    /** Installs {@code ai} as the override, started on the next cycle. Ignored while another override is tracked,
     *  and for {@link #FAILED_OVERRIDE_COOLDOWN} cycles after an override failed. */
    public void setOverridingAI(AIRobot ai) {
        if (overridingAI == null && overrideCooldown <= 0) {
            overridingAI = ai;
        }
    }

    public AIRobot getOverridingAI() {
        return overridingAI;
    }

    @Override
    public boolean canLoadFromNBT() {
        return true;
    }

    /** Saves the override (a gate redirect such as Goto Station) whenever there is one that can be saved:
     *  <ul>
     *  <li>running as the delegate, it is saved with the delegate, and a flag remembers that it is the override. A
     *  reloaded robot then keeps tracking the redirect it resumes: untracked, the next gate pulse found no override
     *  and stacked a second one over it.</li>
     *  <li>displaced (a recharge or shutdown took the delegate slot and aborted it; {@link #preempt} starts it again
     *  once power is back), it is saved on its own under {@code override}. Otherwise a reload before the recharge
     *  ended dropped the redirect for good, as 7.1.x did.</li>
     *  </ul> */
    @Override
    public void writeSelfToNBT(CompoundTag nbt) {
        if (overridingAI == null || !overridingAI.canLoadFromNBT()) {
            return;
        }
        if (overridingAI == getDelegateAI()) {
            nbt.putBoolean("overriding", true);
        } else {
            CompoundTag override = new CompoundTag();
            overridingAI.writeToNbt(override);
            // Empty when the override's class is not registered: there is nothing a load could rebuild then.
            if (!override.isEmpty()) {
                nbt.put("override", override);
            }
        }
    }

    /** A displaced override is rebuilt here but not started: {@link #preempt} starts it once nothing outranks it. */
    @Override
    public void loadSelfFromNBT(CompoundTag nbt) {
        overrideSaved = NbtApiUtil.getBoolean(nbt, "overriding", false);
        if (nbt.contains("override")) {
            overridingAI = AIRobot.loadAI(NbtApiUtil.getCompound(nbt, "override"), robot);
        }
    }

    @Override
    protected void onLoadedFromNbt() {
        if (overrideSaved) {
            overrideSaved = false;
            // Null when the saved override's class is no longer registered: nothing to track then.
            overridingAI = getDelegateAI();
        }
    }
}
