/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render;

//? if >=26.2 {
import java.util.List;
//?}

//? if >=1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
//?}
//? if >=1.21.10 && <26.2 {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
*///?}

//? if >=1.21.10 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//?}
//? if >=26.2 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?}
//? if >=1.21.10 {
import net.minecraft.client.resources.model.ModelBakery;

import org.jspecify.annotations.Nullable;
//?}

/**
 * The mining crack for block entities whose visible shape their renderer draws (engines) or whose block model only
 * gets its geometry from the block entity (pipes). On the render-state lines (1.21.10+) the level's destroy pass
 * draws the block model with an empty block getter, so neither shows a crack unless the renderer draws one itself.
 * <p>
 * 1.21.1 needs none of this: its destroy pass reads the real level and model data, and the buffer source a block
 * entity renderer is handed already carries the crack for every render type that affects crumbling.
 */
public final class CrumblingOverlayRenderer {
    private CrumblingOverlayRenderer() {}

    //? if >=1.21.10 {
    /**
     * Submits {@code geometry} a second time as the crack overlay for {@code overlay}'s progress. Nothing happens
     * when the block is not being broken. {@code geometry} receives the submit-time pose and a consumer that turns
     * the emitted vertices into crack texture coordinates, so it only has to emit positions and normals.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack,
                              ModelFeatureRenderer.@Nullable CrumblingOverlay overlay,
                              SubmitNodeCollector.CustomGeometryRenderer geometry) {
        if (overlay == null) return;
        int progress = overlay.progress();
        if (progress < 0 || progress >= ModelBakery.DESTROY_TYPES.size()) return;
        PoseStack.Pose cameraPose = overlay.cameraPose();
        //? if >=26.2 {
        // Blended custom geometry lands in a translucent phase that draws after the solid phase holding the
        // block entity's own geometry. With improved transparency on (26.3) that phase is the OIT one, which
        // vanilla only ever feeds the OIT destroy types.
        //? if >=26.3 {
        List<RenderType> destroyTypes = Minecraft.getInstance().gameRenderer.useImprovedTransparency()
                ? ModelBakery.DESTROY_TYPES_OIT : ModelBakery.DESTROY_TYPES;
        //?} else {
        /*List<RenderType> destroyTypes = ModelBakery.DESTROY_TYPES;
        *///?}
        collector.submitCustomGeometry(poseStack, destroyTypes.get(progress),
                (pose, consumer) -> geometry.render(pose, new SheetedDecalTextureGenerator(consumer, cameraPose, 1.0F)));
        //?} else {
        /*// Before 26.2 a blended custom-geometry type goes into the shared buffer, which the level renderer flushes
        // before the cutout sheet, so the block entity's own geometry would paint over the crack. Write into the
        // crumbling buffer source from a feature-time callback instead, as vanilla's ModelFeatureRenderer does: the
        // level renderer flushes it after the cutout sheet. The cutout consumer the callback is handed stays empty.
        collector.submitCustomGeometry(poseStack, BCLibRenderTypes.cutoutBlockSheet(), (pose, unused) -> {
            VertexConsumer crack = Minecraft.getInstance().renderBuffers().crumblingBufferSource()
                    .getBuffer(ModelBakery.DESTROY_TYPES.get(progress));
            geometry.render(pose, new SheetedDecalTextureGenerator(crack, cameraPose, 1.0F));
        });
        *///?}
    }
    //?}
}
