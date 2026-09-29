package art.arcane.iris.world.pregen;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class PregenInFlightRequests {
    private final ConcurrentHashMap<Long, PregenListener> requests = new ConcurrentHashMap<>();

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    public boolean add(long key, PregenListener listener) {
        return requests.putIfAbsent(key, Objects.requireNonNull(listener, "listener")) == null;
    }

    public PregenListener settle(long key) {
        return requests.remove(key);
    }

    public Map<Long, PregenListener> drain() {
        HashMap<Long, PregenListener> drained = new HashMap<>();
        Iterator<Map.Entry<Long, PregenListener>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, PregenListener> entry = iterator.next();
            if (requests.remove(entry.getKey(), entry.getValue())) {
                drained.put(entry.getKey(), entry.getValue());
            }
        }
        return drained;
    }

    public int size() {
        return requests.size();
    }
}
