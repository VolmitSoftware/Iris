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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.placement.ObjectShape;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Optional;

/** An Iris object as the placement stages see it. */
final class IrisObjectShape implements ObjectShape {
    private final IrisObject object;
    private final PackResources resources;

    IrisObjectShape(IrisObject object, PackResources resources) {
        this.object = object;
        this.resources = resources;
    }

    @Override
    public int width() {
        return object.getW();
    }

    @Override
    public int height() {
        return object.getH();
    }

    @Override
    public int depth() {
        return object.getD();
    }

    @Override
    public int centerX() {
        return object.getCenter().getBlockX();
    }

    @Override
    public int centerY() {
        return object.getCenter().getBlockY();
    }

    @Override
    public int centerZ() {
        return object.getCenter().getBlockZ();
    }

    @Override
    public Optional<ResourceId> id() {
        return Optional.ofNullable(object.getLoadKey()).map(resources::idOf);
    }

    @Override
    public List<IrisBlockVector> surfaceSupportOffsets() {
        return object.getSurfaceSupportOffsets();
    }
}
