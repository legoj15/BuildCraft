package buildcraft.api.transport.pipe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public interface IPipeFlowRenderer<F extends PipeFlow> {
    /** @param flow The flow to render
     * @param x
     * @param y
     * @param z
     * @param pose The PoseStack.Pose containing the block-position transform. Quad vertices must be multiplied by this
     *             to appear at the correct world position.
     * @param bufferBuilder The (optional) vertex buffer that you can render into. */
    default void render(F flow, double x, double y, double z, float partialTicks, VertexConsumer bufferBuilder, PoseStack.Pose pose) {
        render(flow, x, y, z, partialTicks, bufferBuilder);
    }

    /** @deprecated Use {@link #render(PipeFlow, double, double, double, float, VertexConsumer, PoseStack.Pose)} */
    @Deprecated
    default void render(F flow, double x, double y, double z, float partialTicks, VertexConsumer bufferBuilder) {}
}
