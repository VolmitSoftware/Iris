package art.arcane.iris.generation.hydrology.water;

public record Pond(
        long id,
        PondKind kind,
        int level,
        int floor,
        int spillX,
        int spillZ,
        float spillHeight,
        PondMask mask,
        long outletReachId,
        int damRaise,
        String fluid,
        int floorX,
        int floorZ
) {
    public Pond withOutletReachId(long outletReachId) {
        return new Pond(id, kind, level, floor, spillX, spillZ, spillHeight, mask, outletReachId, damRaise, fluid, floorX, floorZ);
    }
}
