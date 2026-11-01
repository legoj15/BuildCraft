/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.model.plug;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}
import net.minecraft.core.Direction;

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;
import buildcraft.api.transport.pluggable.PluggableModelKey;

import buildcraft.lib.client.model.MutableQuad;

/** An {@link IPluggableStaticBaker} that rotates a given model to the correct side, and returns the quads. */
public class PlugBakerSimple<K extends PluggableModelKey> implements IPluggableStaticBaker<K> {

    private final IQuadProvider provider;
    /** Chunk sections compile on several threads at once, so a re-bake builds a whole new map and publishes it with
     * the array it came from in one write; a reader never sees a half-filled map. */
    private volatile Baked baked;

    public PlugBakerSimple(IQuadProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<BakedQuad> bake(K key) {
        MutableQuad[] quads = provider.getCutoutQuads();
        Baked current = baked;
        if (current == null || current.source != quads) {
            Map<Direction, List<BakedQuad>> bySide = new EnumMap<>(Direction.class);
            MutableQuad copy = new MutableQuad();
            for (Direction to : Direction.values()) {
                List<BakedQuad> list = new ArrayList<>();
                for (MutableQuad q : quads) {
                    copy.copyFrom(q);
                    copy.rotate(Direction.WEST, to, 0.5f, 0.5f, 0.5f);
                    copy.multShade();
                    list.add(copy.toBakedBlock());
                }
                bySide.put(to, list);
            }
            current = new Baked(quads, bySide);
            baked = current;
        }
        return current.bySide.get(key.side);
    }

    private record Baked(MutableQuad[] source, Map<Direction, List<BakedQuad>> bySide) {}

    public interface IQuadProvider {
        MutableQuad[] getCutoutQuads();
    }
}
