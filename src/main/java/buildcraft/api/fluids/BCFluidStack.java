/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.fluids;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * BuildCraft's own fluid stack: a fluid, an integer amount in millibuckets and a component patch, built from vanilla
 * types only so that it means the same thing on every loader (epic #98, "Fluids (value type)" in
 * {@code docs/platform-layer.md}).
 *
 * <p>The method names mirror NeoForge's {@code FluidStack} so that code migrates by swapping the import, and the
 * semantics follow it too: {@link #EMPTY} is a singleton, any stack of {@link Fluids#EMPTY} or with a non-positive
 * amount {@link #isEmpty() is empty}, {@code equals} is identity (compare with {@link #matches} or
 * {@link #isSameFluidSameComponents}), and the component prototype is the fluid holder's own components on the 26.x
 * line and empty before it.
 *
 * <p><b>Save format.</b> {@link #CODEC}, {@link #OPTIONAL_CODEC}, {@link #STREAM_CODEC} and
 * {@link #OPTIONAL_STREAM_CODEC} write exactly what NeoForge's codecs of the same names write ({@code id},
 * {@code amount}, {@code components}; the stream form is a var-int amount, then the fluid holder and the component
 * patch), which {@code BCFluidStackCodecParityTest} pins against NeoForge's own output and the golden fixtures.
 *
 * <p>{@link #limitSize}, {@link #copyAndClear}, {@link #isComponentsPatchEmpty} and {@link #lenientOptionalFieldOf} have
 * no caller in BuildCraft: they are the rest of that mirror, kept for addon code written against NeoForge's
 * {@code FluidStack} (the decision recorded in {@code docs/platform-layer.md}, "Fluids (value type)").
 *
 * <p>Converting to and from a loader's own fluid type happens only in that loader's platform implementation. (No
 * nullness annotations: this class also compiles on the Fabric node, which has no {@code javax.annotation}.)
 */
public final class BCFluidStack implements DataComponentHolder {
    private static final String FIELD_ID = "id";
    private static final String FIELD_AMOUNT = "amount";
    private static final String FIELD_COMPONENTS = "components";

    /** A fluid holder that may not be {@code minecraft:empty} (and, on 26.x, must have its components bound). */
    public static final Codec<Holder<Fluid>> FLUID_NON_EMPTY_CODEC = BuiltInRegistries.FLUID.holderByNameCodec()
        .validate(holder -> holder.value() == Fluids.EMPTY
            ? DataResult.error(() -> "Fluid must not be minecraft:empty")
            : DataResult.success(holder))
        //? if >=26.1 {
        .validate(holder -> !holder.areComponentsBound()
            ? DataResult.error(() -> "Fluid " + holder.getRegisteredName() + " does not have components yet")
            : DataResult.success(holder))
        //?}
        ;

    private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Fluid>> FLUID_HOLDER_STREAM_CODEC =
        ByteBufCodecs.holderRegistry(Registries.FLUID);

    /** A map codec for stacks that does not accept empty stacks. */
    public static final MapCodec<BCFluidStack> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        FLUID_NON_EMPTY_CODEC.fieldOf(FIELD_ID).forGetter(BCFluidStack::getFluidHolder),
        ExtraCodecs.POSITIVE_INT.fieldOf(FIELD_AMOUNT).forGetter(BCFluidStack::getAmount),
        DataComponentPatch.CODEC.optionalFieldOf(FIELD_COMPONENTS, DataComponentPatch.EMPTY)
            .forGetter(stack -> stack.components.asPatch())
    ).apply(instance, BCFluidStack::new));

    /** A codec for stacks that does not accept empty stacks. */
    public static final Codec<BCFluidStack> CODEC = Codec.lazyInitialized(MAP_CODEC::codec);

    /** A codec for stacks that accepts empty stacks, writing them as {@code {}}. */
    public static final Codec<BCFluidStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
        .xmap(optional -> optional.orElse(BCFluidStack.EMPTY),
            stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));

    /** A stream codec for stacks that accepts empty stacks (an empty stack is a single var-int {@code 0}). */
    public static final StreamCodec<RegistryFriendlyByteBuf, BCFluidStack> OPTIONAL_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BCFluidStack decode(RegistryFriendlyByteBuf buf) {
            int amount = buf.readVarInt();
            if (amount <= 0) {
                return BCFluidStack.EMPTY;
            }
            Holder<Fluid> holder = FLUID_HOLDER_STREAM_CODEC.decode(buf);
            DataComponentPatch patch = DataComponentPatch.STREAM_CODEC.decode(buf);
            return new BCFluidStack(holder, amount, patch);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BCFluidStack stack) {
            if (stack.isEmpty()) {
                buf.writeVarInt(0);
            } else {
                buf.writeVarInt(stack.getAmount());
                FLUID_HOLDER_STREAM_CODEC.encode(buf, stack.getFluidHolder());
                DataComponentPatch.STREAM_CODEC.encode(buf, stack.components.asPatch());
            }
        }
    };

    /** A stream codec for stacks that does not accept empty stacks. */
    public static final StreamCodec<RegistryFriendlyByteBuf, BCFluidStack> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BCFluidStack decode(RegistryFriendlyByteBuf buf) {
            BCFluidStack stack = OPTIONAL_STREAM_CODEC.decode(buf);
            if (stack.isEmpty()) {
                throw new DecoderException("Empty FluidStack not allowed");
            }
            return stack;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, BCFluidStack stack) {
            if (stack.isEmpty()) {
                throw new EncoderException("Empty FluidStack not allowed");
            }
            OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }
    };

    public static final BCFluidStack EMPTY = new BCFluidStack();

    /** Names a stack for display. Installed by BuildCraft's lib from the loader's fluid services. */
    private static volatile Function<BCFluidStack, Component> hoverNames = BCFluidStack::fallbackHoverName;

    private final Holder<Fluid> fluid;
    private int amount;
    private final PatchedDataComponentMap components;

    public BCFluidStack(Fluid fluid, int amount) {
        this(holderOf(fluid), amount, DataComponentPatch.EMPTY);
    }

    public BCFluidStack(Fluid fluid, int amount, DataComponentPatch patch) {
        this(holderOf(fluid), amount, patch);
    }

    public BCFluidStack(Holder<Fluid> fluid, int amount) {
        this(fluid, amount, DataComponentPatch.EMPTY);
    }

    public BCFluidStack(Holder<Fluid> fluid, int amount, DataComponentPatch patch) {
        this(fluid, amount, PatchedDataComponentMap.fromPatch(prototypeOf(fluid), patch));
    }

    private BCFluidStack(Holder<Fluid> fluid, int amount, PatchedDataComponentMap components) {
        this.fluid = fluid;
        this.amount = amount;
        this.components = components;
    }

    private BCFluidStack() {
        this.fluid = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    private static Holder<Fluid> holderOf(Fluid fluid) {
        return BuiltInRegistries.FLUID.wrapAsHolder(fluid);
    }

    private static DataComponentMap prototypeOf(Holder<Fluid> fluid) {
        //? if >=26.1 {
        return fluid.components();
        //?} else {
        /*return DataComponentMap.EMPTY;*/
        //?}
    }

    // ---- identity --------------------------------------------------------------------------------------------------

    /** Whether this is {@link #EMPTY}, a stack of {@link Fluids#EMPTY}, or a stack with a non-positive amount. */
    public boolean isEmpty() {
        return this == EMPTY || fluid == null || fluid.value() == Fluids.EMPTY || amount <= 0;
    }

    /** The fluid, or {@link Fluids#EMPTY} if this stack is empty. */
    public Fluid getFluid() {
        return isEmpty() ? Fluids.EMPTY : fluid.value();
    }

    /** The fluid's registry holder ({@link Fluids#EMPTY}'s if this stack is empty). */
    public Holder<Fluid> getFluidHolder() {
        return isEmpty() ? holderOf(Fluids.EMPTY) : fluid;
    }

    public boolean is(Fluid other) {
        return getFluid() == other;
    }

    public boolean is(TagKey<Fluid> tag) {
        return getFluidHolder().is(tag);
    }

    public boolean is(Holder<Fluid> holder) {
        return is(holder.value());
    }

    public boolean is(HolderSet<Fluid> holderSet) {
        return holderSet.contains(getFluidHolder());
    }

    public boolean is(Predicate<Holder<Fluid>> holderPredicate) {
        return holderPredicate.test(getFluidHolder());
    }

    public Stream<TagKey<Fluid>> getTags() {
        return getFluidHolder().tags();
    }

    // ---- amount ----------------------------------------------------------------------------------------------------

    /** The amount in millibuckets, or {@code 0} if this stack is empty. */
    public int getAmount() {
        return isEmpty() ? 0 : amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public void grow(int addedAmount) {
        setAmount(getAmount() + addedAmount);
    }

    public void shrink(int removedAmount) {
        grow(-removedAmount);
    }

    /** Limits the amount of this stack to at most the given amount. */
    public void limitSize(int max) {
        if (!isEmpty() && getAmount() > max) {
            setAmount(max);
        }
    }

    /** Splits off a stack of (at most) the given amount and reduces this stack by it. */
    public BCFluidStack split(int splitAmount) {
        int taken = Math.min(splitAmount, getAmount());
        BCFluidStack split = copyWithAmount(taken);
        shrink(taken);
        return split;
    }

    // ---- copies ----------------------------------------------------------------------------------------------------

    public BCFluidStack copy() {
        if (isEmpty()) {
            return EMPTY;
        }
        return new BCFluidStack(fluid, amount, components.copy());
    }

    public BCFluidStack copyWithAmount(int newAmount) {
        if (isEmpty()) {
            return EMPTY;
        }
        BCFluidStack copy = copy();
        copy.setAmount(newAmount);
        return copy;
    }

    /** A copy of this stack, leaving this stack with {@code 0} amount. */
    public BCFluidStack copyAndClear() {
        if (isEmpty()) {
            return EMPTY;
        }
        BCFluidStack copy = copy();
        setAmount(0);
        return copy;
    }

    // ---- comparison ------------------------------------------------------------------------------------------------

    /** Same fluid, amount and components. */
    public static boolean matches(BCFluidStack first, BCFluidStack second) {
        if (first == second) {
            return true;
        }
        return first.getAmount() == second.getAmount() && isSameFluidSameComponents(first, second);
    }

    /** Same fluid; ignores amount and components. */
    public static boolean isSameFluid(BCFluidStack first, BCFluidStack second) {
        return first.is(second.getFluid());
    }

    /** Same fluid and components; ignores amount. */
    public static boolean isSameFluidSameComponents(BCFluidStack first, BCFluidStack second) {
        if (!first.is(second.getFluid())) {
            return false;
        }
        return first.isEmpty() && second.isEmpty() || Objects.equals(first.components, second.components);
    }

    /** Hashes the fluid and components of a stack, ignoring the amount. */
    public static int hashFluidAndComponents(BCFluidStack stack) {
        if (stack == null) {
            return 0;
        }
        int i = 31 + stack.getFluid().hashCode();
        return 31 * i + stack.getComponents().hashCode();
    }

    /**
     * An optional field reading a broken stack as {@link #EMPTY} instead of failing. {@link #EMPTY} encodes as an
     * absent field, so saving what was read drops the broken stack, as NeoForge's method of the same name does.
     */
    public static MapCodec<BCFluidStack> lenientOptionalFieldOf(String fieldName) {
        return CODEC.lenientOptionalFieldOf(fieldName)
            .xmap(optional -> optional.orElse(EMPTY), stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
    }

    // ---- components ------------------------------------------------------------------------------------------------

    @Override
    public DataComponentMap getComponents() {
        return isEmpty() ? DataComponentMap.EMPTY : components;
    }

    /** The changes against the fluid's prototype components ({@link DataComponentPatch#EMPTY} if this is empty). */
    public DataComponentPatch getComponentsPatch() {
        return isEmpty() ? DataComponentPatch.EMPTY : components.asPatch();
    }

    public boolean isComponentsPatchEmpty() {
        return isEmpty() || components.asPatch().isEmpty();
    }

    public <T> T set(DataComponentType<T> type, T value) {
        return components.set(type, value);
    }

    public <T> T remove(DataComponentType<? extends T> type) {
        return components.remove(type);
    }

    public void applyComponents(DataComponentPatch patch) {
        components.applyPatch(patch);
    }

    // ---- display ---------------------------------------------------------------------------------------------------

    /** The display name of this stack, as the loader names fluids. */
    public Component getHoverName() {
        return hoverNames.apply(this);
    }

    /**
     * Installs how {@link #getHoverName()} names a stack. BuildCraft's lib installs the loader's fluid naming at
     * startup; before that a stack is named after its fluid's legacy block.
     */
    public static void setHoverNameProvider(Function<BCFluidStack, Component> provider) {
        hoverNames = Objects.requireNonNull(provider, "provider");
    }

    private static Component fallbackHoverName(BCFluidStack stack) {
        return stack.getFluid().defaultFluidState().createLegacyBlock().getBlock().getName();
    }

    @Override
    public String toString() {
        return getAmount() + " " + getFluid();
    }
}
