/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.fluid;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.builders.snapshot.NbtPath;
import buildcraft.builders.snapshot.RequiredExtractor;
import buildcraft.builders.snapshot.SchematicBlockFluid;
import buildcraft.core.BCCore;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.tile.TileDistiller_BC8;
import buildcraft.factory.tile.TileHeatExchange;
import buildcraft.factory.tile.TileTank;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.test.GoldenFixtures;
import buildcraft.lib.test.ValueIoTestUtil;

/**
 * Golden save-format characterisation for everything that persists a fluid, written BEFORE the platform layer swaps
 * {@code FluidStack} for {@code BCFluidStack} and the Transfer API for BuildCraft's own (epic #98, S13/S14): each test
 * builds a populated instance, serialises it, and compares to the committed fixture under
 * {@code src/test/resources/golden/} (see {@link GoldenFixtures} for the layout and how to regenerate), then loads the
 * fixture into a fresh instance and checks it re-serialises to the same bytes.
 */
public class GoldenSaveFormatTester extends VanillaSetupBaseTester {

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static FluidStack lava(int mb) {
        return new FluidStack(Fluids.LAVA, mb);
    }

    private static FluidStack water(int mb) {
        return new FluidStack(Fluids.WATER, mb);
    }

    /** Save, compare to the fixture, then load the fixture into a fresh object and require the same bytes again. */
    private static <T> void pinTile(String name, T populated, Supplier<T> fresh,
                                    java.util.function.BiConsumer<T, BCValueOutput> writer,
                                    java.util.function.BiConsumer<T, BCValueInput> reader) {
        CompoundTag saved = ValueIoTestUtil.write(out -> writer.accept(populated, out));
        GoldenFixtures.assertGolden(name, saved);
        if (GoldenFixtures.isUpdating()) {
            return;
        }
        T reloaded = fresh.get();
        reader.accept(reloaded, ValueIoTestUtil.inputOf(GoldenFixtures.load(name)));
        Assertions.assertEquals(GoldenFixtures.load(name), ValueIoTestUtil.write(out -> writer.accept(reloaded, out)),
                name + ": fixture -> instance -> save must reproduce the fixture");
    }


    /** {@code writeData}/{@code readData} are protected on tiles and entities in other packages: reach them reflectively. */
    private static void saveTo(Object holder, BCValueOutput out) {
        invoke(holder, "writeData", BCValueOutput.class, out);
    }

    private static void loadFrom(Object holder, BCValueInput in) {
        invoke(holder, "readData", BCValueInput.class, in);
    }

    private static void invoke(Object holder, String name, Class<?> argType, Object arg) {
        for (Class<?> c = holder.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(name, argType);
                m.setAccessible(true);
                m.invoke(holder, arg);
                return;
            } catch (NoSuchMethodException skip) {
                // look in the superclass
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException(holder.getClass() + " has no " + name);
    }

    private static Tag encode(FluidStack stack) {
        return FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow();
    }

    // ---- FluidStack.CODEC / SimpleFluidContent (S13 parity) ---------------------------------------------------------

    @Test
    public void fluidStackCodecShape() {
        FluidStack plain = lava(1234);
        GoldenFixtures.assertGolden("fluidstack_codec_plain", encode(plain));

        FluidStack patched = water(250);
        patched.set(DataComponents.RARITY, Rarity.EPIC);
        GoldenFixtures.assertGolden("fluidstack_codec_components", encode(patched));

        GoldenFixtures.assertGolden("fluidstack_optional_empty",
                FluidStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, FluidStack.EMPTY).getOrThrow());

        if (GoldenFixtures.isUpdating()) {
            return;
        }
        for (String name : List.of("fluidstack_codec_plain", "fluidstack_codec_components")) {
            Tag fixture = GoldenFixtures.load(name);
            FluidStack decoded = FluidStack.CODEC.parse(NbtOps.INSTANCE, fixture).getOrThrow();
            Assertions.assertEquals(fixture, encode(decoded), name + " round trip");
        }
        FluidStack decoded = FluidStack.CODEC.parse(NbtOps.INSTANCE, GoldenFixtures.load("fluidstack_codec_components"))
                .getOrThrow();
        Assertions.assertEquals(Rarity.EPIC, decoded.get(DataComponents.RARITY));
        Assertions.assertEquals(250, decoded.getAmount());
    }

