/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.entity;

import java.lang.reflect.Field;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.builders.snapshot.NbtPath;
import buildcraft.builders.snapshot.RequiredExtractor;
import buildcraft.lib.test.GoldenFixtures;
import buildcraft.lib.test.ValueIoTestUtil;
import buildcraft.robotics.BCRoboticsEntities;

/**
 * Golden save format of the robot's saved fluid (the entity needs a real level, hence a game test; the other golden
 * fixtures are unit tests in {@code GoldenSaveFormatTester}, see {@link GoldenFixtures}).
 */
public class GoldenRobotSaveTester {

    private static EntityRobot newRobot(GameTestHelper helper) {
        return new EntityRobot(BCRoboticsEntities.ROBOT.get(), helper.getLevel());
    }

    private static void setTank(EntityRobot robot, FluidStack stack) {
        try {
            Field f = EntityRobot.class.getDeclaredField("tank");
            f.setAccessible(true);
            f.set(robot, stack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void robotSaveFormat(GameTestHelper helper) {
        EntityRobot robot = newRobot(helper);
        setTank(robot, new FluidStack(Fluids.LAVA, 2750));
        CompoundTag saved = ValueIoTestUtil.write(robot::writeData);
        GoldenFixtures.assertGolden("entity_robot", saved);

        // Snapshot pricing reads the entity's save through the same tank rule the rules files use.
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(NbtPath.class, NbtPath.DESERIALIZER)
                .registerTypeAdapter(RequiredExtractor.class, RequiredExtractor.DESERIALIZER)
                .create();
        RequiredExtractor rule = gson.fromJson(JsonParser.parseString("{\"type\":\"tank\",\"path\":[\"tank\"]}"),
                RequiredExtractor.class);
        ListTag priced = new ListTag();
        for (FluidStack stack : rule.extractFluidsFromEntity(saved)) {
            priced.add(FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());
        }
        CompoundTag pricedTag = new CompoundTag();
        pricedTag.put("fluids", priced);
        GoldenFixtures.assertGolden("snapshot_priced_robot", pricedTag);

        if (!GoldenFixtures.isUpdating()) {
            EntityRobot reloaded = newRobot(helper);
            reloaded.readData(ValueIoTestUtil.inputOf(GoldenFixtures.load("entity_robot")));
            CompoundTag again = ValueIoTestUtil.write(reloaded::writeData);
            // Loading a boardless robot installs the empty default board, which a fresh save then lists: that
            // board data is not the saved fluid format, so the round trip compares everything else.
            CompoundTag fixture = GoldenFixtures.load("entity_robot").copy();
            for (CompoundTag t : new CompoundTag[] { again, fixture }) {
                t.remove("board");
                t.remove("boardId");
                t.remove("mainAI");
            }
            if (!fixture.equals(again)) {
                throw new AssertionError("robot fixture -> entity -> save must reproduce the fixture, got "
                        + GoldenFixtures.render(again));
            }
        }
        helper.succeed();
    }
}
