/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.fluids;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.VanillaSetupBaseTester;
import buildcraft.lib.test.GoldenFixtures;
import buildcraft.platform.Platform;
import buildcraft.platform.neoforge.NeoForgeFluids;

/**
 * {@link BCFluidStack} must serialise exactly as NeoForge's {@link FluidStack} does (epic #98, S13a, written before
 * any call site swaps): every codec is run on an equal pair of stacks and the NBT, JSON and stream bytes compared,
 * the NBT form is also checked against S12's golden fixtures, and each type decodes what the other wrote.
 */
public class BCFluidStackCodecParityTest extends VanillaSetupBaseTester {

    /** Equal pairs: plain, with a component patch, and a bucket-plus amount of a second fluid. */
    private static List<Pair> pairs() {
        BCFluidStack patchedBc = new BCFluidStack(Fluids.WATER, 250);
        patchedBc.set(DataComponents.RARITY, Rarity.EPIC);
        FluidStack patchedNeo = new FluidStack(Fluids.WATER, 250);
        patchedNeo.set(DataComponents.RARITY, Rarity.EPIC);
        return List.of(
            new Pair("plain", new BCFluidStack(Fluids.LAVA, 1234), new FluidStack(Fluids.LAVA, 1234)),
            new Pair("components", patchedBc, patchedNeo),
            new Pair("large", new BCFluidStack(Fluids.WATER, 64_000), new FluidStack(Fluids.WATER, 64_000)));
    }

    private record Pair(String name, BCFluidStack bc, FluidStack neo) {}

    // ---- Codec -------------------------------------------------------------------------------------------------------

