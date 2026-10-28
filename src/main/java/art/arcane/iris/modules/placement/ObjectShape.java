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

package art.arcane.iris.modules.placement;

import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/** The object being placed as the placement stages see it: its declared size, the block its offsets count from, and where it must stand on solid ground. */
public final class ObjectShape {
    private final PlaceableObject object;
    private final PackWorld world;

    ObjectShape(PlaceableObject object, PackWorld world) {
        this.object = object;
        this.world = world;
    }

    public int width() {
        return object.width();
    }

    public int height() {
        return object.height();
    }

    public int depth() {
        return object.depth();
    }

    /** The block the object's offsets count from, relative to its corner. */
    public int centerX() {
        return object.center().x();
    }

    public int centerY() {
        return object.center().y();
    }

    public int centerZ() {
        return object.center().z();
    }

    /** The pack object this is, by the id the pack gives its name. */
    public Optional<ResourceId> id() {
        return Optional.of(world.resources().idOf(object.name()));
    }

    /** The columns of the object's lowest solid layer, as offsets from its anchor before any transform; empty for the origin only. */
    public List<IrisBlockVector> surfaceSupportOffsets() {
        return SurfaceSupportOffsets.of(world).of(object, world.resources().blocks());
    }
}
