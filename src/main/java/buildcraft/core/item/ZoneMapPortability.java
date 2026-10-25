/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.jspecify.annotations.Nullable;

import buildcraft.core.BCCore;
import buildcraft.core.BCCoreItems;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.robotics.zone.ZoneMapRef;
import buildcraft.robotics.zone.ZoneMapSavedData;
import buildcraft.robotics.zone.ZonePlan;

/**
 * Carries ZONE map locations through a blueprint. A written zone map holds only a {@link ZoneMapRef} into its world's
 * {@link ZoneMapSavedData}, which names no zone in any other world; so the capture writes the plan each held ref names
 * into that stack's custom data, where builds before the map-id model kept it ({@link ItemMapLocation#TAG_LEGACY_ZONE}),
 * and the build hands it to the store of the world it is built in (the legacy-map migration,
 * {@link ItemMapLocation#migrateLegacyZone}). The blueprint keeps the ref beside the plan, so a build in the world it
 * was captured in names the same entry.
 *
 * <p>The walk finds every saved item stack anywhere in a block entity's or entity's data: a chest's list, a stack
 * inside a shulker box's or bundle's contents, a Zone Planner slot, a gate parameter in a pipe plug. A stack is the
 * {@code {id, count, components}} compound {@code ItemStack.CODEC} writes on every node from 1.21.1 up (a BuildCraft
 * slot wraps it under {@code stack}, which the walk reaches like any other child); a zone map is one whose
 * {@code components} name {@code BCCore.ZONE_MAP_ID}.
 *
 * <p>A plan holds absolute world columns, and a blueprint does not move map locations with the build, the same as a
 * point or area map: a zone map built elsewhere still names the columns it named where it was captured. A blueprint
 * captured before this existed holds bare refs, whose maps stay empty zones in any other world.
 */
public final class ZoneMapPortability {
    /** {@link #holdsMapLocation}'s answers, by price patch; cleared whole when it fills. */
    private static final Map<DataComponentPatch, Boolean> HOLDS_MAP_BY_PATCH = new ConcurrentHashMap<>();
    /** {@link #withZoneMapsBlank(ItemStack)}'s answers, by held stack (empty: nothing to blank); cleared whole when it
     *  fills. */
    private static final Map<HeldKey, ItemStack> BLANKED_BY_HELD = new ConcurrentHashMap<>();
    /** Entries either remembered answer holds before it is cleared. */
    private static final int CACHE_LIMIT = 256;

    /** What {@link #withZoneMapsBlank(ItemStack)} depends on: a stack's item and its component patch (not its count). */
    private record HeldKey(Item item, DataComponentPatch patch) {}

    private ZoneMapPortability() {}

    /** {@link #carryPlans(Tag, ZoneMapSavedData)} against the store this thread may use. */
    public static void carryPlans(@Nullable Tag nbt) {
        carryPlans(nbt, ZoneMapSavedData.forThisThread());
    }

    /**
     * The capture: writes the plan {@code store} holds for each zone map in {@code nbt} into that stack's custom data,
     * keeping its ref. Skips a ref the store does not hold (a map from another world, which names no zone here either)
     * and a stack that already carries a plan (a legacy map, whose own plan is the zone it names). Does nothing with no
     * store (off the server thread).
     */
    public static void carryPlans(@Nullable Tag nbt, @Nullable ZoneMapSavedData store) {
        if (nbt == null || store == null) {
            return;
        }
        forEachZoneMap(nbt, components -> {
            ZoneMapRef ref = readRef(components);
            CompoundTag custom = readCustom(components);
            if (ref == null || (custom != null && custom.contains(ItemMapLocation.TAG_LEGACY_ZONE))) {
                return;
            }
            ZonePlan plan = store.get(ref);
            if (plan == null) {
                return;
            }
            CompoundTag withPlan = custom == null ? new CompoundTag() : custom;
            plan.writeToNBT(withPlan);
            writeCustom(components, withPlan);
        });
    }

    /** {@link #settlePlans(Tag, ZoneMapSavedData)} against the store this thread may use. */
    public static void settlePlans(@Nullable Tag nbt) {
        settlePlans(nbt, ZoneMapSavedData.forThisThread());
    }

