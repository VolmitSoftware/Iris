package art.arcane.iris.generation.hydrology;

public final class HydrologyHash {
    private HydrologyHash() {
    }

    public static long mix(long seed, long... values) {
        long mixed = avalanche(seed ^ 0x9e3779b97f4a7c15L);
        for (long value : values) {
            mixed = avalanche(mixed ^ avalanche(value + 0x9e3779b97f4a7c15L));
        }
        return mixed;
    }

    public static long text(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return avalanche(hash);
    }

    public static double unit(long value) {
        return (avalanche(value) >>> 11) * 0x1.0p-53;
    }

    public static int between(long value, int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum cannot exceed maximum.");
        }
        long span = (long) maximum - minimum + 1L;
        return minimum + (int) Long.remainderUnsigned(avalanche(value), span);
    }

    private static long avalanche(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
