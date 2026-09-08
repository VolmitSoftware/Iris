package art.arcane.iris.engine.hydrology;

record FootprintRasterStencil(
        int[] deltaXs,
        int[] deltaZs,
        double[] distances
) {
    int size() {
        return deltaXs.length;
    }
}
