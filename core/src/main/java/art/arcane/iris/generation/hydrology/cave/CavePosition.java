package art.arcane.iris.generation.hydrology.cave;

public record CavePosition(int x, int y, int z) {
    public CavePosition offset(int dx, int dy, int dz) {
        return new CavePosition(x + dx, y + dy, z + dz);
    }

    @Override
    public int hashCode() {
        int hash = 0x811C9DC5;
        hash = (hash ^ x) * 0x01000193;
        hash = (hash ^ y) * 0x01000193;
        hash = (hash ^ z) * 0x01000193;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        return hash ^ hash >>> 16;
    }
}
