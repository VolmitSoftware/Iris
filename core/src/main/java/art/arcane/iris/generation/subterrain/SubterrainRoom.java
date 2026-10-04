package art.arcane.iris.generation.subterrain;

public record SubterrainRoom(String featureId, String biome, IrisSubterrainFamily family,
                             int centerX, int centerY, int centerZ, int pathX, int pathY, int pathZ,
                             int floorY, int ceilingY, double boundaryDistance, int fluidHeadY,
                             boolean reservedPassage, boolean reservedSolid, SubterrainCell.Kind occupancy) {
    public int vaultHeight() {
        return Math.max(0, ceilingY - floorY - 1);
    }
}