    @Test
    public void codecNbtMatchesNeoForge() {
        for (Pair p : pairs()) {
            Tag bc = BCFluidStack.CODEC.encodeStart(NbtOps.INSTANCE, p.bc()).getOrThrow();
            Tag neo = FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, p.neo()).getOrThrow();
            Assertions.assertEquals(neo, bc, p.name() + ": CODEC NBT");
        }
    }

    @Test
    public void codecJsonMatchesNeoForge() {
        for (Pair p : pairs()) {
            JsonElement bc = BCFluidStack.CODEC.encodeStart(JsonOps.INSTANCE, p.bc()).getOrThrow();
            JsonElement neo = FluidStack.CODEC.encodeStart(JsonOps.INSTANCE, p.neo()).getOrThrow();
            Assertions.assertEquals(neo.toString(), bc.toString(), p.name() + ": CODEC JSON");
        }
    }

    @Test
    public void codecMatchesGoldenFixtures() {
        Pair plain = pairs().get(0);
        Pair patched = pairs().get(1);
        Assertions.assertEquals(GoldenFixtures.load("fluidstack_codec_plain"),
            BCFluidStack.CODEC.encodeStart(NbtOps.INSTANCE, plain.bc()).getOrThrow(), "plain vs golden fixture");
        Assertions.assertEquals(GoldenFixtures.load("fluidstack_codec_components"),
            BCFluidStack.CODEC.encodeStart(NbtOps.INSTANCE, patched.bc()).getOrThrow(), "components vs golden fixture");
        Assertions.assertEquals(GoldenFixtures.load("fluidstack_optional_empty"),
            BCFluidStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, BCFluidStack.EMPTY).getOrThrow(),
            "optional empty vs golden fixture");
    }

    @Test
    public void codecsDecodeEachOther() {
        for (Pair p : pairs()) {
            Tag fromNeo = FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, p.neo()).getOrThrow();
            BCFluidStack bc = BCFluidStack.CODEC.parse(NbtOps.INSTANCE, fromNeo).getOrThrow();
            Assertions.assertTrue(BCFluidStack.matches(p.bc(), bc), p.name() + ": BC decodes NeoForge's NBT");

            Tag fromBc = BCFluidStack.CODEC.encodeStart(NbtOps.INSTANCE, p.bc()).getOrThrow();
            FluidStack neo = FluidStack.CODEC.parse(NbtOps.INSTANCE, fromBc).getOrThrow();
            Assertions.assertTrue(FluidStack.matches(p.neo(), neo), p.name() + ": NeoForge decodes BC's NBT");
        }
    }

    @Test
    public void codecRejectsWhatNeoForgeRejects() {
        Assertions.assertTrue(BCFluidStack.CODEC.encodeStart(NbtOps.INSTANCE, BCFluidStack.EMPTY).isError(),
            "CODEC must refuse an empty stack");
        Tag emptyFluid = FluidStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, FluidStack.EMPTY).getOrThrow();
        Assertions.assertTrue(BCFluidStack.CODEC.parse(NbtOps.INSTANCE, emptyFluid).isError(),
            "CODEC must refuse {}");
    }

    @Test
    public void optionalCodecMatchesNeoForge() {
        List<Pair> all = new java.util.ArrayList<>(pairs());
        all.add(new Pair("empty", BCFluidStack.EMPTY, FluidStack.EMPTY));
        all.add(new Pair("zero", new BCFluidStack(Fluids.WATER, 0), new FluidStack(Fluids.WATER, 0)));
        for (Pair p : all) {
            Tag bc = BCFluidStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, p.bc()).getOrThrow();
            Tag neo = FluidStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, p.neo()).getOrThrow();
            Assertions.assertEquals(neo, bc, p.name() + ": OPTIONAL_CODEC NBT");
            Assertions.assertTrue(BCFluidStack.matches(p.bc().isEmpty() ? BCFluidStack.EMPTY : p.bc(),
                BCFluidStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, neo).getOrThrow()), p.name() + ": decodes");
        }
    }

    // ---- StreamCodec -------------------------------------------------------------------------------------------------

    private static RegistryFriendlyByteBuf buf() {
        return Platform.network().registryBuf(Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static <T> String bytes(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = buf();
        codec.encode(buf, value);
        return ByteBufUtil.hexDump(buf);
    }

    @Test
    public void streamCodecsMatchNeoForge() {
        for (Pair p : pairs()) {
            Assertions.assertEquals(bytes(FluidStack.STREAM_CODEC, p.neo()), bytes(BCFluidStack.STREAM_CODEC, p.bc()),
                p.name() + ": STREAM_CODEC bytes");
            Assertions.assertEquals(bytes(FluidStack.OPTIONAL_STREAM_CODEC, p.neo()),
                bytes(BCFluidStack.OPTIONAL_STREAM_CODEC, p.bc()), p.name() + ": OPTIONAL_STREAM_CODEC bytes");

            RegistryFriendlyByteBuf neoBytes = buf();
            FluidStack.STREAM_CODEC.encode(neoBytes, p.neo());
            Assertions.assertTrue(BCFluidStack.matches(p.bc(), BCFluidStack.STREAM_CODEC.decode(neoBytes)),
                p.name() + ": BC decodes NeoForge's stream");
        }
        Assertions.assertEquals(bytes(FluidStack.OPTIONAL_STREAM_CODEC, FluidStack.EMPTY),
            bytes(BCFluidStack.OPTIONAL_STREAM_CODEC, BCFluidStack.EMPTY), "empty OPTIONAL_STREAM_CODEC bytes");
        assertThrows(() -> bytes(BCFluidStack.STREAM_CODEC, BCFluidStack.EMPTY), "STREAM_CODEC must refuse empty");
    }

    private static void assertThrows(Supplier<?> action, String message) {
        Assertions.assertThrows(RuntimeException.class, action::get, message);
    }

    // ---- Semantics and conversion ------------------------------------------------------------------------------------

    @Test
    public void semanticsMatchNeoForge() {
        for (Pair p : pairs()) {
            BCFluidStack bc = p.bc();
            FluidStack neo = p.neo();
            Assertions.assertEquals(neo.getAmount(), bc.getAmount(), p.name());
            Assertions.assertSame(neo.getFluid(), bc.getFluid(), p.name());
            Assertions.assertEquals(neo.getComponentsPatch(), bc.getComponentsPatch(), p.name());
            Assertions.assertEquals(neo.getHoverName(), bc.getHoverName(), p.name() + ": hover name");
            Assertions.assertEquals(FluidStack.hashFluidAndComponents(neo), BCFluidStack.hashFluidAndComponents(bc),
                p.name() + ": hash");
        }
        BCFluidStack a = new BCFluidStack(Fluids.WATER, 100);
        BCFluidStack b = new BCFluidStack(Fluids.WATER, 200);
        Assertions.assertTrue(BCFluidStack.isSameFluidSameComponents(a, b));
        Assertions.assertFalse(BCFluidStack.matches(a, b));
        b.set(DataComponents.RARITY, Rarity.RARE);
        Assertions.assertTrue(BCFluidStack.isSameFluid(a, b));
        Assertions.assertFalse(BCFluidStack.isSameFluidSameComponents(a, b));

        BCFluidStack c = new BCFluidStack(Fluids.LAVA, 10);
        c.shrink(10);
        Assertions.assertTrue(c.isEmpty());
        Assertions.assertSame(Fluids.EMPTY, c.getFluid());
        Assertions.assertSame(BCFluidStack.EMPTY, c.copy());
        Assertions.assertTrue(new BCFluidStack(Fluids.EMPTY, 1000).isEmpty());
    }

    @Test
    public void neoForgeConversionRoundTrips() {
        for (Pair p : pairs()) {
            FluidStack neo = NeoForgeFluids.toNeo(p.bc());
            Assertions.assertTrue(FluidStack.matches(p.neo(), neo), p.name() + ": toNeo");
            Assertions.assertTrue(BCFluidStack.matches(p.bc(), NeoForgeFluids.fromNeo(neo)), p.name() + ": fromNeo");
        }
        Assertions.assertSame(FluidStack.EMPTY, NeoForgeFluids.toNeo(BCFluidStack.EMPTY));
        Assertions.assertSame(BCFluidStack.EMPTY, NeoForgeFluids.fromNeo(FluidStack.EMPTY));
    }
}
