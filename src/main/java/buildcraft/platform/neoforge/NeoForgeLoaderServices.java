/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforgespi.language.IModInfo;

import buildcraft.platform.LoaderServices;

/**
 * {@link LoaderServices} on NeoForge: FML's environment, mod list and paths, and the server lifecycle hooks. It holds no
 * state and reads FML on every call, so nothing here runs before it is asked for.
 */
public final class NeoForgeLoaderServices implements LoaderServices {

    @Override
    public boolean isPhysicalClient() {
        //? if >=1.21.10 {
        return FMLEnvironment.getDist() == Dist.CLIENT;
        //?} else {
        /*return FMLEnvironment.dist == Dist.CLIENT;*/
        //?}
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            ids.add(info.getModId());
        }
        return List.copyOf(ids);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        for (IModInfo info : ModList.get().getMods()) {
            if (modId.equals(info.getModId())) {
                return Optional.of(info.getVersion().toString());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Path> modSourcePaths(String modId) {
        var modFile = ModList.get().getModFileById(modId);
        if (modFile == null) {
            return List.of();
        }
        return List.of(modFile.getFile().getFilePath());
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
//?}
