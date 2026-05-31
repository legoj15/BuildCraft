/* Copyright (c) 2026 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.lib.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersBlocks;
import buildcraft.builders.BCBuildersItems;
import buildcraft.core.BCCore;
import buildcraft.core.BCCoreBlockEntities;
import buildcraft.core.BCCoreBlocks;
import buildcraft.core.BCCoreItems;
import buildcraft.energy.BCEnergyBlockEntities;
import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.BCEnergyFluids;
import buildcraft.energy.BCEnergyItems;
import buildcraft.factory.BCFactoryBlockEntities;
import buildcraft.factory.BCFactoryBlocks;
import buildcraft.factory.BCFactoryItems;
import buildcraft.lib.BCLibItems;
import buildcraft.robotics.BCRoboticsBlockEntities;
import buildcraft.robotics.BCRoboticsBlocks;
import buildcraft.robotics.BCRoboticsItems;
import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.transport.BCTransportBlockEntities;
import buildcraft.transport.BCTransportBlocks;
import buildcraft.transport.BCTransportItems;

/**
 * Backwards-compatibility registry aliases: makes block / item / block-entity / fluid / data-component
 * IDs from older BuildCraft generations resolve to their current {@code buildcraftunofficial} equivalents
 * when an old world or inventory is loaded.
 *
 * <h2>The four ID generations</h2>
 * <ol>
 *   <li><b>1.12.2</b> — eight submod namespaces ({@code buildcraftcore}, {@code buildcrafttransport}, …),
 *       pre-flatten paths, metadata sub-items.</li>
 *   <li><b>1.21.11 / early 26.1</b> — the same eight submod namespaces, post-flatten paths.</li>
 *   <li><b>26.1, post module-merge</b> (commit {@code d42ed434a}) — {@code buildcraftunofficial}, pre-rename paths.</li>
 *   <li><b>current</b> — {@code buildcraftunofficial}, current paths.</li>
 * </ol>
 *
 * <h2>How it works</h2>
 * {@code MappedRegistry.getValue} applies {@code resolve()} on every lookup, so a registered alias
 * transparently remaps an old ID on load — across namespaces, with no event handler. The module merge
 * was a pure namespace flip (paths preserved), so the bulk of the table is <em>generated</em>: every
 * currently-registered path is re-namespaced under its old submod namespace ({@link #flip}). Path renames
 * (chipsets) and 1.12.2 metadata collapses are layered on as explicit exceptions. Because the generative
 * flip reads the live registers, dev-gated entries (null in public builds) are automatically skipped.
 *
 * <h2>1.12.2 caveat</h2>
 * Only inventory <em>item stacks</em> survive a 1.12.2 load — placed pre-flatten blocks are turned to air
 * by vanilla's flattening ({@code ChunkPalettedStorageFix}) before any mod code runs, so the block / BE /
 * fluid aliases below only benefit the post-flatten generations (2–4). The item aliases still recover
 * 1.12.2 inventory contents because item stacks carry string IDs through the vanilla data-fixers.
 */
public final class LegacyAliases {
    private LegacyAliases() {}

    // Historical submod namespaces (shared by generations 1 and 2).
    private static final String CORE = "buildcraftcore";
    private static final String TRANSPORT = "buildcrafttransport";
    private static final String ENERGY = "buildcraftenergy";
    private static final String FACTORY = "buildcraftfactory";
    private static final String BUILDERS = "buildcraftbuilders";
    private static final String SILICON = "buildcraftsilicon";
    private static final String ROBOTICS = "buildcraftrobotics";
    private static final String LIB = "buildcraftlib";

