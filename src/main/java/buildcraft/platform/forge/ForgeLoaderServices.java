/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.server.MinecraftServer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.server.ServerLifecycleHooks;

import buildcraft.platform.LoaderServices;

/^*
 * {@link LoaderServices} on MinecraftForge (not NeoForge): FML's environment, mod list and paths, and Forge's server
 * lifecycle hooks. It holds no state and reads FML on every call, so nothing here runs before it is asked for.
 *
 * <p><b>Static API.</b> Forge 26.3's {@link ModList} is all static (NeoForge's is an instance behind {@code ModList.get()},
 * and so is Forge 1.21.1's), and {@link FMLEnvironment#dist} is a field, as on NeoForge before 1.21.10.
 *
 * <p><b>Installed, not constructed.</b> {@link ModList#isLoaded} reads an index that Forge fills only once the mods have
 * been constructed, so before then it is a null dereference. {@link #isModLoaded} asks the discovery-time file index
 * ({@link ModList#getModFileById}) instead, which Forge builds with the class itself, so the answer is the same at any
 * point of loading: the mod is installed. {@link #modIds()} and {@link #modVersion} read the same discovery-time list.
 *
 * <p><b>Current server.</b> {@link ServerLifecycleHooks} is Forge's own server tracker, but it is annotated
 * {@code @ApiStatus.Internal}: Forge has no public accessor for the running server. The fallback if that ever bites is
 * the Fabric route (remember the server from Forge's starting event and forget it on the stopped one).
 *^/
public final class ForgeLoaderServices implements LoaderServices {

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    private static List<IModInfo> mods() {
        //? if >=1.21.10 {
        return ModList.getMods();
        //?} else {
        /^return ModList.get().getMods();
        ^///?}
    }

    @Override
    public boolean isModLoaded(String modId) {
        //? if >=1.21.10 {
        return ModList.getModFileById(modId) != null;
        //?} else {
        /^return ModList.get().getModFileById(modId) != null;
        ^///?}
    }

    @Override
    public List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (IModInfo info : mods()) {
            ids.add(info.getModId());
        }
        return List.copyOf(ids);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        for (IModInfo info : mods()) {
            if (modId.equals(info.getModId())) {
                return Optional.of(info.getVersion().toString());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Path> modSourcePaths(String modId) {
        //? if >=1.21.10 {
        IModFileInfo modFile = ModList.getModFileById(modId);
        //?} else {
        /^IModFileInfo modFile = ModList.get().getModFileById(modId);
        ^///?}
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
*///?}
