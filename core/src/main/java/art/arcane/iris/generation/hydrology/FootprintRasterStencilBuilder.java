package art.arcane.iris.generation.hydrology;

import java.util.Arrays;

final class FootprintRasterStencilBuilder {
    private int[] deltaXs;
    private int[] deltaZs;
    private double[] distances;
    private int size;

    FootprintRasterStencilBuilder() {
        this.deltaXs = new int[64];
        this.deltaZs = new int[64];
        this.distances = new double[64];
    }

    void add(int deltaX, int deltaZ, double distance) {
        if (size == deltaXs.length) {
            int expandedSize = Math.multiplyExact(size, 2);
            deltaXs = Arrays.copyOf(deltaXs, expandedSize);
            deltaZs = Arrays.copyOf(deltaZs, expandedSize);
            distances = Arrays.copyOf(distances, expandedSize);
        }
        deltaXs[size] = deltaX;
        deltaZs[size] = deltaZ;
        distances[size] = distance;
        size++;
    }

    FootprintRasterStencil build() {
        return new FootprintRasterStencil(
                Arrays.copyOf(deltaXs, size),
                Arrays.copyOf(deltaZs, size),
                Arrays.copyOf(distances, size)
        );
    }
}
