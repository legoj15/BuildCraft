package buildcraft.core.lib.utils;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

public class IdentifiableAABB<T> extends AxisAlignedBB {
    public final T ResourceLocation;

    public IdentifiableAABB(AxisAlignedBB bb, T ResourceLocation) {
        this(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, ResourceLocation);
    }

    public IdentifiableAABB(Vec3d min, Vec3d max, T ResourceLocation) {
        this(min.xCoord, min.yCoord, min.zCoord, max.xCoord, max.yCoord, max.zCoord, ResourceLocation);
    }

    public IdentifiableAABB(double x1, double y1, double z1, double x2, double y2, double z2, T ResourceLocation) {
        super(x1, y1, z1, x2, y2, z2);
        this.ResourceLocation = ResourceLocation;
    }
}
