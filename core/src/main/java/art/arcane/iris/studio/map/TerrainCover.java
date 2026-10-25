/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the 3D noise view draws for one camera, and which tiles it still needs. Ground in sight wants detail tiles,
 * whose columns are at most a pixel wide, and backdrop tiles a few levels coarser, which are cheap enough to cover the
 * whole view and more right away. Preview tiles one level coarser than detail come between them: a quarter of the
 * columns, so the whole view looks nearly finished long before the detail is in. Until it is, coarser tiles stand in
 * for it, with holes where finer tiles are ready; detail left over from before zooming out stands in too, as does
 * ground from before the noise changed.
 */
final class TerrainCover {
    private static final double MOST_COLUMN_PIXELS = 1D;
    // A little above one, as tiles in sight stick out past the view's edges; beyond it, columns only add faces no one can
    // tell apart, and drawing them costs the frame its time.
    private static final double MOST_COLUMNS_PER_PIXEL = 1.25D;
    // With leftovers one level finer than detail, a backdrop's hole is cut for tiles up to TerrainHole.MOST_LEVELS_FINER
    // levels finer than it, and no further.
    private static final int BACKDROP_LEVELS_ABOVE_DETAIL = 3;
    private static final int PREVIEW_LEVELS_ABOVE_DETAIL = 1;
    // Of the view's size, on every side: enough for small moves to find their ground already sampled.
    private static final double DETAIL_MARGIN_SHARE = 0.15D;
    private static final double BACKDROP_MARGIN_SHARE = 0.5D;
    // Detail takes at most this share of the tiles memory holds, leaving the rest for the backdrop and recent views.
    private static final double DETAIL_SHARE_OF_MEMORY = 0.5D;

    private final TerrainTiles tiles;
    private final Sight sight;
    private final int detailLevel;
    private final int previewLevel;
    private final int backdropLevel;
    private final List<HoledLayer> layers = new ArrayList<>();
    private final Set<TerrainTile> inUse = new HashSet<>();
    private final List<TerrainTile> wantedBackdrop = new ArrayList<>();
    private final List<TerrainTile> wantedPreview = new ArrayList<>();
    private final List<TerrainTile> wantedDetail = new ArrayList<>();

    private TerrainCover(TerrainTiles tiles, Sight sight, int detailLevel) {
        this.tiles = tiles;
        this.sight = sight;
        this.detailLevel = detailLevel;
        this.previewLevel = detailLevel + PREVIEW_LEVELS_ABOVE_DETAIL;
        this.backdropLevel = detailLevel + BACKDROP_LEVELS_ABOVE_DETAIL;
    }

    /**
     * Works out what to draw and asks {@code tiles} to sample what is still missing, most needed first.
     */
    static Result of(TerrainTiles tiles, OrbitProjection projection, int width, int height, int coveredLeftWidth) {
        TerrainSampling sampling = tiles.sampling();
        Sight sight = Sight.of(projection, coveredLeftWidth, width, height, sampling.floorY(), sampling.topY());
        TerrainCover cover = new TerrainCover(tiles, sight, detailLevelFor(sight, projection.pixelsPerBlock()));
        cover.coverSight();
        tiles.sampleOnly(cover.wantedInOrder(), cover.inUse);
        return new Result(List.copyOf(cover.layers), 1 << cover.detailLevel, tiles.pendingCount());
    }

    // The widest columns that still draw at most a pixel wide, unless there would be more of them in sight than pixels
    // to show them, as looking at the ground from low down foreshortens it, or than memory holds.
    private static int detailLevelFor(Sight sight, double pixelsPerBlock) {
        int level = 0;
        while ((1 << (level + 1)) * pixelsPerBlock <= MOST_COLUMN_PIXELS) {
            level++;
        }
        long columnsPerTile = (long) TerrainTile.COLUMNS * TerrainTile.COLUMNS;
        long affordableColumns = (long) (TerrainTiles.mostColumnsInMemory() * DETAIL_SHARE_OF_MEMORY);
        long showableColumns = (long) (sight.pixelsWithin(DETAIL_MARGIN_SHARE) * MOST_COLUMNS_PER_PIXEL);
        long mostTiles = Math.min(affordableColumns, showableColumns) / columnsPerTile;
        while (sight.tilesInSight(level, DETAIL_MARGIN_SHARE).size() > mostTiles) {
            level++;
        }
        return level;
    }

