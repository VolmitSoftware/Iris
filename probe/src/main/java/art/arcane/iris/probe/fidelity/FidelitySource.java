package art.arcane.iris.probe.fidelity;

public interface FidelitySource extends AutoCloseable {
    String label();

    int minY();

    int maxY();

    FidelityChunk sample(int chunkX, int chunkZ) throws Exception;
}
