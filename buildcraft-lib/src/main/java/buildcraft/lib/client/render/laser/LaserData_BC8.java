/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.client.render.laser;

import java.util.Objects;

import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Holds information about a single laser beam in the world:
 * its visual type, position (start/end), its scale, and rendering flags.
 */
@OnlyIn(Dist.CLIENT)
public class LaserData_BC8 {
    public final LaserType laserType;
    public final Vec3 start, end;
    public final double scale;
    public final boolean enableDiffuse, doubleFace;
    public final int minBlockLight;
    private final int hash;

    public LaserData_BC8(LaserType laserType, Vec3 start, Vec3 end, double scale) {
        this(laserType, start, end, scale, true, false, 0);
    }

    public LaserData_BC8(LaserType laserType, Vec3 start, Vec3 end, double scale,
            boolean enableDiffuse, boolean doubleFace, int minBlockLight) {
        this.laserType = laserType;
        this.start = start;
        this.end = end;
        this.scale = scale;
        this.enableDiffuse = enableDiffuse;
        this.doubleFace = doubleFace;
        this.minBlockLight = minBlockLight;
        hash = Objects.hash(laserType, start, end,
                Double.doubleToLongBits(scale), enableDiffuse, doubleFace, minBlockLight);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        LaserData_BC8 other = (LaserData_BC8) obj;
        if (laserType != other.laserType) return false;
        if (!start.equals(other.start)) return false;
        if (!end.equals(other.end)) return false;
        if (Double.compare(scale, other.scale) != 0) return false;
        if (enableDiffuse != other.enableDiffuse) return false;
        if (doubleFace != other.doubleFace) return false;
        if (minBlockLight != other.minBlockLight) return false;
        return true;
    }

    /**
     * Simplified laser type for 1.21. Holds a color and a line width
     * instead of the 1.12 textured-quad UV system.
     * The full textured system can be restored later.
     */
    public static class LaserType {
        public final float red, green, blue, alpha;
        public final float lineWidth;

        public LaserType(float red, float green, float blue, float alpha, float lineWidth) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.lineWidth = lineWidth;
        }

        /** Convenience constructor with default width */
        public LaserType(float red, float green, float blue, float alpha) {
            this(red, green, blue, alpha, 2.0f);
        }
    }
}
