/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;

import buildcraft.robotics.BCRobotics;
import buildcraft.robotics.BCRoboticsParticles;
import buildcraft.robotics.particle.RobotEnergyParticleOptions;

/**
 * Pins the robot's red energy exhaust — 7.1.x's {@code EntityRobotEnergyParticle}, which the port had
 * replaced with a fixed white vanilla {@code CLOUD} (robotics gameplay audit B15).
 *
 * <p>What is checkable without a client: the two scaling rules 7.1.x's {@code EntityRobot} applied (puff
 * rate by the particle setting, puff size by the running AI's cost), the particle type's registration and
 * data round-trip, and the particle description that points the type at the vanilla smoke frames 7.1.x
 * animated through. The particle's own motion and colour are client-only and need an in-client look.
 */
public class RobotExhaustTest {

    // ── Rate: 7.1.x's 100 << (2 * particleSetting) ─────────────────────────

    @Test
    public void thresholdQuadruplesPerParticleSettingStep() {
        Assertions.assertEquals(100F, RobotExhaust.particleThreshold(0), "All: one puff per 100 spend");
        Assertions.assertEquals(400F, RobotExhaust.particleThreshold(1), "Decreased: one puff per 400 spend");
        Assertions.assertEquals(1600F, RobotExhaust.particleThreshold(2), "Minimal: one puff per 1600 spend");
    }

    @Test
    public void thresholdClampsAnUnknownSettingIntoRange() {
        Assertions.assertEquals(100F, RobotExhaust.particleThreshold(-1));
        Assertions.assertEquals(1600F, RobotExhaust.particleThreshold(3),
                "a setting past Minimal must not shift into an absurd (or overflowing) threshold");
    }

    @Test
    public void accumulatorEmitsOncePerThresholdAndResets() {
        RobotExhaust.Accumulator acc = new RobotExhaust.Accumulator();
        int puffs = 0;
        // Spend 16 per cycle (AIRobotAttack's cost) against the All threshold of 100: a puff on the 7th cycle
        // (112 >= 100), then the counter restarts from zero exactly as 7.1.x's energyFX = 0 did.
        for (int tick = 0; tick < 14; tick++) {
            if (acc.tick(16, 100F)) {
                puffs++;
            }
        }
        Assertions.assertEquals(2, puffs);
        Assertions.assertFalse(acc.tick(0, 100F), "an idle robot (spend 0) never puffs");
    }

    @Test
    public void accumulatorFiresExactlyAtTheThreshold() {
        RobotExhaust.Accumulator acc = new RobotExhaust.Accumulator();
        Assertions.assertFalse(acc.tick(99, 100F));
        Assertions.assertFalse(acc.tick(0, 100F));
        Assertions.assertTrue(new RobotExhaust.Accumulator().tick(100, 100F), "7.1.x compared with >=, not >");
    }

    // ── The particle setting ───────────────────────────────────────────────

    @Test
    public void particleSettingMapsByConstantName() {
        Assertions.assertEquals(0, RobotExhaust.particleSettingId("ALL"));
        Assertions.assertEquals(1, RobotExhaust.particleSettingId("DECREASED"));
        Assertions.assertEquals(2, RobotExhaust.particleSettingId("MINIMAL"));
        Assertions.assertEquals(0, RobotExhaust.particleSettingId("SOMETHING_NEW"), "unknown settings read as All");
    }