    /**
     * The build: hands each plan a zone map in {@code nbt} carries to {@code store} the way a legacy map is migrated
     * ({@link ItemMapLocation#migrateLegacyZone}). A ref the store holds with that plan (a build in the world the
     * blueprint was captured in) is kept; any other ref (another world's, or an id this world has since handed to a
     * different zone) is pointed at an entry holding the plan. The plan stays on the stack until that entry is durable,
     * so a crash before the store is saved cannot leave the built map naming nothing. Stacks carrying no plan are left
     * alone. Does nothing with no store.
     */
    public static void settlePlans(@Nullable Tag nbt, @Nullable ZoneMapSavedData store) {
        if (nbt == null || store == null) {
            return;
        }
        forEachZoneMap(nbt, components -> {
            CompoundTag custom = readCustom(components);
            if (custom == null || !custom.contains(ItemMapLocation.TAG_LEGACY_ZONE)) {
                return;
            }
            ZoneMapRef ref = readRef(components);
            ItemStack probe = new ItemStack(BCCoreItems.MAP_LOCATION.get());
            if (ref != null) {
                probe.set(BCCore.ZONE_MAP_ID.get(), ref);
            }
            probe.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            ItemMapLocation.migrateLegacyZone(probe, store);
            ZoneMapRef settled = probe.get(BCCore.ZONE_MAP_ID.get());
            if (settled != null) {
                ZoneMapRef.CODEC.encodeStart(NbtOps.INSTANCE, settled).result()
                    .ifPresent(tag -> components.put(zoneKey(), tag));
            }
            CustomData after = probe.get(DataComponents.CUSTOM_DATA);
            if (after == null) {
                components.remove(customKey());
            } else {
                writeCustom(components, after.copyTag());
            }
        });
    }

    /**
     * {@code stackNbt} (one saved stack, as a pricing extractor finds it) with every ZONE map location in it, the stack
     * itself and any inside it at any depth, as {@link ItemMapLocation#pricedAsBlank} leaves it: a blank map. The same
     * compound when it holds none, else a copy. A zone map's ref names an entry in one world's store only, and the plan
     * {@link #carryPlans} adds (or {@link #settlePlans} leaves on a map nested in a built container, where nothing ticks
     * to drop it) is nothing a player holds, so a container priced with either could be paid for in no other world;
     * the Zone Planner writes a blank map for free. A held stack is compared the same way
     * ({@link #withZoneMapsBlank(ItemStack)}), so any container holding a zone map, written in whichever world, pays.
     */
    public static CompoundTag withZoneMapsBlank(CompoundTag stackNbt) {
        boolean[] found = {false};
        forEachMapLocation(stackNbt, stack -> found[0] |= isZoneMap(stack));
        if (!found[0]) {
            return stackNbt;
        }
        CompoundTag copy = stackNbt.copy();
        forEachMapLocation(copy, stack -> {
            if (isZoneMap(stack)) {
                blankInPlace(stack);
            }
        });
        return copy;
    }

    /**
     * {@code held} (a stack offered to pay for a priced one) with every ZONE map location in it as a blank map, the
     * held-side twin of {@link #withZoneMapsBlank(CompoundTag)}: {@code held} itself when it holds none, else a copy,
     * of count 1. Encodes the stack, so callers try the exact match, and {@link #holdsMapLocation} on the price, first.
     *
     * <p>The answer is remembered per item and component patch (an immutable value, compared by content), so a stalled
     * build that offers the same held stacks on every extraction attempt encodes each distinct one once, not once per
     * attempt.
     */
    public static ItemStack withZoneMapsBlank(ItemStack held) {
        if (held.isEmpty() || held.getComponentsPatch().isEmpty()) {
            return held;
        }
        HeldKey key = new HeldKey(held.getItem(), held.getComponentsPatch());
        ItemStack blanked = BLANKED_BY_HELD.get(key);
        if (blanked == null) {
            blanked = blankedCopy(held);
            if (BLANKED_BY_HELD.size() >= CACHE_LIMIT) {
                BLANKED_BY_HELD.clear();
            }
            BLANKED_BY_HELD.put(key, blanked);
        }
        // The remembered stack is never handed out: a caller may edit what it gets.
        return blanked.isEmpty() ? held : blanked.copy();
    }