    @Test
    public void simpleFluidContentComponent() {
        // BCCore.FLUID_CONTENT persists with SimpleFluidContent.CODEC: the shape is the FluidStack codec's.
        var type = BCCore.FLUID_CONTENT.get();
        Assertions.assertNotNull(type);
        SimpleFluidContent content = SimpleFluidContent.copyOf(lava(700));
        Tag encoded = SimpleFluidContent.CODEC.encodeStart(NbtOps.INSTANCE, content).getOrThrow();
        GoldenFixtures.assertGolden("simple_fluid_content", encoded);
        if (!GoldenFixtures.isUpdating()) {
            SimpleFluidContent decoded = SimpleFluidContent.CODEC.parse(NbtOps.INSTANCE, GoldenFixtures.load("simple_fluid_content")).getOrThrow();
            Assertions.assertEquals(content, decoded);
            Assertions.assertEquals(GoldenFixtures.load("simple_fluid_content"),
                    GoldenFixtures.asCompound(SimpleFluidContent.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow()));
        }
    }

    // ---- BCFluidTank / TileTank -------------------------------------------------------------------------------------

    private static BCFluidTank filledTank() {
        BCFluidTank tank = new BCFluidTank(2, 16_000);
        tank.setFluidStack(0, lava(4321));
        tank.setFluidStack(1, water(16_000));
        return tank;
    }

    @Test
    public void bcFluidTankSerialization() {
        // ">=1.21.10": the "stacks" list (StacksResourceHandler.VALUE_IO_KEY); 1.21.1: fluid<i>/amount<i>.
        pinTile("bcfluidtank", filledTank(), () -> new BCFluidTank(2, 16_000),
                (t, out) -> t.serialize(out.raw), (t, in) -> t.deserialize(in.raw));
    }

    private static TileTank newTank() {
        return new TileTank(BlockPos.ZERO, BCFactoryBlocks.TANK.get().defaultBlockState());
    }

    @Test
    public void tileTankSave() {
        TileTank tile = newTank();
        tile.tank.setFluidStack(0, lava(9000));
        pinTile("tile_tank", tile, GoldenSaveFormatTester::newTank, GoldenSaveFormatTester::saveTo, GoldenSaveFormatTester::loadFrom);
    }

    @Test
    public void tileTankPickBlockStripsTheFluidKey() {
        TileTank tile = newTank();
        tile.tank.setFluidStack(0, lava(9000));
        //? if >=1.21.10 {
        ValueIoTestUtil.Written written = ValueIoTestUtil.newOutput();
        saveTo(tile, written.out);
        Assertions.assertTrue(written.tag().contains("stacks"), "the fluid lives under \"stacks\"");
        tile.removeComponentsFromTag(written.out.raw);
        Assertions.assertFalse(written.tag().contains("stacks"), "removeComponentsFromTag must drop \"stacks\"");
        //?} else {
        /*CompoundTag tag = ValueIoTestUtil.write(out -> saveTo(tile, out));
        Assertions.assertTrue(tag.contains("fluid0") && tag.contains("amount0"), "the fluid lives under fluid0/amount0");
        tile.removeComponentsFromTag(tag);
        Assertions.assertFalse(tag.contains("fluid0") || tag.contains("amount0"), "removeComponentsFromTag must drop them");*/
        //?}
    }

    // ---- Distiller --------------------------------------------------------------------------------------------------

    private static TileDistiller_BC8 newDistiller() {
        return new TileDistiller_BC8(BlockPos.ZERO, BCFactoryBlocks.DISTILLER.get().defaultBlockState());
    }

    @Test
    public void distillerSave() {
        TileDistiller_BC8 tile = newDistiller();
        tile.getTankIn().setFluidStack(0, lava(1500));
        tile.getTankGasOut().setFluidStack(0, water(300));
        tile.getTankLiquidOut().setFluidStack(0, lava(40));
        pinTile("tile_distiller", tile, GoldenSaveFormatTester::newDistiller,
                GoldenSaveFormatTester::saveTo, GoldenSaveFormatTester::loadFrom);
    }

    // ---- Heat exchanger ---------------------------------------------------------------------------------------------

    private static TileHeatExchange newExchange() {
        return new TileHeatExchange(BlockPos.ZERO, BCFactoryBlocks.HEAT_EXCHANGE.get().defaultBlockState());
    }

    private static TileHeatExchange exchangeFrom(Consumer<BCValueOutput> shape) {
        TileHeatExchange tile = newExchange();
        loadFrom(tile, ValueIoTestUtil.inputOf(ValueIoTestUtil.write(shape)));
        return tile;
    }

