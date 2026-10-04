package art.arcane.iris.generation.subterrain;

public record SubterrainCell(Kind kind, String material, SubterrainRoom room) {
    public static final SubterrainCell OUTSIDE = new SubterrainCell(Kind.OUTSIDE, "", null);

    public boolean owned() {
        return kind != Kind.OUTSIDE;
    }

    public boolean occupied() {
        return kind == Kind.AIR || fluid();
    }

    public boolean solid() {
        return kind == Kind.SOLID;
    }

    public boolean carve() {
        return occupied();
    }

    public boolean fluid() {
        return kind == Kind.WATER || kind == Kind.LAVA;
    }

    public enum Kind {
        OUTSIDE, SOLID, AIR, WATER, LAVA
    }
}
