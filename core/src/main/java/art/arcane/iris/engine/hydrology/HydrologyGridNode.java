package art.arcane.iris.engine.hydrology;

record HydrologyGridNode(
        int index,
        int gridX,
        int gridZ,
        int x,
        int z,
        long id,
        HydrologyTerrainSample terrain
) {
    HydrologyPoint naturalPoint() {
        return new HydrologyPoint(x, terrain.naturalHeight(), z);
    }
}
