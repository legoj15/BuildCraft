/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.client;

//? if fabric && <1.21.10 {
/*import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;

/^*
 * NeoForge's {@code ModelEvent.ModifyBakingResult} on Fabric 1.21.1: the edits {@code ClientRegistrationSinks} queues
 * ({@code onModelsBaking}, {@code editBakedModels}, {@code pipeBlockModel}), run in the order they were added over
 * the whole baked map each time the models bake, before they go live. {@code ModelManagerMixin} runs them where
 * NeoForge posts the event, once the bakery has baked every top-level model.
 *^/
public final class FabricBakedModelEdits {
    private static final List<Consumer<Map<ModelResourceLocation, BakedModel>>> EDITS = new CopyOnWriteArrayList<>();

    private FabricBakedModelEdits() {}

    static void add(Consumer<Map<ModelResourceLocation, BakedModel>> edit) {
        EDITS.add(edit);
    }

    /^* Runs every edit over {@code models}, the bakery's mutable top-level map. *^/
    public static void run(Map<ModelResourceLocation, BakedModel> models) {
        for (Consumer<Map<ModelResourceLocation, BakedModel>> edit : EDITS) {
            edit.accept(models);
        }
    }
}
*///?}
