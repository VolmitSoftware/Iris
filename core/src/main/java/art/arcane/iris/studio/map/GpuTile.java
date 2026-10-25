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

import java.lang.ref.WeakReference;

/**
 * A place on the ground on the graphics card: the colours and faces of the terrain last sampled there. They're put on
 * the card once, whole, and stay as finer tiles inside come and go, since the face shader leaves out whatever hole they
 * cut. When the noise or its heights change, the place is sampled anew; until the newest terrain is on the card, the
 * older one still covers the place. Only for the GPU thread.
 */
final class GpuTile implements GpuCache.Upload {
    private GpuColors colors;
    private GpuFaces faces;
    // Held weakly, so ground no longer drawn can be dropped from memory while its upload still stands in for it.
    private WeakReference<TerrainLayer> source;

    private GpuTile(GpuColors colors, GpuFaces faces, TerrainLayer source) {
        this.colors = colors;
        this.faces = faces;
        this.source = new WeakReference<>(source);
    }

    /**
     * @param largestSide the card's GL_MAX_TEXTURE_SIZE
     * @param mostFaces   the card's GL_MAX_TEXTURE_BUFFER_SIZE
     * @return the tile, or null when its terrain has more columns or rows than the card's textures can hold
     */
    static GpuTile upload(TerrainLayer layer, int largestSide, int mostFaces) {
        GpuColors colors = GpuColors.upload(layer.terrain(), largestSide);
        if (colors == null) {
            return null;
        }
        try {
            return new GpuTile(colors, GpuFaces.upload(layer.faces(), mostFaces), layer);
        } catch (RuntimeException failure) {
            colors.delete();
            throw failure;
        }
    }

    boolean shows(TerrainLayer wanted) {
        return source.get() == wanted;
    }

    // Ground whose terrain is gone from memory is drawn only while nothing newer for its place is on the card.
    boolean showsForgottenTerrain() {
        return source.get() == null;
    }

    /**
     * @return false when the layer's terrain has more columns or rows than the card's textures can hold
     */
    boolean showInstead(TerrainLayer wanted, int largestSide, int mostFaces) {
        GpuTile next = upload(wanted, largestSide, mostFaces);
        if (next == null) {
            return false;
        }
        delete();
        colors = next.colors;
        faces = next.faces;
        source = next.source;
        return true;
    }

    GpuColors colors() {
        return colors;
    }

    GpuFaces faces() {
        return faces;
    }

    @Override
    public long bytes() {
        return colors.bytes() + faces.bytes();
    }

    @Override
    public void delete() {
        colors.delete();
        faces.delete();
    }
}
