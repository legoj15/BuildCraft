/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.JsonDeserializer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.IStackFilter;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.StackUtil;

import buildcraft.core.item.ItemMapLocation;
import buildcraft.core.item.ZoneMapPortability;

@SuppressWarnings("WeakerAccess")
public abstract class RequiredExtractor {
    @Nonnull
    public List<ItemStack> extractItemsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        return Collections.emptyList();
    }

    @Nonnull
    public List<FluidStack> extractFluidsFromBlock(@Nonnull BlockState blockState, @Nullable CompoundTag tileNbt) {
        return Collections.emptyList();
    }

    @Nonnull
    public List<ItemStack> extractItemsFromEntity(@Nonnull CompoundTag entityNbt) {
        return Collections.emptyList();
    }

    @Nonnull
    public List<FluidStack> extractFluidsFromEntity(@Nonnull CompoundTag entityNbt) {
        return Collections.emptyList();
    }

    /**
     * In-place strip the items this extractor would surface from {@code tileNbt}. Used by the
     * Builder's IGNORE container-contents mode: every extractor that declares an item-bearing
     * NBT path also knows where it lives, so callers can route a single
     * {@code rules → extractors → clearItemsFromBlock} pass over the tileNbt copy and have all
     * item-bearing paths stripped uniformly. Default is a no-op so the extractors that price no
     * contents (item_from_block, constant, …) don't accidentally mutate the tag.
     */
    public void clearItemsFromBlock(@Nonnull CompoundTag tileNbt) {
    }

    /**
     * Whether this extractor prices items the block entity holds (an inventory list, or one stored item such as a
     * lectern's book), which the Builder's IGNORE container-contents mode neither places nor charges: such an
     * extractor's {@link #clearItemsFromBlock} strips them from the placed copy. Tanks are not contents here.
     */
    public boolean isContainerContents() {
        return false;
    }

    /**
     * Whether this extractor prices something the block entity's NBT holds (an inventory list, a single stored item,
     * a tank). A schematic none of whose rules has such an extractor places its block entity empty (see
     * {@code SchematicBlockDefault#build}): whatever the captured NBT carries was never charged. False for the
     * extractors that price only the block (item_from_block) or a fixed list (constant).
     */
    public boolean pricesTileContents() {
        return false;
    }

    /**
     * Whether this extractor charges for an entity itself, whatever it holds: a fixed list of items or fluids
     * ({@code constant}), which the Builder's IGNORE container-contents mode never skips. False for every extractor
     * that reads the entity's data (the ones that price what it holds), and for {@code item_from_block}, which prices
     * a block and has no entity form: an entity rule needs one of these or it builds its entity for free.
     * <p>
     * This is deliberately narrower than "not {@link #isContainerContents() contents}": a {@code tank} extractor is
     * not contents (the IGNORE mode charges its fluid) but still prices only what the entity holds, so an entity with
     * an empty tank costs nothing. A rule that prices an entity only through a tank is therefore refused too, and
     * needs a {@code constant} beside it.
     */
    public boolean pricesEntityItself() {
        return false;
    }

    /**
     * One stack as an extractor's path finds it: vanilla's {@code ItemStack.CODEC} compound ({@code id}, {@code count},
     * {@code components}), or a BuildCraft {@code ItemHandlerSimple} slot, which wraps that compound under
     * {@code stack} and saves a count the codec cannot carry (over 99) beside it as {@code count}. Reading only the
     * first form priced every BuildCraft inventory at nothing. Empty when neither parses.
     *
     * <p>Priced as the build charges it: a zone map location as a blank map ({@link ItemMapLocation#pricedAsBlank}),
     * loose or inside a stack holding one (a shulker box), whose ref and carried plan no other world could match
     * ({@link ZoneMapPortability#withZoneMapsBlank(CompoundTag)}); {@link #payer} compares a held container alike.
     * The placed data keeps the refs and plans.
     */
    public static ItemStack parseItemStack(CompoundTag ct) {
        CompoundTag wrapped = NBTUtilBC.getCompoundOrNull(ct, "stack");
        CompoundTag payload = ZoneMapPortability.withZoneMapsBlank(wrapped != null ? wrapped : ct);
        ItemStack stack = ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), payload)
            .resultOrPartial()
            .orElse(ItemStack.EMPTY);
        if (wrapped != null && !stack.isEmpty() && ct.contains("count")) {
            stack = stack.copyWithCount(Math.max(1, NBTUtilBC.getInt(ct, "count", stack.getCount())));
        }
        return stack;
    }

    /** Whether {@code held} pays for {@code required}: {@link #payer}'s test, for one held stack. */
    public static boolean pays(ItemStack required, ItemStack held) {
        return payer(required).matches(held);
    }

    /**
     * The filter of held stacks that pay for {@code required}, a stack {@link #parseItemStack} priced: the same item
     * and components, or a container that holds them once the zone maps inside it are blank maps, as the price was
     * made ({@link ZoneMapPortability#withZoneMapsBlank(ItemStack)}). A zone map held loose pays only as the blank map
     * it is priced as, so a build never takes a loose written map. A container holding a written zone map is taken
     * whole, that map and its zone included: priced with the exact map, it could be paid for in no other world.
     *
     * <p>The comparison encodes the held stack, so it runs only when the exact match fails, the held stack has the
     * price's components (blanking a map inside a stack changes what a component holds, never which components the
     * stack has), and the price holds a map location at all ({@link ZoneMapPortability#holdsMapLocation}, which
     * remembers its answer per price).
     */
    public static IStackFilter payer(ItemStack required) {
        return new Payer(required);
    }

    private static final class Payer implements IStackFilter {
        private final ItemStack required;
        /** Whether the price holds a map location inside it; null until a held stack first needs it. */
        @Nullable
        private Boolean requiredHoldsMap;

        Payer(ItemStack required) {
            this.required = required;
        }

        @Override
        public boolean matches(ItemStack held) {
            if (StackUtil.canMerge(required, held)) {
                return true;
            }
            if (required.getItem() != held.getItem() || held.getItem() instanceof ItemMapLocation
                    || held.getComponentsPatch().isEmpty()
                    || !required.getComponents().keySet().equals(held.getComponents().keySet())) {
                return false;
            }
            if (requiredHoldsMap == null) {
                requiredHoldsMap = ZoneMapPortability.holdsMapLocation(required);
            }
            return requiredHoldsMap && StackUtil.canMerge(required, ZoneMapPortability.withZoneMapsBlank(held));
        }
    }

    /** {@code tag} read as an int, or {@code def} when it is not a number (a path that found nothing); through {@link NBTUtilBC} so it reads alike on every node. */
    static int readInt(@Nullable Tag tag, int def) {
        if (tag == null || tag.getId() < Tag.TAG_BYTE || tag.getId() > Tag.TAG_DOUBLE) return def;
        CompoundTag holder = new CompoundTag();
        holder.put("v", tag);
        return NBTUtilBC.getInt(holder, "v", def);
    }

    /** {@code tag} read as a string, or the empty string. */
    static String readString(@Nullable Tag tag) {
        if (tag == null || tag.getId() != Tag.TAG_STRING) return "";
        CompoundTag holder = new CompoundTag();
        holder.put("v", tag);
        return NBTUtilBC.getString(holder, "v", "");
    }

    public enum EnumType {
        CONSTANT(RequiredExtractorConstant.class),
        ITEM_FROM_BLOCK(RequiredExtractorItemFromBlock.class),
        ITEM(RequiredExtractorItem.class),
        ITEMS_LIST(RequiredExtractorItemsList.class),
        POT_SHERDS(RequiredExtractorPotSherds.class),
        TANK(RequiredExtractorTank.class);

        public final Class<? extends RequiredExtractor> clazz;

        EnumType(Class<? extends RequiredExtractor> clazz) {
            this.clazz = clazz;
        }

        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static EnumType byName(String name) {
            return Arrays.stream(values())
                .filter(type -> type.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Required extractor type not found"));
        }
    }

    public static final JsonDeserializer<RequiredExtractor> DESERIALIZER = (json, typeOfT, context) -> {
        EnumType type = EnumType.byName(json.getAsJsonObject().get("type").getAsString());
        json.getAsJsonObject().remove("type");
        return context.deserialize(json, type.clazz);
    };
}
