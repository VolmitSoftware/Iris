package art.arcane.iris.generation.hydrology.surface;

public record ChannelProfile(double[] halfWidth, double[] depth, double[] bankMultiplier) {
    public int size() {
        return halfWidth.length;
    }

    public double collar(int station, double roughness) {
        return halfWidth[station] * (1D + roughness) + 2D;
    }
}
