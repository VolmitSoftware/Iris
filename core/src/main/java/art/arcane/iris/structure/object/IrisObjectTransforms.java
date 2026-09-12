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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.AxisAlignedBB;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.geometry.IrisVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.interpolation.Interpolation3D;

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
        return scaled(self, scale, interpolation, ScaleOrigin.SAVED);
    }

    private static IrisObject scaled(IrisObject self, double scale, IrisObjectPlacementScaleInterpolator interpolation,
                                     ScaleOrigin origin) {
        IrisObjectScale.requireValidFactor(scale, "Object scale");
        if (interpolation == null) {
            interpolation = IrisObjectPlacementScaleInterpolator.NONE;
        }
        boolean savedOrigin = origin == ScaleOrigin.SAVED;
        List<ScaledVoxel> voxels;
        IrisObject output;
        self.readLock.lock();
        try {
            output = savedOrigin ? createOriginScaledObject(self, scale)
                    : createCenteredScaledObject(self, scale);
            output.setLoadKey(self.getLoadKey());
            output.setLoader(self.getLoader());
            output.setLoadFile(self.getLoadFile());
            voxels = new ArrayList<>(self.blocks.size());
            Vector3i sourceCenter = self.getCenter();
            IrisBlockVector targetCenter = new IrisBlockVector(output.getCenter().getX(),
                    output.getCenter().getY(), output.getCenter().getZ());
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : self.blocks) {
                IrisBlockVector sourcePosition = entry.getKey();
                IrisBlockVector position = savedOrigin ? sourcePosition : new IrisBlockVector(
                        sourcePosition.getBlockX() + sourceCenter.getX(),
                        sourcePosition.getBlockY() + sourceCenter.getY(),
                        sourcePosition.getBlockZ() + sourceCenter.getZ());
                IrisBlockVector minimum = scaledMinimum(position, scale);
                IrisBlockVector maximum = scaledMaximum(position, scale);
                if (!savedOrigin) {
                    minimum.subtract(targetCenter);
                    maximum.subtract(targetCenter);
                }
                TileData tile = self.states.get(sourcePosition);
                voxels.add(new ScaledVoxel(sourcePosition.clone(), minimum, maximum, entry.getValue(),
                        tile == null ? null : tile.clone()));
            }
        } finally {
            self.readLock.unlock();
        }
        if (scale != Math.rint(scale)) {
            voxels.sort(Comparator.comparingInt((ScaledVoxel voxel) -> voxel.source().getBlockX())
                    .thenComparingInt(voxel -> voxel.source().getBlockY())
                    .thenComparingInt(voxel -> voxel.source().getBlockZ()));
        }
        for (ScaledVoxel voxel : voxels) {
            writeScaledVoxel(output, voxel);
        }

        VectorMap<PlatformBlockState> scaledBlocks = output.blocks;
        if (scale > 1) {
            switch (interpolation) {
                case TRILINEAR -> trilinear(output, (int) Math.round(scale));
                case TRICUBIC -> tricubic(output, (int) Math.round(scale));
                case TRIHERMITE -> trihermite(output, (int) Math.round(scale));
            }
        }
        removeInapplicableTiles(output, scaledBlocks);
        return output;
    }

    private static void writeScaledVoxel(IrisObject output, ScaledVoxel voxel) {
        IrisBlockVector minimum = voxel.minimum();
        IrisBlockVector maximum = voxel.maximum();
        IrisBlockVector position = minimum.clone();
        for (int x = minimum.getBlockX(); x <= maximum.getBlockX(); x++) {
            position.setX(x);
            for (int z = minimum.getBlockZ(); z <= maximum.getBlockZ(); z++) {
                position.setZ(z);
                for (int y = minimum.getBlockY(); y <= maximum.getBlockY(); y++) {
                    position.setY(y);
                    output.blocks.put(position, voxel.block());
                    if (voxel.tile() == null) {
                        output.states.remove(position);
                    } else {
                        output.states.put(position, voxel.tile().clone());
                    }
                }
            }
        }
    }

    private static IrisObject createCenteredScaledObject(IrisObject source, double scale) {
        IrisObject object = new IrisObject((int) Math.ceil(source.w * scale),
                (int) Math.ceil(source.h * scale), (int) Math.ceil(source.d * scale));
        Vector3i center = object.getCenter();
        object.aabb.aquire(() -> new AxisAlignedBB(
                new IrisPosition(-center.getX(), -center.getY(), -center.getZ()),
                new IrisPosition(object.w - center.getX() - 1, object.h - center.getY() - 1,
                        object.d - center.getZ() - 1)));
        return object;
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
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.air());
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
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.air());
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
                            b.put(new IrisBlockVector(x, y, z), IrisObject.States.air());
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

    private record ScaledVoxel(IrisBlockVector source, IrisBlockVector minimum, IrisBlockVector maximum,
                               PlatformBlockState block, TileData tile) {
    }

    private enum ScaleOrigin {
        DEFAULT,
        SAVED
    }
}
