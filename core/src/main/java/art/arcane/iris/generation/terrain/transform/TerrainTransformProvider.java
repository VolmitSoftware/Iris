package art.arcane.iris.generation.terrain.transform;

import java.util.Map;

public interface TerrainTransformProvider {
    String id();

    String version();

    TerrainTransformer create(Map<String, String> settings);
}
