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

package art.arcane.iris.structure.nativegen;

import art.arcane.volmlib.util.collection.KList;

/**
 * World-space axis-aligned bounds of one native structure piece. Volumes are resolved from seed, registry and
 * pack policy alone so the same query answers identically regardless of which chunks exist.
 */
public record NativeStructureVolume(
        String structure,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    // Shared empty sentinel handed to every engine and memoized by the volume caches; a silent
    // add()/clear() here would corrupt them all globally, so mutation fails loudly instead.
    public static final KList<NativeStructureVolume> NONE = new KList<>() {
        @Override
        public boolean add(NativeStructureVolume volume) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public void add(int index, NativeStructureVolume volume) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public boolean addAll(java.util.Collection<? extends NativeStructureVolume> volumes) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public boolean addAll(int index, java.util.Collection<? extends NativeStructureVolume> volumes) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public NativeStructureVolume remove(int index) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }

        @Override
        public boolean remove(Object volume) {
            throw new UnsupportedOperationException("NativeStructureVolume.NONE is immutable");
        }
    };

    public static NativeStructureVolume of(String structure, int aX, int aY, int aZ, int bX, int bY, int bZ) {
        return new NativeStructureVolume(
                structure,
                Math.min(aX, bX),
                Math.min(aY, bY),
                Math.min(aZ, bZ),
                Math.max(aX, bX),
                Math.max(aY, bY),
                Math.max(aZ, bZ));
    }

    public boolean intersectsRect(int rectMinX, int rectMinZ, int rectMaxX, int rectMaxZ) {
        return maxX >= rectMinX && minX <= rectMaxX && maxZ >= rectMinZ && minZ <= rectMaxZ;
    }

    public boolean intersects(int boxMinX, int boxMinY, int boxMinZ, int boxMaxX, int boxMaxY, int boxMaxZ) {
        return maxX >= boxMinX && minX <= boxMaxX
                && maxY >= boxMinY && minY <= boxMaxY
                && maxZ >= boxMinZ && minZ <= boxMaxZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsWithin(int x, int y, int z, int margin) {
        return x >= minX - margin && x <= maxX + margin
                && y >= minY - margin && y <= maxY + margin
                && z >= minZ - margin && z <= maxZ + margin;
    }
}
