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

import art.arcane.iris.builtin.placement.PlacementTerrain;
import art.arcane.iris.builtin.placement.StructureVolumes;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.resource.ResourceId;

import java.util.Optional;

/**
 * An Iris object placer as the placement stages see their terrain. {@code loader} is the object's data, which the placer
 * reads heights through; {@code data} is the pack data the placement was asked to run with, whose engine knows the
 * objects already placed.
 */
final class IrisPlacementTerrain implements PlacementTerrain {
    private static final StructureVolumes NO_VOLUMES = new StructureVolumes() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return false;
        }

        @Override
        public boolean containsWithin(int x, int y, int z, int margin) {
            return false;
        }
    };

    private final IObjectPlacer placer;
    private final IrisData loader;
    private final IrisData data;

    IrisPlacementTerrain(IObjectPlacer placer, IrisData loader, IrisData data) {
        this.placer = placer;
        this.loader = loader;
        this.data = data;
    }

    @Override
    public int highest(int x, int z, boolean ignoreFluid) {
        return placer.getHighest(x, z, loader, ignoreFluid);
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return placer.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return placer.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return placer.isUnderwater(x, z);
    }

    @Override
    public int fluidHeight() {
        return placer.getFluidHeight();
    }

    @Override
    public boolean preventsDecay() {
        return placer.isPreventingDecay();
    }

    @Override
    public boolean debugsSmartBore() {
        return placer.isDebugSmartBore();
    }

    @Override
    public boolean hasWorld() {
        return placer.getEngine() != null;
    }

    @Override
    public double slopeAt(int x, int z) {
        return engine().getComplex().getSlopeStream().get(x, z);
    }

    @Override
    public double heightAt(double x, double z) {
        return engine().getComplex().getHeightStream().get(x, z);
    }

    @Override
    public int minWorldY() {
        return engine().getMinHeight();
    }

    @Override
    public int worldHeight() {
        return engine().getHeight();
    }

    @Override
    public StructureVolumes structureVolumes(int minX, int minZ, int maxX, int maxZ) {
        KList<NativeStructureVolume> volumes = engine().getNativeStructureVolumes(minX, minZ, maxX, maxZ);
        if (volumes == null || volumes.isEmpty()) {
            return NO_VOLUMES;
        }
        return new NativeVolumes(volumes);
    }

    @Override
    public Optional<ResourceId> objectAt(int x, int y, int z) {
        Engine engine = data.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Object placement requires active Iris data for collision settings.");
        }
        var placed = engine.getObjectPlacement(x, y, z);
        if (placed == null || placed.getObject() == null) {
            return Optional.empty();
        }
        return Optional.of(data.getPackResources().idOf(placed.getObject().getLoadKey()));
    }

    private Engine engine() {
        Engine engine = placer.getEngine();
        if (engine == null) {
            throw new IllegalStateException("This placement runs without an Iris engine, so it has no world");
        }
        return engine;
    }

    private record NativeVolumes(KList<NativeStructureVolume> volumes) implements StructureVolumes {
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean intersects(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return volumes.stream().anyMatch(volume -> volume.intersects(minX, minY, minZ, maxX, maxY, maxZ));
        }

        @Override
        public boolean containsWithin(int x, int y, int z, int margin) {
            for (NativeStructureVolume volume : volumes) {
                if (volume.containsWithin(x, y, z, margin)) {
                    return true;
                }
            }
            return false;
        }
    }
}
