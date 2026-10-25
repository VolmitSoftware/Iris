package art.arcane.iris.generation.hydrology.grid;

public record Basin(int id, int floorIndex, int spillIndex, float spillHeight, float maxDepth, int[] cells) {
    public int span(int width) {
        int minI = Integer.MAX_VALUE;
        int maxI = Integer.MIN_VALUE;
        int minJ = Integer.MAX_VALUE;
        int maxJ = Integer.MIN_VALUE;
        for (int cell : cells) {
            minI = Math.min(minI, cell % width);
            maxI = Math.max(maxI, cell % width);
            minJ = Math.min(minJ, cell / width);
            maxJ = Math.max(maxJ, cell / width);
        }
        return Math.max(maxI - minI + 1, maxJ - minJ + 1);
    }
}