    // Alias-only deferred registers owned by this class. Decoupled from the subsystem registers:
    // an alias-only register with zero entries still flushes its aliases on RegisterEvent.
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BCCore.MODID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BCCore.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BCCore.MODID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, BCCore.MODID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, BCCore.MODID);

    /** One registered alias, retained so the game test can verify every entry resolves to a live target. */
    public record Mapping(Registry<?> registry, Identifier from, Identifier to) {}

    /** All registered aliases, in registration order. Read by {@code LegacyAliasTester}. */
    public static final List<Mapping> MAPPINGS = new ArrayList<>();

    /** Guards against accidental duplicate {@code from} keys (which would throw at load). */
    private static final Set<String> SEEN = new HashSet<>();

    /**
     * Registers every legacy alias. Must run before {@code RegisterEvent} fires (i.e. during mod
     * construction) and after the subsystem registers are populated, so {@link #flip} can read them.
     */
    public static void init(IEventBus modEventBus) {
        // Explicit exceptions first so they win any (theoretical) collision with a generated flip.
        addRenames();
        addMetadataCollapses();
        addDataComponents();
        addNamespaceFlips();

        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        FLUIDS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
    }

    // ─── Generation 2/3 → 4 path renames ────────────────────────────────
    private static void addRenames() {
        // Chipsets: redstone_<material>_chipset → chipset_<material> (commit 67d194430). They shipped
        // under both the silicon submod (gen 2) and the unified namespace (gen 3) — alias both forms.
        String[][] chipsets = {
            { "redstone_red_chipset", "chipset_redstone" },
            { "redstone_iron_chipset", "chipset_iron" },
            { "redstone_gold_chipset", "chipset_gold" },
            { "redstone_quartz_chipset", "chipset_quartz" },
            { "redstone_diamond_chipset", "chipset_diamond" },
        };
        for (String[] c : chipsets) {
            item(id(SILICON, c[0]), bc(c[1]));
            item(bc(c[0]), bc(c[1]));
        }
        // 1.12.2 pre-flatten item renames.
        item(id(CORE, "fragile_fluid_shard"), bc("fragile_fluid_container"));
        item(id(FACTORY, "gel"), bc("gelled_water"));
    }

    // ─── 1.12.2 metadata families → primary variant ─────────────────────
    // Pre-flatten these were one item + a Damage value; aliases are Damage-blind, so the whole family
    // collapses to its base variant. The stack survives; its exact variant/colour may differ.
    private static void addMetadataCollapses() {
        item(id(SILICON, "redstone_chipset"), bc("chipset_redstone"));   // Damage 0-7
        item(id(TRANSPORT, "wire"), bc("wire_white"));                   // Damage 0-15 (DyeColor)
        item(id(CORE, "engine"), bc("engine_redstone"));
        item(id(CORE, "spring"), bc("spring_water"));
        item(id(CORE, "decorated"), bc("decorated_laser"));
        item(id(BUILDERS, "snapshot"), bc("blueprint_clean"));
        item(id(BUILDERS, "schematic_single"), bc("schematic_single_clean"));
    }

    // ─── Data components (gen 2 namespace → unified) ────────────────────
    // Keeps painted-pipe colour, fragile-fluid contents and paintbrush state on post-flatten item stacks.
    private static void addDataComponents() {
        component(id(CORE, "fluid_content"), bc("fluid_content"));
        component(id(CORE, "brush_color"), bc("brush_color"));
        component(id(CORE, "brush_uses"), bc("brush_uses"));
        component(id(TRANSPORT, "pipe_colour"), bc("pipe_colour"));
    }

    // ─── Namespace flip (gens 2/3 → 4) ──────────────────────────────────
    // Every currently-registered path, re-namespaced under its old submod namespace.
    private static void addNamespaceFlips() {
        // Items.
        flip(BCCoreItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", CORE);
        flip(BCLibItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", LIB);
        flip(BCTransportItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", TRANSPORT);
        flip(BCEnergyItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", ENERGY, CORE); // engines briefly lived under core
        flip(BCEnergyFluids.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", ENERGY);      // fluid buckets
        flip(BCFactoryItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", FACTORY);
        flip(BCBuildersItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", BUILDERS);
        flip(BCSiliconItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", SILICON);
        flip(BCRoboticsItems.ITEMS, ITEMS, BuiltInRegistries.ITEM, "item", ROBOTICS);

        // Blocks — help placed machines in post-flatten worlds (useless for 1.12.2, which loads as air).
        flip(BCCoreBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", CORE);
        flip(BCTransportBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", TRANSPORT);
        flip(BCEnergyBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", ENERGY, CORE);
        flip(BCEnergyFluids.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", ENERGY); // liquid blocks
        flip(BCFactoryBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", FACTORY);
        flip(BCBuildersBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", BUILDERS);
        flip(BCSiliconBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", SILICON);
        flip(BCRoboticsBlocks.BLOCKS, BLOCKS, BuiltInRegistries.BLOCK, "block", ROBOTICS);

        // Block entities — needed for placed machines (e.g. pipe_holder) to keep their tile data.
        flip(BCCoreBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", CORE);
        flip(BCTransportBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", TRANSPORT);
        flip(BCEnergyBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", ENERGY, CORE);
        flip(BCFactoryBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", FACTORY);
        flip(BCBuildersBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", BUILDERS);
        flip(BCSiliconBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", SILICON);
        flip(BCRoboticsBlockEntities.BLOCK_ENTITIES, BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE, "be", ROBOTICS);

        // Fluids — keep tank contents (FluidStack references the source fluid) in post-flatten worlds.
        flip(BCEnergyFluids.FLUIDS, FLUIDS, BuiltInRegistries.FLUID, "fluid", ENERGY);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    /** Re-namespaces every entry of {@code source} under each of {@code oldNamespaces}, aliasing it to its current id. */
    private static void flip(DeferredRegister<?> source, DeferredRegister<?> aliasRegister, Registry<?> registry,
            String key, String... oldNamespaces) {
        for (var holder : source.getEntries()) {
            Identifier to = holder.getId();
            for (String ns : oldNamespaces) {
                add(aliasRegister, registry, key, id(ns, to.getPath()), to);
            }
        }
    }

    private static void item(Identifier from, Identifier to) {
        add(ITEMS, BuiltInRegistries.ITEM, "item", from, to);
    }

    private static void component(Identifier from, Identifier to) {
        add(DATA_COMPONENTS, BuiltInRegistries.DATA_COMPONENT_TYPE, "component", from, to);
    }

    private static void add(DeferredRegister<?> aliasRegister, Registry<?> registry, String key,
            Identifier from, Identifier to) {
        if (from.equals(to) || !SEEN.add(key + "|" + from)) {
            return;
        }
        aliasRegister.addAlias(from, to);
        MAPPINGS.add(new Mapping(registry, from, to));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static Identifier bc(String path) {
        return Identifier.fromNamespaceAndPath(BCCore.MODID, path);
    }
}
