package art.arcane.iris.nativegen;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.OptionalInt;
import java.util.function.Supplier;

public final class NativeTerrainHeightCache {
    private static final int MAXIMUM_CACHED_QUERIES = 65_536;

    private final Object2IntLinkedOpenHashMap<Query> heights = new Object2IntLinkedOpenHashMap<>();

    public OptionalInt resolvedHeight(Query query, Supplier<OptionalInt> resolver) {
        synchronized (heights) {
            if (heights.containsKey(query)) {
                return OptionalInt.of(heights.getAndMoveToFirst(query));
            }
        }
        OptionalInt resolved = resolver.get();
        if (resolved.isEmpty()) {
            return resolved;
        }
        int height = resolved.getAsInt();
        synchronized (heights) {
            heights.putAndMoveToFirst(query, height);
            while (heights.size() > MAXIMUM_CACHED_QUERIES) {
                heights.removeLastInt();
            }
        }
        return resolved;
    }

    public void evictRuntime(int runtimeId) {
        synchronized (heights) {
            heights.keySet().removeIf(query -> query.runtimeId() == runtimeId);
        }
    }

    public record Query(int runtimeId, int x, int z, Heightmap.Types type, int minimumY, int height) {
    }
}
