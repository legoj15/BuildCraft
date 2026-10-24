/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a written ZONE map location carries in place of its zone: the zone's {@code id} in a {@link ZoneMapSavedData},
 * and that store's random {@code store} token. Ids are a per-world counter, so the token is what keeps a map that
 * leaves its world (a structure captured with the chest it sits in, built in another world) from reading whichever
 * zone holds the same number there: a map whose token is not the store's names no zone in it, which a robot reads as
 * an empty zone and idles on, instead of working another station's area. A blueprint carries the plan beside the ref
 * ({@link buildcraft.core.item.ZoneMapPortability}), so a map it builds names the same zone in any world.
 */
public record ZoneMapRef(long store, int id) {
    public static final Codec<ZoneMapRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("store").forGetter(ZoneMapRef::store),
        Codec.INT.fieldOf("id").forGetter(ZoneMapRef::id)
    ).apply(instance, ZoneMapRef::new));

    public static final StreamCodec<ByteBuf, ZoneMapRef> STREAM_CODEC = StreamCodec.composite(
        // VAR_LONG: the fixed-width LONG codec does not exist on 1.21.1.
        ByteBufCodecs.VAR_LONG, ZoneMapRef::store,
        ByteBufCodecs.VAR_INT, ZoneMapRef::id,
        ZoneMapRef::new
    );
}
