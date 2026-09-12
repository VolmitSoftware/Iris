package art.arcane.iris.generation.hydrology;

public record HydrologyPoint(int x, int y, int z) {
    public long packedColumn() {
        return RiverFootprint.pack(x, z);
    }

    public long distanceSquared2D(HydrologyPoint other) {
        long deltaX = (long) x - other.x;
        long deltaZ = (long) z - other.z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }
}
