package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.decoration.tree.TreeGrounding;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class ObjectPlacementBake {
    private static final int MAXIMUM_VARIANTS_PER_OBJECT = 32;

    private final Layout layout;
    private final Key key;
    private final int[] offsetX;
    private final int[] offsetY;
    private final int[] offsetZ;
    private final int[] layerY;
    private final short[] paletteIndexes;
    private final long[] canopy;
    private final int[] tileBlocks;
    private final TileData[] tileValues;
    private final int[] sources;
    private volatile double[] exact;

    private ObjectPlacementBake(Layout layout, Key key, Cells cells) {
        this.layout = layout;
        this.key = key;
        this.offsetX = cells.offsetX();
        this.offsetY = cells.offsetY();
        this.offsetZ = cells.offsetZ();
        this.layerY = cells.layerY();
        this.paletteIndexes = cells.paletteIndexes();
        this.canopy = cells.canopy();
        this.tileBlocks = cells.tileBlocks();
        this.tileValues = cells.tileValues();
        this.sources = cells.sources();
    }

    static ObjectPlacementBake of(IrisObject object, SpinKernel spin, boolean ceilingHang, IrisBlockVector translateOffset) {
        Key key = Key.of(spin, ceilingHang, translateOffset);
        object.readLock.lock();
        try {
            Layout layout = Layout.of(object);
            ConcurrentHashMap<Key, ObjectPlacementBake> variants = object.placementBakes;
            ObjectPlacementBake cached = variants.get(key);
            if (cached != null && cached.layout == layout) {
                return cached;
            }
            Cells cells = spin.axisAligned
                    ? transformed(object, layout, spin, ceilingHang, translateOffset)
                    : resampled(object, layout, spin, ceilingHang, translateOffset);
            ObjectPlacementBake built = new ObjectPlacementBake(layout, key, cells);
            if (cached != null) {
                variants.replace(key, cached, built);
            } else if (variants.size() < MAXIMUM_VARIANTS_PER_OBJECT) {
                variants.putIfAbsent(key, built);
            }
            return built;
        } finally {
            object.readLock.unlock();
        }
    }

    private static Cells transformed(IrisObject object, Layout layout, SpinKernel spin, boolean ceilingHang,
                                     IrisBlockVector translateOffset) {
        int count = layout.count;
        int[] x = new int[count];
        int[] y = new int[count];
        int[] z = new int[count];
        int[] layer = new int[count];
        boolean layerDiffers = false;
        IrisBlockVector rotated = new IrisBlockVector(0, 0, 0);
        VectorMap<NativeBlockState>.Cursor cursor = object.blocks.cursor();
        int index = 0;
        while (cursor.next() && index < count) {
            RotationResampler.transform(cursor.key(), rotated, spin, ceilingHang, translateOffset);
            int roundedY = (int) Math.round(rotated.getY());
            int floorY = rotated.getBlockY();
            x[index] = (int) Math.round(rotated.getX());
            y[index] = roundedY;
            z[index] = (int) Math.round(rotated.getZ());
            layer[index] = floorY;
            layerDiffers |= floorY != roundedY;
            index++;
        }
        if (index != count) {
            throw new IllegalStateException("Object " + object.getLoadKey() + " changed while its placement bake was built");
        }
        return new Cells(x, y, z, layerDiffers ? layer : null, layout.paletteIndexes, layout.canopy, layout.tileBlocks,
                layout.tileValues, null);
    }

    private static Cells resampled(IrisObject object, Layout layout, SpinKernel spin, boolean ceilingHang,
                                   IrisBlockVector translateOffset) {
        RotationResampler.Targets targets = layout.resampler(object).resample(spin, ceilingHang, translateOffset);
        return gather(layout, targets.x(), targets.y(), targets.z(), targets.sources());
    }

    private static Cells gather(Layout layout, int[] x, int[] y, int[] z, int[] sources) {
        int count = sources.length;
        short[] paletteIndexes = new short[count];
        long[] canopy = layout.canopy == null ? null : new long[(count + 63) >>> 6];
        IntArrayList tileBlocks = layout.tileBlocks == null ? null : new IntArrayList();
        List<TileData> tileValues = layout.tileBlocks == null ? null : new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int source = sources[index];
            paletteIndexes[index] = layout.paletteIndexes[source];
            if (canopy != null && (layout.canopy[source >>> 6] & (1L << (source & 63))) != 0L) {
                canopy[index >>> 6] |= 1L << (index & 63);
            }
            if (tileBlocks != null) {
                int slot = Arrays.binarySearch(layout.tileBlocks, source);
                if (slot >= 0) {
                    tileBlocks.add(index);
                    tileValues.add(layout.tileValues[slot]);
                }
            }
        }
        boolean tiled = tileBlocks != null && !tileBlocks.isEmpty();
        return new Cells(x, y, z, null, paletteIndexes, canopy, tiled ? tileBlocks.toIntArray() : null,
                tiled ? tileValues.toArray(new TileData[0]) : null, sources);
    }

    int count() {
        return offsetX.length;
    }

    int sourceCount() {
        return layout.count;
    }

    int offsetX(int index) {
        return offsetX[index];
    }

    int offsetY(int index) {
        return offsetY[index];
    }

    int offsetZ(int index) {
        return offsetZ[index];
    }

    int layerY(int index) {
        return layerY == null ? offsetY[index] : layerY[index];
    }

    int paletteIndex(int index) {
        return paletteIndexes[index];
    }

    NativeBlockState[] palette() {
        return layout.palette;
    }

    int[] tileBlocks() {
        return tileBlocks;
    }

    TileData[] tileValues() {
        return tileValues;
    }

    boolean canopy(int index) {
        return canopy != null && (canopy[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    String[] markers(String[] sourceMarkers) {
        if (sources == null) {
            return sourceMarkers;
        }
        String[] unclaimed = sourceMarkers.clone();
        String[] resolved = new String[sources.length];
        for (int index = 0; index < sources.length; index++) {
            int source = sources[index];
            resolved[index] = unclaimed[source];
            unclaimed[source] = null;
        }
        return resolved;
    }

    double[] exact(IrisObject object, SpinKernel spin) {
        double[] resolved = exact;
        if (resolved != null) {
            return resolved;
        }
        resolved = sources == null ? transformedExact(object, spin) : targetExact();
        exact = resolved;
        return resolved;
    }

    private double[] transformedExact(IrisObject object, SpinKernel spin) {
        int count = layout.count;
        object.readLock.lock();
        try {
            double[] resolved = new double[count * 3];
            IrisBlockVector rotated = new IrisBlockVector(0, 0, 0);
            VectorMap<NativeBlockState>.Cursor cursor = object.blocks.cursor();
            int index = 0;
            while (cursor.next() && index < count) {
                RotationResampler.transform(cursor.key(), rotated, spin, key.ceilingHang(), key.translateOffset());
                resolved[index * 3] = rotated.getX();
                resolved[index * 3 + 1] = rotated.getY();
                resolved[index * 3 + 2] = rotated.getZ();
                index++;
            }
            if (index != count) {
                throw new IllegalStateException("Object " + object.getLoadKey() + " changed while its placement bake was read");
            }
            return resolved;
        } finally {
            object.readLock.unlock();
        }
    }

    private double[] targetExact() {
        double[] resolved = new double[offsetX.length * 3];
        for (int index = 0; index < offsetX.length; index++) {
            resolved[index * 3] = offsetX[index];
            resolved[index * 3 + 1] = offsetY[index];
            resolved[index * 3 + 2] = offsetZ[index];
        }
        return resolved;
    }

    static final class Layout {
        private final long blocksRevision;
        private final long statesRevision;
        private final TreeGrounding grounding;
        private final int count;
        private final short[] paletteIndexes;
        private final NativeBlockState[] palette;
        private final int[] tileBlocks;
        private final TileData[] tileValues;
        private final long[] canopy;
        private volatile RotationResampler resampler;

        private Layout(long blocksRevision, long statesRevision, TreeGrounding grounding, int count, short[] paletteIndexes,
                       NativeBlockState[] palette, int[] tileBlocks, TileData[] tileValues, long[] canopy) {
            this.blocksRevision = blocksRevision;
            this.statesRevision = statesRevision;
            this.grounding = grounding;
            this.count = count;
            this.paletteIndexes = paletteIndexes;
            this.palette = palette;
            this.tileBlocks = tileBlocks;
            this.tileValues = tileValues;
            this.canopy = canopy;
        }

        private static Layout of(IrisObject object) {
            Layout current = object.placementLayout;
            if (current != null && current.matches(object)) {
                return current;
            }
            Layout built = build(object);
            if (!built.matches(object)) {
                return built;
            }
            object.placementLayout = built;
            if (current != null) {
                object.placementBakes.clear();
            }
            return built;
        }

        private static Layout build(IrisObject object) {
            VectorMap<NativeBlockState> blocks = object.blocks;
            VectorMap<TileData> states = object.states;
            long blocksRevision = blocks.modificationRevision();
            long statesRevision = states.modificationRevision();
            TreeGrounding grounding = object.getTreeGrounding();
            boolean hasStates = !states.isEmpty();
            int size = blocks.size();
            short[] paletteIndexes = new short[size];
            IntArrayList tileBlocks = new IntArrayList();
            List<TileData> tileValues = new ArrayList<>();
            List<NativeBlockState> palette = new ArrayList<>();
            IdentityHashMap<NativeBlockState, Integer> paletteSlots = new IdentityHashMap<>();
            long[] canopy = null;
            VectorMap<NativeBlockState>.Cursor cursor = blocks.cursor();
            int index = 0;
            while (cursor.next()) {
                IrisBlockVector source = cursor.key();
                NativeBlockState state = cursor.value();
                if (state == null) {
                    state = IrisObject.States.air();
                }
                Integer slot = paletteSlots.get(state);
                if (slot == null) {
                    slot = palette.size();
                    if (slot >= Short.MAX_VALUE) {
                        throw new IllegalStateException("Object " + object.getLoadKey() + " has more than "
                                + Short.MAX_VALUE + " distinct block states");
                    }
                    palette.add(state);
                    paletteSlots.put(state, slot);
                }
                if (index == paletteIndexes.length) {
                    throw new IllegalStateException("Object " + object.getLoadKey() + " changed while its placement layout was built");
                }
                paletteIndexes[index] = (short) slot.intValue();
                if (hasStates) {
                    TileData tile = states.get(source);
                    if (tile != null) {
                        tileBlocks.add(index);
                        tileValues.add(tile);
                    }
                }
                if (grounding != null && grounding.isCanopy(source.getBlockX(), source.getBlockY(), source.getBlockZ())) {
                    if (canopy == null) {
                        canopy = new long[(size + 63) >>> 6];
                    }
                    canopy[index >>> 6] |= 1L << (index & 63);
                }
                index++;
            }
            if (index != size) {
                throw new IllegalStateException("Object " + object.getLoadKey() + " changed while its placement layout was built");
            }
            return new Layout(blocksRevision, statesRevision, grounding, index, paletteIndexes,
                    palette.toArray(new NativeBlockState[0]), tileBlocks.isEmpty() ? null : tileBlocks.toIntArray(),
                    tileValues.isEmpty() ? null : tileValues.toArray(new TileData[0]), canopy);
        }

        private boolean matches(IrisObject object) {
            return object.blocks.modificationRevision() == blocksRevision
                    && object.states.modificationRevision() == statesRevision
                    && object.getTreeGrounding() == grounding;
        }

        private RotationResampler resampler(IrisObject object) {
            RotationResampler resolved = resampler;
            if (resolved == null) {
                resolved = RotationResampler.of(object, count);
                resampler = resolved;
            }
            return resolved;
        }
    }

    record Key(boolean rotates, int xMode, int yMode, int zMode, long xCos, long xSin, long yCos, long ySin,
               long zCos, long zSin, boolean ceilingHang, boolean translating, long translateX, long translateY,
               long translateZ) {
        static Key of(SpinKernel spin, boolean ceilingHang, IrisBlockVector translateOffset) {
            boolean translating = translateOffset != null;
            return new Key(spin.rotates, spin.xMode, spin.yMode, spin.zMode,
                    Double.doubleToLongBits(spin.xCos), Double.doubleToLongBits(spin.xSin),
                    Double.doubleToLongBits(spin.yCos), Double.doubleToLongBits(spin.ySin),
                    Double.doubleToLongBits(spin.zCos), Double.doubleToLongBits(spin.zSin), ceilingHang, translating,
                    translating ? Double.doubleToLongBits(translateOffset.getX()) : 0L,
                    translating ? Double.doubleToLongBits(translateOffset.getY()) : 0L,
                    translating ? Double.doubleToLongBits(translateOffset.getZ()) : 0L);
        }

        IrisBlockVector translateOffset() {
            return translating ? new IrisBlockVector(Double.longBitsToDouble(translateX),
                    Double.longBitsToDouble(translateY), Double.longBitsToDouble(translateZ)) : null;
        }
    }

    private record Cells(int[] offsetX, int[] offsetY, int[] offsetZ, int[] layerY, short[] paletteIndexes, long[] canopy,
                         int[] tileBlocks, TileData[] tileValues, int[] sources) {
    }
}
