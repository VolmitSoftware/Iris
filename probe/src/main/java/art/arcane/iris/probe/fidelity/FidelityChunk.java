package art.arcane.iris.probe.fidelity;

public interface FidelityChunk {
    int surfaceY(int localX, int localZ);

    String surfaceBiome(int localX, int localZ);

    boolean open(int localX, int y, int localZ);

    boolean fluid(int localX, int y, int localZ);

    String block(int localX, int y, int localZ);

    default boolean lava(int localX, int y, int localZ) {
        String id = block(localX, y, localZ);
        return id != null && FidelityBlocks.lava(id);
    }

    default boolean terrainMass(int localX, int y, int localZ) {
        return true;
    }

    default boolean river(int localX, int localZ) {
        return false;
    }

    default boolean wallsKnown() {
        return false;
    }

    default boolean wall(int localX, int y, int localZ) {
        return false;
    }
}
