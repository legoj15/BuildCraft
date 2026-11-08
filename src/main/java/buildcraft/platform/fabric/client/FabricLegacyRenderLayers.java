/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <26.1 {
/*import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

//? if >=1.21.10 {
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
//?} else {
/^import net.minecraft.client.renderer.rendertype.RenderType;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
^///?}
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/^*
 * Puts BuildCraft's blocks on the chunk layer their models ask for, before 26.1. The models name it with NeoForge's
 * {@code render_type} key, which only NeoForge reads; vanilla before 26.1 keeps a per-block layer table instead
 * (from 26.1 the layer comes from the sprites' own transparency, so nothing is needed there). Without this every
 * cutout block (Tank, Distiller, Builder, Quarry frame) renders solid, its clear pixels filled in.
 *
 * <p>The table is read from the same JSON NeoForge reads, so a new cutout model needs no second edit here: each
 * BuildCraft block's blockstate file, every model it names, and that model's own parents in BuildCraft's namespace.
 * A block takes the first {@code render_type} found other than solid.
 ^/
public final class FabricLegacyRenderLayers {
    private static final String NS = "buildcraftunofficial";

    private FabricLegacyRenderLayers() {}

    public static void register() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(NS);
        if (mod.isEmpty()) {
            return;
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(block);
            if (!NS.equals(key.getNamespace())) {
                continue;
            }
            JsonElement blockstate = read(mod.get(), "blockstates/" + key.getPath() + ".json");
            if (blockstate == null) {
                continue;
            }
            Set<String> models = new HashSet<>();
            collectModels(blockstate, models);
            for (String model : models) {
                //? if >=1.21.10 {
                ChunkSectionLayer layer = layerOf(mod.get(), model, new HashSet<>());
                if (layer != null) {
                    BlockRenderLayerMap.putBlock(block, layer);
                    break;
                }
                //?} else {
                /^RenderType layer = layerOf(mod.get(), model, new HashSet<>());
                if (layer != null) {
                    BlockRenderLayerMap.INSTANCE.putBlock(block, layer);
                    break;
                }
                ^///?}
            }
        }
    }

    // Every "model" string anywhere in a blockstate: variants (single or weighted lists) and multipart "apply"s.
    private static void collectModels(JsonElement json, Set<String> out) {
        if (json.isJsonObject()) {
            for (var e : json.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("model") && e.getValue().isJsonPrimitive()) {
                    out.add(e.getValue().getAsString());
                } else {
                    collectModels(e.getValue(), out);
                }
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> collectModels(e, out));
        }
    }

    // The model's non-solid render_type, following its parents while they stay in BuildCraft's namespace.
    //? if >=1.21.10 {
    private static ChunkSectionLayer layerOf(ModContainer mod, String model, Set<String> seen) {
    //?} else {
    /^private static RenderType layerOf(ModContainer mod, String model, Set<String> seen) {
    ^///?}
        int colon = model.indexOf(':');
        String ns = colon < 0 ? "minecraft" : model.substring(0, colon);
        String path = model.substring(colon + 1);
        if (!NS.equals(ns) || !seen.add(path)) {
            return null;
        }
        JsonElement json = read(mod, "models/" + path + ".json");
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        JsonObject obj = json.getAsJsonObject();
        if (obj.has("render_type")) {
            String type = obj.get("render_type").getAsString();
            //? if >=1.21.10 {
            return switch (type.substring(type.indexOf(':') + 1)) {
                // 1.21.11 has no separate mipped cutout layer; no BuildCraft model asks for one.
                case "cutout", "cutout_mipped" -> ChunkSectionLayer.CUTOUT;
                case "translucent" -> ChunkSectionLayer.TRANSLUCENT;
                default -> null;
            };
            //?} else {
            /^// 1.21.1 still has its mipped cutout layer, as NeoForge's render_type names it.
            return switch (type.substring(type.indexOf(':') + 1)) {
                case "cutout" -> RenderType.cutout();
                case "cutout_mipped" -> RenderType.cutoutMipped();
                case "translucent" -> RenderType.translucent();
                default -> null;
            };
            ^///?}
        }
        return obj.has("parent") ? layerOf(mod, obj.get("parent").getAsString(), seen) : null;
    }

    private static JsonElement read(ModContainer mod, String asset) {
        Optional<Path> file = mod.findPath("assets/" + NS + "/" + asset);
        if (file.isEmpty() || !Files.isRegularFile(file.get())) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file.get())) {
            return JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
*///?}
