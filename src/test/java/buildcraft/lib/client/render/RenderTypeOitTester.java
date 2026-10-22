/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Minecraft 26.3's improved-transparency (OIT) mode draws every blending render type through the type's OIT
 * pipeline set, and throws "does not have OIT pipelines set up" for a blending type that has none, which crashes the
 * client on the first frame such geometry is in view (machine LEDs, the advanced debugger). Every blending render type
 * BuildCraft builds itself must carry one. The types are found by reflection over {@link BCLibRenderTypes}' static
 * fields (render types, and memoised per-texture factories applied to the blocks atlas), so a new blending type there
 * is checked without editing this test. Breaks when a blending type is added or rebuilt without
 * {@code setOitPipelines}. Reflection only, so the test compiles on every line; it is skipped on lines without OIT.
 */
public class RenderTypeOitTester {

    @Test
    public void everyBlendingRenderTypeHasOitPipelines() throws ReflectiveOperationException {
        try {
            Class.forName("net.minecraft.client.renderer.oit.OitPipelineSet");
        } catch (ClassNotFoundException e) {
            Assumptions.abort("no improved-transparency (OIT) path on this Minecraft line");
        }
        Map<String, Object> types = new LinkedHashMap<>();
        for (Field f : BCLibRenderTypes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Object value = f.get(null);
            if (value instanceof Function<?, ?> factory) {
                @SuppressWarnings("unchecked")
                Function<Object, Object> perTexture = (Function<Object, Object>) factory;
                value = perTexture.apply(BCLibRenderTypes.BLOCKS_ATLAS_ID);
            }
            if (value != null && isRenderType(value.getClass())) {
                types.put(f.getName(), value);
            }
        }
        Assertions.assertTrue(types.keySet().containsAll(List.of("LED", "DEBUG_SOLID", "ZONE_OVERLAY",
            "ENTITY_TRANSLUCENT_CULL")), "reflection should find BuildCraft's own render types, found " + types.keySet());

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Object> entry : types.entrySet()) {
            Object type = entry.getValue();
            if (!(Boolean) type.getClass().getMethod("hasBlending").invoke(type)) {
                continue;
            }
            Object state = field(type.getClass(), "state").get(type);
            Object oit = field(state.getClass(), "oitPipelineSet").get(state);
            if (oit == null) {
                missing.add(entry.getKey());
            }
        }
        Assertions.assertEquals(List.of(), missing, "blending render types without an OIT pipeline set");
    }

    private static boolean isRenderType(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getSimpleName().equals("RenderType")) {
                return true;
            }
        }
        return false;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
}