    /** {@code held} with its zone maps blank (count 1), or {@link ItemStack#EMPTY} when that changes nothing or the
     *  result does not parse. */
    private static ItemStack blankedCopy(ItemStack held) {
        if (!(encode(held.copyWithCount(1)) instanceof CompoundTag compound)) {
            return ItemStack.EMPTY;
        }
        CompoundTag blank = withZoneMapsBlank(compound);
        if (blank == compound) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), blank).result().orElse(ItemStack.EMPTY);
    }

    /**
     * Whether {@code stack} holds a map location item inside it, at any depth: where a price made by
     * {@link #withZoneMapsBlank(CompoundTag)} holds the blank map that stood for a zone map. A price holding none
     * cannot be paid for by a stack whose zone maps would turn blank, so the encode of each held stack is skipped. A
     * stack with no component data holds nothing.
     *
     * <p>The answer is remembered per component patch (an immutable value, compared by content), so a price a stalled
     * build re-parses on every extraction attempt is encoded once, not once per attempt.
     */
    public static boolean holdsMapLocation(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return false;
        }
        Boolean known = HOLDS_MAP_BY_PATCH.get(patch);
        if (known != null) {
            return known;
        }
        boolean found = encodedHoldsMapLocation(stack);
        if (HOLDS_MAP_BY_PATCH.size() >= CACHE_LIMIT) {
            HOLDS_MAP_BY_PATCH.clear();
        }
        HOLDS_MAP_BY_PATCH.put(patch, found);
        return found;
    }

    private static boolean encodedHoldsMapLocation(ItemStack stack) {
        if (!(encode(stack.copyWithCount(1)) instanceof CompoundTag compound)
                || !(compound.get("components") instanceof CompoundTag components)) {
            return false;
        }
        boolean[] found = {false};
        forEachMapLocation(components, map -> found[0] = true);
        return found[0];
    }

    @Nullable
    private static Tag encode(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(NBTUtilBC.registryAwareOps(), stack).result().orElse(null);
    }

    /** Whether the saved map location {@code stack} is a ZONE map, by {@link ItemMapLocation#pricedAsBlank}'s test:
     *  it carries a ref, or its custom data names the ZONE type. */
    private static boolean isZoneMap(CompoundTag stack) {
        if (!(stack.get("components") instanceof CompoundTag components)) {
            return false;
        }
        if (components.contains(zoneKey())) {
            return true;
        }
        CompoundTag custom = readCustom(components);
        return custom != null && "ZONE".equals(NBTUtilBC.getString(custom, ItemMapLocation.TAG_MAP_TYPE, ""));
    }

    /** Rewrites the components of the saved map location {@code stack} to those of the blank map it is priced as,
     *  keeping its id, count and any slot key beside them. Left alone when it does not parse. */
    private static void blankInPlace(CompoundTag stack) {
        ItemStack.CODEC.parse(NBTUtilBC.registryAwareOps(), stack).result()
            .map(ItemMapLocation::pricedAsBlank)
            .map(ZoneMapPortability::encode)
            .ifPresent(encoded -> {
                if (!(encoded instanceof CompoundTag blank)) {
                    return;
                }
                if (blank.get("components") instanceof CompoundTag components) {
                    stack.put("components", components);
                } else {
                    stack.remove("components");
                }
            });
    }

    /** Calls {@code action} with every saved map location stack in {@code tag} ({@code tag} itself included), at any
     *  depth; the action may edit that compound. */
    private static void forEachMapLocation(Tag tag, Consumer<CompoundTag> action) {
        walkStacks(tag, stack -> stack.contains("id") && mapLocationId().equals(NBTUtilBC.getString(stack, "id", ""))
                ? stack : null, action);
    }

    /** Calls {@code action} with the {@code components} compound of every saved zone map stack in {@code tag}, at any
     *  depth; the action may edit that compound. */
    private static void forEachZoneMap(Tag tag, Consumer<CompoundTag> action) {
        walkStacks(tag, stack -> stack.contains("id") && stack.get("components") instanceof CompoundTag components
                && components.contains(zoneKey()) ? components : null, action);
    }

    /**
     * The walk both finders share: depth-first through the compounds and lists of {@code tag}, handing {@code action}
     * what {@code target} picks out of each compound (null: not a stack of interest, so its children are walked). A
     * stack of interest is not walked into: a map location holds no stacks, and its plan can be large.
     */
    private static void walkStacks(Tag tag, Function<CompoundTag, @Nullable CompoundTag> target,
            Consumer<CompoundTag> action) {
        if (tag instanceof CompoundTag compound) {
            CompoundTag hit = target.apply(compound);
            if (hit != null) {
                action.accept(hit);
                return;
            }
            for (String key : NBTUtilBC.keysOf(compound)) {
                Tag child = compound.get(key);
                if (child instanceof CompoundTag || child instanceof ListTag) {
                    walkStacks(child, target, action);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                if (child instanceof CompoundTag || child instanceof ListTag) {
                    walkStacks(child, target, action);
                }
            }
        }
    }

    @Nullable
    private static ZoneMapRef readRef(CompoundTag components) {
        Tag tag = components.get(zoneKey());
        return tag == null ? null : ZoneMapRef.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    /** The stack's custom data as a fresh compound, or null when it has none (or none that parses). */
    @Nullable
    private static CompoundTag readCustom(CompoundTag components) {
        Tag tag = components.get(customKey());
        return tag == null ? null : CustomData.CODEC.parse(NbtOps.INSTANCE, tag).result()
            .map(CustomData::copyTag)
            .orElse(null);
    }

    private static void writeCustom(CompoundTag components, CompoundTag custom) {
        CustomData.CODEC.encodeStart(NbtOps.INSTANCE, CustomData.of(custom)).result()
            .ifPresent(tag -> components.put(customKey(), tag));
    }

    private static String mapLocationId() {
        return BCCoreItems.MAP_LOCATION.getId().toString();
    }

    private static String zoneKey() {
        return BCCore.ZONE_MAP_ID.getId().toString();
    }

    private static String customKey() {
        return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.CUSTOM_DATA));
    }
}
