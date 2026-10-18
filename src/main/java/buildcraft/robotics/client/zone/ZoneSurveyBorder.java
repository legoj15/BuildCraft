/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.zone;

/**
 * Which terrain cells outline the Zone Planner's survey square. Pure (no Minecraft types) so the geometry is
 * unit-testable; {@code ZoneMapGeometry} draws what it lists.
 *
 * <p>The terrain is drawn as a world-aligned grid of {@code band x band} cells (a multiple of {@code band} in both
 * coordinates; a cell's height is that of its lowest-corner sample). The square's edges fall wherever the planner
 * stands, so they are generally not on that grid. The outline is therefore made of the grid cells that contain the
 * square's outermost columns, whole: each lies on the terrain cell it is drawn over, at that cell's height, rather
 * than straddling two terrain cells and floating above the lower. A cell at an edge may reach up to
 * {@code band - 1} blocks past the square, which at the survey zoom (8 blocks to the pixel) is under one pixel.
 */
public final class ZoneSurveyBorder {
    private ZoneSurveyBorder() {}

    /** Receives one border cell: the world-block coordinates of its lowest corner (a multiple of the band). */
    @FunctionalInterface
    public interface CellVisitor {
        void visit(int cellX, int cellZ);
    }

    /**
     * Visits, once each, the grid cells of size {@code band} outlining the square
     * {@code [minX, maxX) x [minZ, maxZ)} (world blocks) that overlap the view box (world blocks; may be fractional).
     * The two rows of cells along the north and south edges run the square's whole width, the two columns along
     * the west and east edges only the rows between them, so the corners are listed once.
     */
    public static void forEachCell(int minX, int minZ, int maxX, int maxZ,
                                   double viewMinX, double viewMinZ, double viewMaxX, double viewMaxZ,
                                   int band, CellVisitor visitor) {
        // The part of the square in view, in blocks.
        int fromX = Math.max(minX, (int) Math.floor(viewMinX));
        int toX = Math.min(maxX, (int) Math.ceil(viewMaxX));
        int fromZ = Math.max(minZ, (int) Math.floor(viewMinZ));
        int toZ = Math.min(maxZ, (int) Math.ceil(viewMaxZ));
        if (fromX >= toX || fromZ >= toZ) {
            return; // the square is wholly out of view
        }
        // The edge rows/columns: the cells holding the outermost block of each edge.
        int northRow = cellOrigin(minZ, band);
        int southRow = cellOrigin(maxZ - 1, band);
        int westCol = cellOrigin(minX, band);
        int eastCol = cellOrigin(maxX - 1, band);
        // The cells holding the visible blocks of the square, along each axis.
        int firstX = cellOrigin(fromX, band);
        int lastX = cellOrigin(toX - 1, band);
        int firstZ = cellOrigin(fromZ, band);
        int lastZ = cellOrigin(toZ - 1, band);

        for (int row = northRow; ; row = southRow) {
            if (row + band > viewMinZ && row < viewMaxZ) {
                for (int x = firstX; x <= lastX; x += band) {
                    visitor.visit(x, row);
                }
            }
            if (row == southRow) {
                break;
            }
        }
        // West and east: only between the north and south rows, which already hold the corners.
        int fromRow = Math.max(firstZ, northRow + band);
        int toRow = Math.min(lastZ, southRow - band);
        for (int col = westCol; ; col = eastCol) {
            if (col + band > viewMinX && col < viewMaxX) {
                for (int z = fromRow; z <= toRow; z += band) {
                    visitor.visit(col, z);
                }
            }
            if (col == eastCol) {
                break;
            }
        }
    }

    /** The lowest corner of the grid cell holding block {@code coord}. */
    private static int cellOrigin(int coord, int band) {
        return Math.floorDiv(coord, band) * band;
    }
}
