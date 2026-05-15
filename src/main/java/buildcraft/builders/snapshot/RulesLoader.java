/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import buildcraft.lib.misc.JsonUtil;

public class RulesLoader {
    private static final Gson GSON = JsonUtil.registerNbtSerializersDeserializers(new GsonBuilder())
        .registerTypeAdapter(
            BlockPos.class,
            (JsonDeserializer<BlockPos>) (json, typeOfT, context) ->
                new BlockPos(
                    json.getAsJsonArray().get(0).getAsInt(),
                    json.getAsJsonArray().get(1).getAsInt(),
                    json.getAsJsonArray().get(2).getAsInt()
                )
        )
        .registerTypeAdapter(RequiredExtractor.class, RequiredExtractor.DESERIALIZER)
        .registerTypeAdapter(EnumNbtCompareOperation.class, EnumNbtCompareOperation.DESERIALIZER)
        .registerTypeAdapter(NbtPath.class, NbtPath.DESERIALIZER)
        .registerTypeAdapterFactory(JsonSelector.TYPE_ADAPTER_FACTORY)
        .registerTypeAdapterFactory(NbtRef.TYPE_ADAPTER_FACTORY)
        .create();

    private static final List<JsonRule> RULES = new ArrayList<>();
    @SuppressWarnings("WeakerAccess")
    public static final Set<String> READ_DOMAINS = new HashSet<>();
    @SuppressWarnings("ConstantConditions")
    private static final LoadingCache<Pair<BlockState, CompoundTag>, Set<JsonRule>>
        BLOCK_RULES_CACHE = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build(CacheLoader.from(pair -> getBlockRulesInternal(pair.getLeft(), pair.getRight())));

    public static void loadAll() {
        RULES.clear();
        READ_DOMAINS.clear();
        for (IModInfo modInfo : ModList.get().getMods()) {
            String domain = modInfo.getModId();
            if (!READ_DOMAINS.contains(domain)) {
                String base = "assets/" + domain + "/compat/buildcraft/builders/";
                InputStream inputStream = RulesLoader.class.getClassLoader().getResourceAsStream(
                    base + "index.json"
                );
                if (inputStream != null) {
                    GSON.<List<String>>fromJson(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                        new TypeToken<List<String>>() {
                        }.getType()
                    ).stream()
                        .map(name -> base + name + ".json")
                        .map(name -> {
                            InputStream resourceAsStream = RulesLoader.class
                                .getClassLoader()
                                .getResourceAsStream(name);
                            if (resourceAsStream == null) {
                                throw new RuntimeException(new IOException("Can't read " + name));
                            }
                            return resourceAsStream;
                        })
                        .flatMap(localInputStream ->
                            GSON.<List<JsonRule>>fromJson(
                                new InputStreamReader(localInputStream),
                                new TypeToken<List<JsonRule>>() {
                                }.getType()
                            ).stream()
                        )
                        .forEach(RULES::add);
                    READ_DOMAINS.add(domain);
                }
            }
        }
        READ_DOMAINS.add("minecraft");
        // Note: BC blocks aren't in the default-schematic whitelist. They have
        // dedicated schematic factories registered via SchematicBlockFactoryRegistry,
        // so they shouldn't fall through to SchematicBlockDefault. In 1.12.2 a planned
        // BCLib.DEV flag was meant to gate this — letting devs force-fallback to the
        // default factory for testing — but the flag was never implemented. If you
        // need that toggle later, add a single `READ_DOMAINS.add("buildcraftunofficial")`
        // here under the dev-mode condition.
    }

    private static Set<JsonRule> getBlockRulesInternal(BlockState blockState, CompoundTag tileNbt) {
        return RulesLoader.RULES.stream()
            .filter(rule -> rule.selectors != null)
            .filter(rule ->
                rule.selectors.stream()
                    .anyMatch(selector ->
                        selector.matches(
                            base -> {
                                boolean complex = base.contains("[");
                                Identifier blockId = Identifier.parse(
                                    complex
                                        ? base.substring(0, base.indexOf("["))
                                        : base
                                );
                                Block block = BuiltInRegistries.BLOCK.getValue(blockId);
                                return block == blockState.getBlock() &&
                                    (!complex ||
                                        Arrays.stream(
                                            base.substring(
                                                base.indexOf("[") + 1,
                                                base.indexOf("]")
                                            )
                                                .split(", ")
                                        )
                                            .map(nameValue -> nameValue.split("="))
                                            .allMatch(nameValue ->
                                                blockState.getProperties().stream()
                                                    .filter(property -> property.getName().equals(nameValue[0]))
                                                    .findFirst()
                                                    .map(property ->
                                                        getPropertyStringValue(
                                                            blockState,
                                                            property
                                                        )
                                                    )
                                                    .map(nameValue[1]::equals)
                                                    .orElse(false)
                                            )
                                    );
                            },
                            tileNbt == null ? new CompoundTag() : tileNbt
                        )
                    )
            )
            .collect(Collectors.toCollection(HashSet::new));
    }

    @SuppressWarnings("WeakerAccess")
    public static Set<JsonRule> getRules(BlockState blockState, CompoundTag tileNbt) {
        return BLOCK_RULES_CACHE.getUnchecked(Pair.of(blockState, tileNbt));
    }

    @SuppressWarnings("WeakerAccess")
    public static Set<JsonRule> getRules(Identifier entityId, CompoundTag tileNbt) {
        return RulesLoader.RULES.stream()
            .filter(rule -> rule.selectors != null)
            .filter(rule ->
                rule.selectors.stream()
                    .anyMatch(selector -> selector.matches(entityId.toString()::equals, tileNbt))
            )
            .collect(Collectors.toCollection(HashSet::new));
    }

    /** Get the string value of a property from a block state. */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyStringValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
