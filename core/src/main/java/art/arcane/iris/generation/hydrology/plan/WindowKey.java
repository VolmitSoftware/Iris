package art.arcane.iris.generation.hydrology.plan;

public record WindowKey(int wx, int wz) {
    public static final int SIZE = 512;
    private static final int WINDOWS_PER_REGION = RegionKey.SIZE / SIZE;

    public static WindowKey containing(int blockX, int blockZ) {
        return new WindowKey(Math.floorDiv(blockX, SIZE), Math.floorDiv(blockZ, SIZE));
    }

    public RegionKey region() {
        return new RegionKey(Math.floorDiv(wx, WINDOWS_PER_REGION), Math.floorDiv(wz, WINDOWS_PER_REGION));
    }
}
