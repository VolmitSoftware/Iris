/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.region.IrisRegion;

import java.util.Objects;

/**
 * What the map shows: a view, and in the region view optionally one region previewed everywhere, each spot getting
 * the biome that region would place there.
 *
 * @param previewRegion the region previewed across the map, or null to show the world as it is
 */
public record MapLayer(MapView view, IrisRegion previewRegion) {
    public MapLayer {
        Objects.requireNonNull(view, "view");
        if (previewRegion != null && view != MapView.REGIONS) {
            throw new IllegalArgumentException("Only the region view previews a region");
        }
    }

    public static MapLayer of(MapView view) {
        return new MapLayer(view, null);
    }

    public static MapLayer preview(IrisRegion region) {
        return new MapLayer(MapView.REGIONS, Objects.requireNonNull(region, "region"));
    }

    public boolean previews() {
        return previewRegion != null;
    }

    /** Whether each spot is labelled with its region rather than a biome; a preview labels the biomes. */
    boolean labelsRegions() {
        return view.labelsRegions() && !previews();
    }

    RenderType colorType() {
        return previews() ? RenderType.BIOME : view.colorType();
    }
}
