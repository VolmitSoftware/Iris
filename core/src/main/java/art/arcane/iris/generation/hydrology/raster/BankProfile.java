package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;

final class BankProfile {
    private static final double BENCH = 0.5D;
    private static final double WALL = 0.2D;
    private static final double TERRACE = 3D;

    private BankProfile() {
    }

    static double shape(IrisBankStyle style, double u) {
        double x = Math.clamp(u, 0D, 1D);
        return switch (style) {
            case SMOOTH, TERRACED -> x * x * (3D - 2D * x);
            case LINEAR -> x;
            case CONCAVE -> 1D - (1D - x) * (1D - x);
            case CLIFF -> Math.clamp((x - BENCH) / WALL, 0D, 1D);
        };
    }

    static int cut(IrisBankStyle style, int surface, int ground, double u, double cone, double noise) {
        double rise = (ground - surface) * shape(style, u);
        double height = switch (style) {
            case SMOOTH, LINEAR, CONCAVE -> surface + Math.max(rise, cone) + noise;
            case TERRACED -> u >= 1D ? ground : surface + Math.floor(Math.max(rise, cone) / TERRACE) * TERRACE;
            case CLIFF -> surface + rise;
        };
        return (int) Math.clamp((long) Math.floor(height), surface, ground);
    }

    static int levee(IrisBankStyle style, int surface, int ground, double u, double noise, int maxFill) {
        int crest = surface + 1;
        double fall = (crest - ground) * shape(style, u);
        double height = switch (style) {
            case SMOOTH, LINEAR, CONCAVE -> crest - fall + noise;
            case TERRACED -> u >= 1D ? ground : crest - Math.floor(fall / TERRACE) * TERRACE;
            case CLIFF -> crest - fall;
        };
        return (int) Math.clamp((long) Math.ceil(height), ground, ground + maxFill);
    }
}
