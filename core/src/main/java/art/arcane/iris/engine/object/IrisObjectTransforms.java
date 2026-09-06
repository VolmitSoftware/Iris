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

package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.AxisAlignedBB;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.math.IrisVector;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.iris.util.project.interpolation.Interpolation3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Geometric transforms for {@link IrisObject}: rotation, scaling and the interpolated upscalers.
 */
final class IrisObjectTransforms {
    private IrisObjectTransforms() {
    }

    static IrisObject rotateCopy(IrisObject self, IrisObjectRotation rt) {
        IrisObject copy = self.copy();
        rotate(copy, rt, 0, 0, 0);
        return copy;
    }

    static void rotate(IrisObject self, IrisObjectRotation r, int spinx, int spiny, int spinz) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> d = new VectorMap<>();
            Set<IrisBlockVector> omitted = new HashSet<>();

            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : self.blocks) {
                PlatformBlockState rotated = r.rotate(entry.getValue(), spinx, spiny, spinz);
                if (rotated == null) {
                    omitted.add(entry.getKey());
                    continue;
                }
                d.put(r.rotate(entry.getKey(), spinx, spiny, spinz), rotated);
            }

            VectorMap<TileData> dx = new VectorMap<>();

            for (Map.Entry<IrisBlockVector, TileData> entry : self.states) {
                if (!omitted.contains(entry.getKey())) {
                    dx.put(r.rotate(entry.getKey(), spinx, spiny, spinz), entry.getValue());
                }
            }

            self.blocks = d;
            self.states = dx;
            IrisObjectShaping.shrinkwrap(self);
            self.surfaceSupportOffsets.reset();
            self.floatingFootprint.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static IrisObject scaled(IrisObject self, double scale, IrisObjectPlacementScaleInterpolator interpolation) {
        return scaled(self, scale, interpolation, ScaleOrigin.DEFAULT);
    }

    static IrisObject scaledAroundOrigin(IrisObject self, double scale, IrisObjectPlacementScaleInterpolator interpolation) {
        if (!Double.isFinite(scale) || scale <= 0 || scale > 50) {
            throw new IllegalArgumentException("Object scale must be finite, greater than zero, and at most 50");
        }
        return scaled(self, scale, interpolation, ScaleOrigin.SAVED);
    }

    private static IrisObject scaled(IrisObject self, double scale, IrisObjectPlacementScaleInterpolator interpolation,
                                     ScaleOrigin origin) {
        if (interpolation == null) {
            interpolation = IrisObjectPlacementScaleInterpolator.NONE;
        }
        boolean savedOrigin = origin == ScaleOrigin.SAVED;
        IrisVector sm1 = new IrisVector(scale - 1, scale - 1, scale - 1);
        scale = Math.max(0.001, Math.min(50, scale));
        if (!savedOrigin && scale < 1) {
            scale = scale - 0.0001;
        }

        VectorMap<PlatformBlockState> placeBlock = new VectorMap<>();
        VectorMap<TileData> placeTile = new VectorMap<>();
        VectorMap<IrisBlockVector> placeMax = savedOrigin && scale > 1 ? new VectorMap<>() : null;

        IrisVector center;
        IrisObject oo;
        boolean hasTiles;
        self.readLock.lock();
        try {
            center = new IrisVector(self.getCenter().getX(), self.getCenter().getY(), self.getCenter().getZ());
            if (self.getH() == 2) {
                center = center.setY(center.getBlockY() + 0.5);
            }
            if (self.getW() == 2) {
                center = center.setX(center.getBlockX() + 0.5);
            }
            if (self.getD() == 2) {
                center = center.setZ(center.getBlockZ() + 0.5);
            }

            oo = savedOrigin ? createOriginScaledObject(self, scale)
                    : new IrisObject((int) Math.ceil((self.w * scale) + (scale * 2)), (int) Math.ceil((self.h * scale) + (scale * 2)), (int) Math.ceil((self.d * scale) + (scale * 2)));
            oo.setLoadKey(self.getLoadKey());
            oo.setLoader(self.getLoader());
            oo.setLoadFile(self.getLoadFile());
            hasTiles = !self.states.isEmpty();
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : self.blocks) {
                PlatformBlockState bd = entry.getValue();
                IrisBlockVector sourcePosition = entry.getKey();
                IrisBlockVector position = savedOrigin
                        ? scaledMinimum(sourcePosition, scale)
                        : sourcePosition.clone().add(IrisObject.HALF).subtract(center)
                                .multiply(scale).add(sm1).toBlockVector();
                placeBlock.put(position, bd);
                if (placeMax != null) {
                    placeMax.put(position, scaledMaximum(sourcePosition, scale));
                }
                if (hasTiles) {
                    TileData tile = self.states.get(entry.getKey());
                    if (tile == null) {
                        placeTile.remove(position);
                    } else {
                        placeTile.put(position, tile);
                    }
                }
            }
        } finally {
            self.readLock.unlock();
        }

        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : placeBlock) {
            IrisBlockVector v = entry.getKey();
            TileData tile = hasTiles ? placeTile.get(v) : null;
            if (scale > 1) {
                IrisVector minimum = savedOrigin ? v : v.clone().add(center);
                IrisVector maximum = savedOrigin ? placeMax.get(v) : v.clone().add(center).add(sm1);
                int minX = Math.min(minimum.getBlockX(), maximum.getBlockX());
                int maxX = Math.max(minimum.getBlockX(), maximum.getBlockX());
                int minY = Math.min(minimum.getBlockY(), maximum.getBlockY());
                int maxY = Math.max(minimum.getBlockY(), maximum.getBlockY());
                int minZ = Math.min(minimum.getBlockZ(), maximum.getBlockZ());
                int maxZ = Math.max(minimum.getBlockZ(), maximum.getBlockZ());
                IrisBlockVector position = new IrisBlockVector(minX, minY, minZ);
                for (int x = minX; x <= maxX; x++) {
                    position.setX(x);
                    for (int z = minZ; z <= maxZ; z++) {
                        position.setZ(z);
                        for (int y = minY; y <= maxY; y++) {
                            position.setY(y);
                            oo.blocks.put(position, entry.getValue());
                            if (hasTiles) {
                                if (tile == null) {
                                    oo.states.remove(position);
                                } else {
                                    oo.states.put(position, tile.clone());
                                }
                            }
                        }
                    }
                }
            } else {
                IrisBlockVector position = savedOrigin
                        ? v
                        : oo.getSigned(v.getBlockX(), v.getBlockY(), v.getBlockZ());
                oo.blocks.put(position, entry.getValue());
                if (hasTiles) {
                    if (tile == null) {
                        oo.states.remove(position);
                    } else {
                        oo.states.put(position, tile.clone());
                    }
                }
            }
        }

        VectorMap<PlatformBlockState> scaledBlocks = oo.blocks;
        if (scale > 1) {
            switch (interpolation) {
                case TRILINEAR -> trilinear(oo, (int) Math.round(scale));
                case TRICUBIC -> tricubic(oo, (int) Math.round(scale));
                case TRIHERMITE -> trihermite(oo, (int) Math.round(scale));
            }
        }

        removeInapplicableTiles(oo, scaledBlocks);
        return oo;
    }

    private static IrisObject createOriginScaledObject(IrisObject source, double scale) {
        IrisBlockVector minimum = scaledMinimum(new IrisBlockVector(
                -source.getCenter().getX(), -source.getCenter().getY(), -source.getCenter().getZ()), scale);
        IrisBlockVector maximum = scaledMaximum(new IrisBlockVector(
                source.getW() - source.getCenter().getX() - 1,
                source.getH() - source.getCenter().getY() - 1,
                source.getD() - source.getCenter().getZ() - 1), scale);
        IrisObject object = new IrisObject(
                maximum.getBlockX() - minimum.getBlockX() + 1,
                maximum.getBlockY() - minimum.getBlockY() + 1,
                maximum.getBlockZ() - minimum.getBlockZ() + 1);
        object.setCenter(new Vector3i(-minimum.getBlockX(), -minimum.getBlockY(), -minimum.getBlockZ()));
        object.aabb.aquire(() -> new AxisAlignedBB(
                new IrisPosition(minimum.getBlockX(), minimum.getBlockY(), minimum.getBlockZ()),
                new IrisPosition(maximum.getBlockX(), maximum.getBlockY(), maximum.getBlockZ())));
        return object;
    }

    private static IrisBlockVector scaledMinimum(IrisBlockVector position, double scale) {
        return new IrisBlockVector(Math.floor(position.getX() * scale),
                Math.floor(position.getY() * scale), Math.floor(position.getZ() * scale));
    }

    private static IrisBlockVector scaledMaximum(IrisBlockVector position, double scale) {
        return new IrisBlockVector(Math.ceil((position.getX() + 1) * scale) - 1,
                Math.ceil((position.getY() + 1) * scale) - 1,
                Math.ceil((position.getZ() + 1) * scale) - 1);
    }

    private static void removeInapplicableTiles(IrisObject object, VectorMap<PlatformBlockState> sourceBlocks) {
        Iterator<Map.Entry<IrisBlockVector, TileData>> iterator = object.states.iterator();
        while (iterator.hasNext()) {
            Map.Entry<IrisBlockVector, TileData> entry = iterator.next();
            PlatformBlockState block = object.blocks.get(entry.getKey());
            String tileKey = entry.getValue().getMaterialKey();
            if (tileKey == null) {
                tileKey = IrisObjectShaping.materialKey(sourceBlocks.get(entry.getKey()));
            } else {
                tileKey = tileKey.toLowerCase(Locale.ROOT);
                if (tileKey.indexOf(':') < 0) {
                    tileKey = "minecraft:" + tileKey;
                }
            }
            if (block == null || !tileKey.equals(IrisObjectShaping.materialKey(block))) {
                iterator.remove();
            }
        }
    }

    static void trilinear(IrisObject self, int rad) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTrilinear(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
            self.floatingFootprint.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static void tricubic(IrisObject self, int rad) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTricubic(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
            self.floatingFootprint.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    static void trihermite(IrisObject self, int rad) {
        trihermite(self, rad, 0D, 0D);
    }

    static void trihermite(IrisObject self, int rad, double tension, double bias) {
        self.writeLock.lock();
        try {
            VectorMap<PlatformBlockState> v = self.blocks;
            VectorMap<PlatformBlockState> b = new VectorMap<>();
            IrisPosition min = self.getAABB().min();
            IrisPosition max = self.getAABB().max();
            NearestBlockIndex nearestBlocks = NearestBlockIndex.create(v);

            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        if (Interpolation3D.getTrihermite(x, y, z, rad, (xx, yy, zz) -> {
                            PlatformBlockState data = v.get(new IrisBlockVector((int) xx, (int) yy, (int) zz));

                            if (B.isAir(data)) {
                                return 0;
                            }

                            return 1;
                        }, tension, bias) >= 0.5) {
                            b.put(new IrisBlockVector(x, y, z), nearestBlockData(v, nearestBlocks, x, y, z));
                        } else {
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.AIR);
                        }
                    }
                }
            }

            self.blocks = b;
            self.surfaceSupportOffsets.reset();
            self.floatingFootprint.reset();
        } finally {
            self.writeLock.unlock();
        }
    }

    private static PlatformBlockState nearestBlockData(VectorMap<PlatformBlockState> blocks,
                                                       NearestBlockIndex nearestBlocks,
                                                       int x, int y, int z) {
        IrisBlockVector vv = new IrisBlockVector(x, y, z);
        PlatformBlockState direct = blocks.get(vv);
        if (!B.isAir(direct)) {
            return direct;
        }
        return nearestBlocks.nearest(x, y, z, direct);
    }

    static final class NearestBlockIndex {
        private static final Comparator<NearestBlock> X_ORDER = Comparator
                .comparingInt(NearestBlock::x)
                .thenComparingInt(NearestBlock::rank);
        private static final Comparator<NearestBlock> Y_ORDER = Comparator
                .comparingInt(NearestBlock::y)
                .thenComparingInt(NearestBlock::rank);
        private static final Comparator<NearestBlock> Z_ORDER = Comparator
                .comparingInt(NearestBlock::z)
                .thenComparingInt(NearestBlock::rank);

        private final NearestNode root;
        private PlatformBlockState bestState;
        private double bestDistance;
        private int bestRank;

        private NearestBlockIndex(NearestNode root) {
            this.root = root;
        }

        static NearestBlockIndex create(VectorMap<PlatformBlockState> blocks) {
            List<NearestBlock> points = new ArrayList<>(blocks.size());
            VectorMap<PlatformBlockState>.Cursor cursor = blocks.cursor();
            int rank = 0;
            while (cursor.next()) {
                PlatformBlockState state = cursor.value();
                if (!B.isAir(state)) {
                    IrisBlockVector position = cursor.key();
                    points.add(new NearestBlock(position.getBlockX(), position.getBlockY(), position.getBlockZ(),
                            rank, state));
                }
                rank++;
            }

            NearestBlock[] pointArray = points.toArray(new NearestBlock[0]);
            return new NearestBlockIndex(build(pointArray, 0, pointArray.length, 0));
        }

        PlatformBlockState nearest(int x, int y, int z, PlatformBlockState fallback) {
            if (root == null) {
                return fallback;
            }

            bestState = fallback;
            bestDistance = Double.MAX_VALUE;
            bestRank = Integer.MAX_VALUE;
            search(root, x, y, z);
            return bestState;
        }

        private static NearestNode build(NearestBlock[] points, int from, int to, int depth) {
            if (from >= to) {
                return null;
            }

            int axis = depth % 3;
            Arrays.sort(points, from, to, comparator(axis));
            int middle = (from + to) >>> 1;
            return new NearestNode(
                    points[middle],
                    axis,
                    build(points, from, middle, depth + 1),
                    build(points, middle + 1, to, depth + 1)
            );
        }

        private static Comparator<NearestBlock> comparator(int axis) {
            return switch (axis) {
                case 0 -> X_ORDER;
                case 1 -> Y_ORDER;
                default -> Z_ORDER;
            };
        }

        private void search(NearestNode node, int x, int y, int z) {
            if (node == null) {
                return;
            }

            NearestBlock point = node.point();
            double xDistance = point.x() - x;
            double yDistance = point.y() - y;
            double zDistance = point.z() - z;
            double distance = (xDistance * xDistance) + (yDistance * yDistance) + (zDistance * zDistance);
            if (distance < bestDistance || (distance == bestDistance && point.rank() < bestRank)) {
                bestState = point.state();
                bestDistance = distance;
                bestRank = point.rank();
            }

            double axisDistance = switch (node.axis()) {
                case 0 -> x - point.x();
                case 1 -> y - point.y();
                default -> z - point.z();
            };
            NearestNode near = axisDistance <= 0D ? node.lower() : node.upper();
            NearestNode far = axisDistance <= 0D ? node.upper() : node.lower();
            search(near, x, y, z);
            if ((axisDistance * axisDistance) <= bestDistance) {
                search(far, x, y, z);
            }
        }
    }

    private record NearestNode(NearestBlock point, int axis, NearestNode lower, NearestNode upper) {
    }

    private record NearestBlock(int x, int y, int z, int rank, PlatformBlockState state) {
    }

    private enum ScaleOrigin {
        DEFAULT,
        SAVED
    }
}
