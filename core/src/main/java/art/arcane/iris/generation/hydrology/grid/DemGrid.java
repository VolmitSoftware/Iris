package art.arcane.iris.generation.hydrology.grid;

import java.util.Objects;

public final class DemGrid {
    private final int originX;
    private final int originZ;
    private final int cell;
    private final int width;
    private final int depth;
    private final float[] values;

    public DemGrid(int originX, int originZ, int cell, int width, int depth) {
        if (cell <= 0 || width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Invalid grid cell=" + cell + " width=" + width + " depth=" + depth);
        }
        this.originX = originX;
        this.originZ = originZ;
        this.cell = cell;
        this.width = width;
        this.depth = depth;
        this.values = new float[Math.multiplyExact(width, depth)];
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int cell() {
        return cell;
    }

    public int originX() {
        return originX;
    }

    public int originZ() {
        return originZ;
    }

    public float get(int i, int j) {
        return values[index(i, j)];
    }

    public void set(int i, int j, float v) {
        values[index(i, j)] = v;
    }

    public int blockX(int index) {
        return originX + (Objects.checkIndex(index, values.length) % width) * cell;
    }

    public int blockZ(int index) {
        return originZ + (Objects.checkIndex(index, values.length) / width) * cell;
    }

    public int index(int i, int j) {
        return Objects.checkIndex(j, depth) * width + Objects.checkIndex(i, width);
    }

    public float[] values() {
        return values;
    }
}
