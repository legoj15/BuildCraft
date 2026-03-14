/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.client.model;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.IPluggableStaticBaker;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableModelKey;

import buildcraft.lib.client.model.IModelCache;
import buildcraft.lib.client.model.ModelCache;
import buildcraft.lib.client.model.ModelCacheMultipleSame;

public class PipeModelCachePluggable {
    public static final IModelCache<PluggableKey> cacheCutoutAll, cacheTranslucentAll;
    public static final ModelCache<PluggableModelKey> cacheCutoutSingle, cacheTranslucentSingle;

    static {
        cacheCutoutSingle = new ModelCache<>(PipeModelCachePluggable::generate);
        cacheCutoutAll = new ModelCacheMultipleSame<>(PluggableKey::getKeys, cacheCutoutSingle);

        cacheTranslucentSingle = new ModelCache<>(PipeModelCachePluggable::generate);
        cacheTranslucentAll = new ModelCacheMultipleSame<>(PluggableKey::getKeys, cacheTranslucentSingle);
    }

    private static <K extends PluggableModelKey> List<BakedQuad> generate(K key) {
        if (key == null) {
            return ImmutableList.of();
        }
        // Pluggable baking not yet ported — return empty for now
        return ImmutableList.of();
    }

    public static class PluggableKey {
        private final ImmutableSet<PluggableModelKey> pluggables;
        private final int hash;

        /** NeoForge 1.21.11: RenderType replaces BlockRenderLayer.
         *  For now, we accept a boolean isCutout instead. */
        public PluggableKey(boolean isCutout, IPipeHolder holder) {
            ImmutableSet.Builder<PluggableModelKey> builder = ImmutableSet.builder();
            for (Direction side : Direction.values()) {
                PipePluggable pluggable = holder.getPluggable(side);
                if (pluggable == null) continue;
                // Pluggable model keys not yet wired — skip for now
            }
            this.pluggables = builder.build();
            this.hash = pluggables.hashCode();
        }

        public ImmutableSet<PluggableModelKey> getKeys() {
            return pluggables;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            PluggableKey other = (PluggableKey) obj;
            if (!pluggables.equals(other.pluggables)) return false;
            return true;
        }
    }
}
