package art.arcane.iris.world.entity;

import art.arcane.volmlib.util.math.RNG;

public final class EntitySpawnSeed {
    private static final long CHUNK_SALT = 0xA0761D6478BD642FL;
    private static final long MARKER_SALT = 0xE7037ED1A0B428DBL;
    private static final long ENTITY_SALT = 0x8EBC6AF09C88C6E3L;

    private EntitySpawnSeed() {
    }

    public static RNG chunk(long entitySeed, int x, int z) {
        long seed = mix(entitySeed ^ CHUNK_SALT);
        seed = mix(seed ^ (long) x * 0xD6E8FEB86659FD93L);
        return new RNG(mix(seed ^ (long) z * 0x8CB92BA72F3D8DD7L));
    }

    public static RNG marker(long entitySeed, int x, int y, int z) {
        long seed = mix(entitySeed ^ MARKER_SALT);
        seed = mix(seed ^ (long) x * 0xD6E8FEB86659FD93L);
        seed = mix(seed ^ (long) y * 0xA5A3564E27F886A7L);
        return new RNG(mix(seed ^ (long) z * 0x8CB92BA72F3D8DD7L));
    }

    public static RNG entity(long batchSeed, int ordinal) {
        return new RNG(mix(batchSeed ^ ENTITY_SALT ^ (long) ordinal * 0x9E3779B97F4A7C15L));
    }

    private static long mix(long seed) {
        seed = (seed ^ seed >>> 30) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ seed >>> 27) * 0x94D049BB133111EBL;
        return seed ^ seed >>> 31;
    }
}