    private void coverSight() {
        for (TerrainTile backdrop : sight.tilesInSight(backdropLevel, BACKDROP_MARGIN_SHARE)) {
            cover(backdrop, true);
        }
    }

    /**
     * Draws what is ready of the tile in sight, finest first, each coarser tile with the finer ready ones inside it to
     * fill its hole. Ready tiles out of sight fill holes too, which keeps a hole's edge out of sight as well.
     *
     * @return the ready tiles, this one or finer ones inside it, that together cover it as far as they reach
     */
    private List<TerrainTile> cover(TerrainTile tile, boolean inSight) {
        if (tile.level() == detailLevel) {
            return coverDetail(tile, inSight);
        }
        List<TerrainTile> coveredInside = new ArrayList<>();
        for (TerrainTile child : tile.children()) {
            coveredInside.addAll(cover(child, inSight && sight.sees(child, DETAIL_MARGIN_SHARE)));
        }
        TerrainLayer whole = inSight ? leftoverOrBackdropOf(tile) : null;
        boolean ready = whole != null || !inSight && tiles.isSampled(tile);
        if (inSight && tile.level() == backdropLevel && (!ready || tiles.isStandingIn(tile))) {
            wantedBackdrop.add(tile);
        }
        if (inSight && tile.level() == previewLevel && !ready && !coveredInside.containsAll(tile.children())) {
            wantedPreview.add(tile);
        }
        if (!ready) {
            return coveredInside;
        }
        // Drawn even when finer tiles fill all of it, as it still covers for them until they can be drawn themselves.
        if (whole != null) {
            draw(tile, whole, coveredInside);
        }
        return List.of(tile);
    }

    // Other tiles between detail and backdrop are only left over from other zoom levels and never sampled anew, so
    // ground from before the noise changed would stand in for them for good; the backdrop covers them instead. Preview
    // ground standing in is left to the backdrop too, while its tile is sampled anew.
    private TerrainLayer leftoverOrBackdropOf(TerrainTile tile) {
        if (tile.level() != backdropLevel && tiles.isStandingIn(tile)) {
            return null;
        }
        return tiles.layerOf(tile);
    }

    private List<TerrainTile> coverDetail(TerrainTile tile, boolean inSight) {
        if (!inSight) {
            return tiles.isSampled(tile) ? List.of(tile) : finerLeftoversOutOfSight(tile);
        }
        TerrainLayer whole = tiles.layerOf(tile);
        if (whole != null) {
            draw(tile, whole);
            if (tiles.isStandingIn(tile)) {
                wantedDetail.add(tile);
            }
            return List.of(tile);
        }
        wantedDetail.add(tile);
        return coverWithFinerLeftovers(tile);
    }

    private List<TerrainTile> coverWithFinerLeftovers(TerrainTile tile) {
        if (tile.level() == 0) {
            return List.of();
        }
        List<TerrainTile> covered = new ArrayList<>();
        for (TerrainTile child : tile.children()) {
            if (!sight.sees(child, DETAIL_MARGIN_SHARE)) {
                if (tiles.isSampled(child)) {
                    covered.add(child);
                }
                continue;
            }
            TerrainLayer leftover = tiles.layerOf(child);
            if (leftover != null) {
                draw(child, leftover);
                covered.add(child);
            }
        }
        return covered;
    }

    private List<TerrainTile> finerLeftoversOutOfSight(TerrainTile tile) {
        if (tile.level() == 0) {
            return List.of();
        }
        return tile.children().stream().filter(tiles::isSampled).toList();
    }

    private void draw(TerrainTile tile, TerrainLayer layer) {
        draw(tile, layer, List.of());
    }

    private void draw(TerrainTile tile, TerrainLayer layer, List<TerrainTile> fillers) {
        layers.add(new HoledLayer(tile, layer, List.copyOf(fillers)));
        inUse.add(tile);
    }

