/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.util.function.IntSupplier;

import net.minecraft.util.Mth;

/**
 * The robot's energy exhaust rules — 7.1.x's {@code EntityRobot.updateEnergyFX}/{@code spawnEnergyFX}, kept as
 * pure math so they are testable without a client.
 *
 * <ul>
 * <li><b>Rate.</b> One puff per {@code 100 << (2 * particleSetting)} accumulated spend-per-cycle: 100 on All,
 * 400 on Decreased, 1600 on Minimal. The spawn goes through the particle engine's limiter-free path (as
 * 7.1.x's direct {@code effectRenderer.addEffect} did), so this is the ONLY thinning applied — vanilla's
 * own limiter would drop every puff on Minimal and a third of them on Decreased on top of it.</li>
 * <li><b>Size.</b> {@code max(1, spend * 0.075)} — a costlier AI blows a bigger, longer-lived puff. Capped at
 * {@link #MAX_PARTICLE_SIZE}, which no built-in AI reaches (the costliest, Attack, is 1.2).</li>
 * </ul>
 *
 * <p>The particle setting is a client option, and this class is loaded on dedicated servers, so the client
 * wires {@link #setParticleSettingSource} at init; until it does (and always on a server, where the exhaust
 * never runs) the setting reads as All.
 */
public final class RobotExhaust {
    /** 7.1.x's base puff interval at the All setting. */
    public static final float BASE_THRESHOLD = 100F;
    /** 7.1.x's spend-to-size factor. */
    public static final float SIZE_PER_SPEND = 0.075F;
    /** Guard rail for addon AIs with enormous per-cycle costs — size also multiplies the puff's lifetime. */
    public static final float MAX_PARTICLE_SIZE = 4F;

    private static volatile IntSupplier particleSetting = () -> 0;

    private RobotExhaust() {}

    /** @param particleSettingId the client's particle setting id: 0 All, 1 Decreased, 2 Minimal. */
    public static float particleThreshold(int particleSettingId) {
        return BASE_THRESHOLD * (1 << (2 * Mth.clamp(particleSettingId, 0, 2)));
    }

    public static float particleSize(int energySpend) {
        return Mth.clamp(energySpend * SIZE_PER_SPEND, 1F, MAX_PARTICLE_SIZE);
    }

    /** Maps a {@code ParticleStatus} constant name to 7.1.x's setting id. By name, not ordinal: the enum has
     *  already moved package and lost {@code getId()} across the supported lines, and a constant inserted in
     *  future must not silently shift Minimal onto the All rate. Unknown names read as All. */
    public static int particleSettingId(String particleStatusName) {
        return switch (particleStatusName) {
            case "DECREASED" -> 1;
            case "MINIMAL" -> 2;
            default -> 0;
        };
    }

    /** The current client particle setting id (0 All, 1 Decreased, 2 Minimal). */
    public static int particleSetting() {
        return particleSetting.getAsInt();
    }

    /** Client init only: the source of the live particle setting. */
    public static void setParticleSettingSource(IntSupplier source) {
        particleSetting = source;
    }

    /** 7.1.x's {@code energyFX} counter: accumulates the spend each client tick and fires (and restarts from
     *  zero) once it reaches the threshold. */
    public static final class Accumulator {
        private float value;

        /** @return true when a puff is due this tick. */
        public boolean tick(int energySpend, float threshold) {
            value += energySpend;
            if (value >= threshold) {
                value = 0;
                return true;
            }
            return false;
        }
    }
}
