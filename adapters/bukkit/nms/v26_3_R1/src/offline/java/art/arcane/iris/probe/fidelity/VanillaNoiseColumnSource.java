package art.arcane.iris.probe.fidelity;

import art.arcane.iris.probe.WorldgenExportSampler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class VanillaNoiseColumnSource implements ColumnSource {
    private final WorldgenExportSampler sampler;
    private final Map<String, String> loadKeyByBiomeId;
    private final int minY;
    private final int maxY;
    private final int seaLevel;

    public VanillaNoiseColumnSource(WorldgenExportSampler sampler, Map<String, String> loadKeyByBiomeId) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.loadKeyByBiomeId = Map.copyOf(loadKeyByBiomeId);
        this.minY = sampler.minY();
        this.maxY = sampler.minY() + sampler.height();
        this.seaLevel = sampler.seaLevel();
    }

    @Override
    public String label() {
        return "vanilla:26.3";
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public FidelityColumn column(int x, int z) {
        int floor = sampler.floorHeight(x, z);
        int terrainY = floor - 1;
        boolean wet = floor < seaLevel && sampler.baseHeight(x, z) > floor;
        return new FidelityColumn(loadKey(x, Math.max(minY, terrainY), z), terrainY, wet);
    }

    @Override
    public void close() throws IOException {
        sampler.close();
    }

    private String loadKey(int x, int y, int z) {
        String biomeId = sampler.biome(x, y, z);
        String loadKey = loadKeyByBiomeId.get(biomeId);
        if (loadKey == null) {
            throw new IllegalStateException("Exported biome " + biomeId + " at " + x + "," + y + "," + z
                    + " has no Iris load key in the export result");
        }
        return loadKey;
    }
}