    private List<TerrainTile> wantedInOrder() {
        Comparator<TerrainTile> nearestFirst = Comparator.comparingDouble(sight::distanceFromMiddle);
        wantedBackdrop.sort(nearestFirst);
        wantedPreview.sort(nearestFirst);
        wantedDetail.sort(nearestFirst);
        List<TerrainTile> wanted = new ArrayList<>(wantedBackdrop.size() + wantedPreview.size() + wantedDetail.size());
        wanted.addAll(wantedBackdrop);
        wanted.addAll(wantedPreview);
        wanted.addAll(wantedDetail);
        return wanted;
    }

    /**
     * @param detailStep   how many blocks wide the columns are that the view wants
     * @param pendingTiles how many tiles are still to be sampled for it
     */
    record Result(List<HoledLayer> layers, int detailStep, int pendingTiles) {
    }

    /**
     * What the camera sees: a tile is in sight when any part of it, from the lowest ground the noise reaches to the
     * highest, lands in the view or within a margin around it.
     */
    private record Sight(OrbitProjection projection, int left, int right, int bottom, int floorY, int topY) {
        static Sight of(OrbitProjection projection, int coveredLeftWidth, int width, int height, int floorY, int topY) {
            return new Sight(projection, Math.max(0, Math.min(width - 1, coveredLeftWidth)), width, height, floorY, topY);
        }

        double pixelsWithin(double marginShare) {
            double marginFactor = 1D + 2D * marginShare;
            return (right - left) * marginFactor * bottom * marginFactor;
        }

        List<TerrainTile> tilesInSight(int level, double marginShare) {
            double marginX = (right - left) * marginShare;
            double marginY = bottom * marginShare;
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (double screenX : new double[]{left - marginX, right + marginX}) {
                for (double screenY : new double[]{-marginY, bottom + marginY}) {
                    for (double groundY : new double[]{floorY, topY}) {
                        Vec3 point = projection.pointAt(screenX, screenY, groundY);
                        minX = Math.min(minX, point.x());
                        minZ = Math.min(minZ, point.z());
                        maxX = Math.max(maxX, point.x());
                        maxZ = Math.max(maxZ, point.z());
                    }
                }
            }
            TerrainTile first = TerrainTile.containing(minX, minZ, level);
            TerrainTile last = TerrainTile.containing(maxX, maxZ, level);
            List<TerrainTile> inSight = new ArrayList<>();
            for (int tileZ = first.tileZ(); tileZ <= last.tileZ(); tileZ++) {
                for (int tileX = first.tileX(); tileX <= last.tileX(); tileX++) {
                    TerrainTile tile = new TerrainTile(level, tileX, tileZ);
                    if (sees(tile, marginShare)) {
                        inSight.add(tile);
                    }
                }
            }
            return inSight;
        }

        boolean sees(TerrainTile tile, double marginShare) {
            double marginX = (right - left) * marginShare;
            double marginY = bottom * marginShare;
            double minScreenX = Double.POSITIVE_INFINITY;
            double minScreenY = Double.POSITIVE_INFINITY;
            double maxScreenX = Double.NEGATIVE_INFINITY;
            double maxScreenY = Double.NEGATIVE_INFINITY;
            long size = tile.sizeBlocks();
            for (long x : new long[]{tile.originX(), tile.originX() + size}) {
                for (long z : new long[]{tile.originZ(), tile.originZ() + size}) {
                    for (int y : new int[]{floorY, topY}) {
                        double screenX = projection.screenX(x, y, z);
                        double screenY = projection.screenY(x, y, z);
                        minScreenX = Math.min(minScreenX, screenX);
                        minScreenY = Math.min(minScreenY, screenY);
                        maxScreenX = Math.max(maxScreenX, screenX);
                        maxScreenY = Math.max(maxScreenY, screenY);
                    }
                }
            }
            return maxScreenX >= left - marginX && minScreenX <= right + marginX
                    && maxScreenY >= -marginY && minScreenY <= bottom + marginY;
        }

        double distanceFromMiddle(TerrainTile tile) {
            double half = tile.sizeBlocks() / 2D;
            double middleY = (floorY + topY) / 2D;
            double x = tile.originX() + half;
            double z = tile.originZ() + half;
            return Math.hypot(projection.screenX(x, middleY, z) - projection.screenCenterX(),
                    projection.screenY(x, middleY, z) - projection.screenCenterY());
        }
    }
}
