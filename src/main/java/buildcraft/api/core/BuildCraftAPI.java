/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.core;

import java.util.HashMap;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;

public final class BuildCraftAPI {
    public static IFakePlayerProvider fakePlayerProvider;

    public static final Set<Block> softBlocks = Sets.newHashSet();
    public static final HashMap<String, IWorldProperty> worldProperties = Maps.newHashMap();

    /** Deactivate constructor */
    private BuildCraftAPI() {}

    public static String getVersion() {
        ModContainer container = null;
        if (container != null) {
            return "";
        }
        return "UNKNOWN VERSION";
    }

    public static IWorldProperty getWorldProperty(String name) {
        return worldProperties.get(name);
    }

    public static void registerWorldProperty(String name, IWorldProperty property) {
        if (worldProperties.containsKey(name)) {
            BCLog.logger.warn("The WorldProperty key '" + name + "' is being overridden with " + property.getClass().getSimpleName() + "!");
        }
        worldProperties.put(name, property);
    }

    public static boolean isSoftBlock(Level world, BlockPos pos) {
        return worldProperties.get("soft").get(world, pos);
    }

    /**
     * Parses a "namespace:path" string into an {@link Identifier}. If no namespace is present the
     * call fails fast — callers must supply a fully-qualified id rather than relying on an ambient
     * "active mod" context (that context doesn't exist in the 1.21 NeoForge registry lifecycle).
     * <p>
     * Historical note: the 1.12.2 version of this method returned a raw {@code String}. The factory
     * registries stored that string and later compared it against an {@code Identifier} passed in
     * at deserialization time — {@code String.equals(Identifier)} is always false, so no factory
     * could ever be looked up by name. That silent mismatch caused every saved blueprint to fail
     * to load ("Unknown schematic type ..."). Returning {@code Identifier} here aligns the stored
     * key with the lookup key.
     */
    public static Identifier nameToResourceLocation(String name) {
        if (name.indexOf(':') > 0) return Identifier.parse(name);
        throw new IllegalStateException("Illegal name " + name + ". Provide domain id (namespace:path) to register it correctly.");
    }
}


