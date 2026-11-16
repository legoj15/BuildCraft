/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

import buildcraft.api.capability.BCCapability;
import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.platform.transfer.StorageTokens;

/^*
 * The Forge {@link Capability} of each non-storage {@link BCCapability} token (the MJ capabilities), the Forge twin of
 * {@code NeoForgeTransfer.blockCap}.
 *
 * <p>A Forge capability is keyed by the name of a type fixed at compile time: {@code CapabilityManager.get} takes a
 * {@code CapabilityToken} subclass whose generic argument FML's transformer reads into the key, so a runtime
 * {@code Class<T>} (all a {@link BCCapability} carries) cannot make one, and NeoForge's "create the capability of this
 * id and type" has no Forge form. The three MJ tokens are therefore spelled out as real tokens over their interfaces,
 * which makes them the capabilities another Forge mod gets by asking for {@code IMjReceiver}, as on NeoForge, where
 * another mod creating the same id shares BuildCraft's. Any other token (BuildCraft has none registered with a block,
 * the pipe API's are pipe-internal) gets a child of Forge's {@code Object} capability named by the token's id: it works
 * for BuildCraft's own registrations and queries, but no other mod can reach it. The MJ interfaces carry no
 * {@code @AutoRegisterCapability}: Forge only uses that mark to flip {@code Capability.isRegistered}, which nothing
 * gates on, and the annotation would put a Forge type on BuildCraft's API.
 ^/
final class ForgeCapabilityTokens {
    private ForgeCapabilityTokens() {}

    private static final Capability<IMjConnector> MJ_CONNECTOR = CapabilityManager.get(new CapabilityToken<>() {});
    private static final Capability<IMjReceiver> MJ_RECEIVER = CapabilityManager.get(new CapabilityToken<>() {});
    private static final Capability<IMjRedstoneReceiver> MJ_REDSTONE_RECEIVER =
        CapabilityManager.get(new CapabilityToken<>() {});

    // The root the named children of the fallback hang off.
    private static final CapabilityToken<Object> OBJECT = new CapabilityToken<>() {};

    // The ids MjAPI gives its three tokens (BCCapability.of interns by id, so the ids are the identity).
    private static final Map<Identifier, Capability<?>> KNOWN = Map.of(
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_connector"), MJ_CONNECTOR,
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_receiver"), MJ_RECEIVER,
        Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_redstone_receiver"), MJ_REDSTONE_RECEIVER);

    private static final Map<BCCapability<?>, Capability<?>> RESOLVED = new ConcurrentHashMap<>();

    /^* The Forge capability of a token's id and type (never a storage token). ^/
    @SuppressWarnings("unchecked") // the id fixes the type: BCCapability.of refuses one id for two types
    static <S> Capability<S> of(BCCapability<S> token) {
        if (StorageTokens.isStorage(token)) {
            throw new IllegalArgumentException("A storage token has no direct capability: " + token);
        }
        return (Capability<S>) RESOLVED.computeIfAbsent(token, key -> {
            Capability<?> known = KNOWN.get(key.id());
            return known != null ? known : CapabilityManager.get(OBJECT, key.id());
        });
    }
}
*///?}