    @Test
    public void heatExchangerStartSectionSave() {
        TileHeatExchange tile = exchangeFrom(out -> {
            out.putBoolean("hasSection", true);
            out.putBoolean("isStart", true);
            out.store("sectionInput", FluidStack.CODEC, lava(1200));
            out.store("sectionOutput", FluidStack.CODEC, water(80));
            out.putInt("middleCount", 3);
            out.putInt("progressState", 1);
        });
        pinTile("tile_heat_exchange_start", tile, GoldenSaveFormatTester::newExchange,
                GoldenSaveFormatTester::saveTo, GoldenSaveFormatTester::loadFrom);
    }

    @Test
    public void heatExchangerEndSectionSave() {
        TileHeatExchange tile = exchangeFrom(out -> {
            out.putBoolean("hasSection", true);
            out.putBoolean("isStart", false);
            out.store("sectionInput", FluidStack.CODEC, water(500));
            out.store("sectionOutput", FluidStack.CODEC, lava(900));
        });
        pinTile("tile_heat_exchange_end", tile, GoldenSaveFormatTester::newExchange,
                GoldenSaveFormatTester::saveTo, GoldenSaveFormatTester::loadFrom);
    }

    // ---- Snapshot / blueprint fluid entries -------------------------------------------------------------------------

    private static final Gson RULES_GSON = new GsonBuilder()
            .registerTypeAdapter(NbtPath.class, NbtPath.DESERIALIZER)
            .registerTypeAdapter(RequiredExtractor.class, RequiredExtractor.DESERIALIZER)
            .create();

    private static RequiredExtractor extractor(String json) {
        return RULES_GSON.fromJson(JsonParser.parseString(json), RequiredExtractor.class);
    }

    private static CompoundTag priced(RequiredExtractor extractor, CompoundTag tileNbt) {
        ListTag list = new ListTag();
        for (FluidStack stack : extractor.extractFluidsFromBlock(Blocks.AIR.defaultBlockState(), tileNbt)) {
            list.add(encode(stack));
        }
        CompoundTag result = new CompoundTag();
        result.put("fluids", list);
        return result;
    }

    @Test
    public void snapshotFluidPricing() {
        // The rule paths real rules use, run over the very tags the fixtures above pin: the snapshot reads the
        // tile's save, so a change to either side shows up here.
        String stacksPath = GoldenFixtures.group().equals("legacy") ? "fluid0" : "stacks";
        String tankRule = GoldenFixtures.group().equals("legacy")
                ? "{\"type\":\"tank\",\"path\":[\"fluid0\"],\"amountPath\":[\"amount0\"]}"
                : "{\"type\":\"tank\",\"path\":[\"" + stacksPath + "\"]}";
        TileTank tank = newTank();
        tank.tank.setFluidStack(0, lava(9000));
        CompoundTag tankSave = ValueIoTestUtil.write(out -> saveTo(tank, out));
        GoldenFixtures.assertGolden("snapshot_priced_tank", priced(extractor(tankRule), tankSave));

        TileDistiller_BC8 distiller = newDistiller();
        distiller.getTankIn().setFluidStack(0, lava(1500));
        CompoundTag distillerSave = ValueIoTestUtil.write(out -> saveTo(distiller, out));
        GoldenFixtures.assertGolden("snapshot_priced_distiller",
                priced(extractor("{\"type\":\"tank\",\"path\":[\"fluidIn\"]}"), distillerSave));
    }

    @Test
    public void schematicBlockFluidSave() throws Exception {
        CompoundTag blockState = new CompoundTag();
        blockState.putString("Name", "minecraft:lava");
        CompoundTag properties = new CompoundTag();
        properties.putString("level", "3");
        blockState.put("Properties", properties);
        CompoundTag in = new CompoundTag();
        in.put("blockState", blockState);
        in.putBoolean("isFlowing", true);

        SchematicBlockFluid schematic = new SchematicBlockFluid();
        schematic.deserializeNbt(in);
        CompoundTag saved = schematic.serializeNbt();
        GoldenFixtures.assertGolden("snapshot_schematic_block_fluid", saved);
        if (!GoldenFixtures.isUpdating()) {
            SchematicBlockFluid reloaded = new SchematicBlockFluid();
            reloaded.deserializeNbt(GoldenFixtures.load("snapshot_schematic_block_fluid"));
            Assertions.assertEquals(GoldenFixtures.load("snapshot_schematic_block_fluid"), reloaded.serializeNbt());
        }
    }
}
