package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public final class RotationResampler {
    private final int[] sourceX;
    private final int[] sourceY;
    private final int[] sourceZ;
    private final Box sourceBox;
    private final int[] sourceCells;

    private RotationResampler(int[] sourceX, int[] sourceY, int[] sourceZ, Box sourceBox, int[] sourceCells) {
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceZ = sourceZ;
        this.sourceBox = sourceBox;
        this.sourceCells = sourceCells;
    }

    public static RotationResampler of(String owner, int[] x, int[] y, int[] z) {
        int count = x.length;
        if (y.length != count || z.length != count) {
            throw new IllegalArgumentException(owner + " has " + x.length + "/" + y.length + "/" + z.length
                    + " source coordinates on its x/y/z axes");
        }
        if (count == 0) {
            return new RotationResampler(x, y, z, new Box(0, 0, 0, 0, 0, 0), new int[1]);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int index = 0; index < count; index++) {
            minX = Math.min(minX, x[index]);
            minY = Math.min(minY, y[index]);
            minZ = Math.min(minZ, z[index]);
            maxX = Math.max(maxX, x[index]);
            maxY = Math.max(maxY, y[index]);
            maxZ = Math.max(maxZ, z[index]);
        }
        Box box = new Box(minX, maxX, minY, maxY, minZ, maxZ);
        if (box.volume() > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException(owner + " spans " + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x"
                    + (maxZ - minZ + 1) + " blocks, too large to resample for a non-right-angle rotation");
        }
        int[] cells = new int[(int) box.volume()];
        for (int index = 0; index < count; index++) {
            cells[(int) box.cell(x[index], y[index], z[index])] = index + 1;
        }
        return new RotationResampler(x, y, z, box, cells);
    }

    static RotationResampler of(IrisObject object, int count) {
        int[] x = new int[count];
        int[] y = new int[count];
        int[] z = new int[count];
        VectorMap<NativeBlockState>.Cursor cursor = object.blocks.cursor();
        int index = 0;
        while (index < count && cursor.next()) {
            IrisBlockVector position = cursor.key();
            x[index] = position.getBlockX();
            y[index] = position.getBlockY();
            z[index] = position.getBlockZ();
            index++;
        }
        if (index != count) {
            throw new IllegalStateException("Object " + object.getLoadKey() + " changed while its placement grid was built");
        }
        return of("Object " + object.getLoadKey(), x, y, z);
    }

    public Targets resample(double xDegrees, double yDegrees, double zDegrees) {
        return resample(new SpinKernel(IrisObjectRotation.of(xDegrees, yDegrees, zDegrees), 0, 0, 0), false, null);
    }

    Targets resample(SpinKernel spin, boolean ceilingHang, IrisBlockVector translateOffset) {
        int count = sourceX.length;
        Box bounds = targetBounds(spin, ceilingHang, translateOffset);
        long[] occupied = new long[(int) ((bounds.volume() + 63L) >>> 6)];
        long[] sampled = new long[(count + 63) >>> 6];
        IntArrayList x = new IntArrayList(count);
        IntArrayList y = new IntArrayList(count);
        IntArrayList z = new IntArrayList(count);
        IntArrayList sources = new IntArrayList(count);
        IrisBlockVector probe = new IrisBlockVector(0, 0, 0);
        for (int targetX = bounds.minX(); targetX <= bounds.maxX(); targetX++) {
            for (int targetY = bounds.minY(); targetY <= bounds.maxY(); targetY++) {
                for (int targetZ = bounds.minZ(); targetZ <= bounds.maxZ(); targetZ++) {
                    probe.setX(targetX);
                    probe.setY(targetY);
                    probe.setZ(targetZ);
                    if (translateOffset != null) {
                        probe.subtract(translateOffset);
                    }
                    if (ceilingHang) {
                        probe.setY(-probe.getY());
                    }
                    spin.inverseRotate(probe);
                    int source = source((int) Math.round(probe.getX()), (int) Math.round(probe.getY()),
                            (int) Math.round(probe.getZ()));
                    if (source < 0) {
                        continue;
                    }
                    long cell = bounds.cell(targetX, targetY, targetZ);
                    occupied[(int) (cell >>> 6)] |= 1L << (cell & 63L);
                    sampled[source >>> 6] |= 1L << (source & 63);
                    x.add(targetX);
                    y.add(targetY);
                    z.add(targetZ);
                    sources.add(source);
                }
            }
        }
        for (int source = 0; source < count; source++) {
            if ((sampled[source >>> 6] & (1L << (source & 63))) != 0L) {
                continue;
            }
            probe.setX(sourceX[source]);
            probe.setY(sourceY[source]);
            probe.setZ(sourceZ[source]);
            transform(probe, probe, spin, ceilingHang, translateOffset);
            int targetX = Math.clamp(Math.round(probe.getX()), bounds.minX(), bounds.maxX());
            int targetY = Math.clamp(Math.round(probe.getY()), bounds.minY(), bounds.maxY());
            int targetZ = Math.clamp(Math.round(probe.getZ()), bounds.minZ(), bounds.maxZ());
            long cell = bounds.cell(targetX, targetY, targetZ);
            if ((occupied[(int) (cell >>> 6)] & (1L << (cell & 63L))) == 0L) {
                occupied[(int) (cell >>> 6)] |= 1L << (cell & 63L);
                x.add(targetX);
                y.add(targetY);
                z.add(targetZ);
                sources.add(source);
            }
        }
        return new Targets(x.toIntArray(), y.toIntArray(), z.toIntArray(), sources.toIntArray());
    }

    static void transform(IrisBlockVector source, IrisBlockVector target, SpinKernel spin, boolean ceilingHang,
                          IrisBlockVector translateOffset) {
        target.setX(source.getX());
        target.setY(source.getY());
        target.setZ(source.getZ());
        spin.rotate(target);
        if (ceilingHang) {
            target.setY(-target.getBlockY());
        }
        if (translateOffset != null) {
            target.add(translateOffset);
        }
    }

    private int source(int x, int y, int z) {
        return sourceBox.contains(x, y, z) ? sourceCells[(int) sourceBox.cell(x, y, z)] - 1 : -1;
    }

    private Box targetBounds(SpinKernel spin, boolean ceilingHang, IrisBlockVector translateOffset) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        IrisBlockVector corner = new IrisBlockVector(0, 0, 0);
        for (int index = 0; index < 8; index++) {
            corner.setX((index & 1) == 0 ? sourceBox.minX() : sourceBox.maxX());
            corner.setY((index & 2) == 0 ? sourceBox.minY() : sourceBox.maxY());
            corner.setZ((index & 4) == 0 ? sourceBox.minZ() : sourceBox.maxZ());
            transform(corner, corner, spin, ceilingHang, translateOffset);
            int x = (int) Math.round(corner.getX());
            int y = (int) Math.round(corner.getY());
            int z = (int) Math.round(corner.getZ());
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        return new Box(minX, maxX, minY, maxY, minZ, maxZ);
    }

    public record Targets(int[] x, int[] y, int[] z, int[] sources) {
        public int count() {
            return sources.length;
        }
    }

    private record Box(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        private boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        private long cell(int x, int y, int z) {
            return ((long) (x - minX) * (maxY - minY + 1) + (y - minY)) * (maxZ - minZ + 1) + (z - minZ);
        }
    }
}
