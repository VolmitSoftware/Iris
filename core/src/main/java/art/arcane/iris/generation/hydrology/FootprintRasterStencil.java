package art.arcane.iris.generation.hydrology;

record FootprintRasterStencil(
        int[] deltaXs,
        int[] deltaZs,
        double[] distances
) {
    int size() {
        return deltaXs.length;
    }
}
