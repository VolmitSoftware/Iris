package art.arcane.iris.generation.hydrology.grid;

public final class CellHash {
    private static final long GOLDEN = 0x9e3779b97f4a7c15L;

    private CellHash() {
    }

    public static long mix(long seed, long salt, int x, int z) {
        long mixed = avalanche(seed ^ GOLDEN);
        mixed = avalanche(mixed ^ avalanche(salt + GOLDEN));
        mixed = avalanche(mixed ^ avalanche(x + GOLDEN));
        return avalanche(mixed ^ avalanche(z + GOLDEN));
    }

    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private static long avalanche(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