    /** Tripwire for a Minecraft update: the client maps the live {@code ParticleStatus} by constant name, so a
     *  renamed or added constant must fail here rather than silently read as All. The enum lives in
     *  {@code net.minecraft.client} on 1.21.1 and {@code net.minecraft.server.level} from 1.21.10, hence the
     *  by-name lookup instead of a Stonecutter directive in shared test source. */
    @Test
    public void vanillaParticleStatusStillHasExactlyTheThreeKnownConstants() throws Exception {
        Class<?> status;
        try {
            status = Class.forName("net.minecraft.server.level.ParticleStatus");
        } catch (ClassNotFoundException e) {
            status = Class.forName("net.minecraft.client.ParticleStatus");
        }
        List<String> names = new ArrayList<>();
        for (Object constant : status.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        Assertions.assertEquals(List.of("ALL", "DECREASED", "MINIMAL"), names);
    }

    @Test
    public void particleSettingSeamReadsTheInstalledSource() {
        java.util.function.IntSupplier serverDefault = () -> 0; // no client in the unit-test JVM
        try {
            RobotExhaust.setParticleSettingSource(() -> 2);
            Assertions.assertEquals(2, RobotExhaust.particleSetting());
            Assertions.assertEquals(1600F, RobotExhaust.particleThreshold(RobotExhaust.particleSetting()));
        } finally {
            RobotExhaust.setParticleSettingSource(serverDefault);
        }
    }

    // ── Size: 7.1.x's max(1, spend * 0.075) ────────────────────────────────

    @Test
    public void sizeIsAtLeastOneForCheapAis() {
        Assertions.assertEquals(1F, RobotExhaust.particleSize(0));
        Assertions.assertEquals(1F, RobotExhaust.particleSize(3), "AIRobotGoto (3 RF-equivalent)");
        Assertions.assertEquals(1F, RobotExhaust.particleSize(13), "13 * 0.075 = 0.975 still floors to 1");
    }

    @Test
    public void sizeGrowsWithCostlierAis() {
        Assertions.assertEquals(1.2F, RobotExhaust.particleSize(16), 1.0E-6F, "AIRobotAttack (16 RF-equivalent)");
        Assertions.assertTrue(RobotExhaust.particleSize(16) > RobotExhaust.particleSize(15));
    }

    @Test
    public void sizeIsCappedSoAnExpensiveAddonAiCannotFillTheScreen() {
        Assertions.assertEquals(RobotExhaust.MAX_PARTICLE_SIZE, RobotExhaust.particleSize(1_000_000));
        Assertions.assertTrue(RobotExhaust.MAX_PARTICLE_SIZE > RobotExhaust.particleSize(16),
                "the cap must sit above every built-in AI's size, so it never changes vanilla-robot visuals");
    }

    // ── Registration and data ──────────────────────────────────────────────

    @Test
    public void particleTypeIsRegistered() {
        Assertions.assertEquals(BCRobotics.MODID + ":robot_energy",
                String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(BCRoboticsParticles.ROBOT_ENERGY.get())));
    }

    @Test
    public void optionsCarryTheirTypeAndRoundTripTheSize() {
        RobotEnergyParticleOptions options = new RobotEnergyParticleOptions(1.2F);
        Assertions.assertSame(BCRoboticsParticles.ROBOT_ENERGY.get(), options.getType());

        JsonElement json = RobotEnergyParticleOptions.MAP_CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, options).getOrThrow();
        RobotEnergyParticleOptions fromJson = RobotEnergyParticleOptions.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, json).getOrThrow();
        Assertions.assertEquals(options, fromJson);

        ByteBuf buf = Unpooled.buffer();
        RobotEnergyParticleOptions.STREAM_CODEC.encode(buf, options);
        Assertions.assertEquals(options, RobotEnergyParticleOptions.STREAM_CODEC.decode(buf));
    }

    /** 7.1.x drew the puff from the classic particle sheet's smoke frames, {@code 7 - age * 8 / maxAge} —
     *  big to small. Those frames are vanilla's {@code generic_7 .. generic_0}, the same list (in the same
     *  order) as vanilla's own {@code smoke.json}, so the particle ships no art of its own. */
    @Test
    public void particleDescriptionAnimatesThroughTheVanillaSmokeFrames() throws Exception {
        String path = "/assets/" + BCRobotics.MODID + "/particles/robot_energy.json";
        List<String> textures = new ArrayList<>();
        try (InputStream in = RobotExhaustTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(in, "missing particle description " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            root.getAsJsonArray("textures").forEach(e -> textures.add(e.getAsString()));
        }
        List<String> expected = new ArrayList<>();
        for (int frame = 7; frame >= 0; frame--) {
            expected.add("minecraft:generic_" + frame);
        }
        Assertions.assertEquals(expected, textures);
    }
}
