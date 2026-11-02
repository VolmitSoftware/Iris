package art.arcane.iris.testsupport;

import art.arcane.iris.generation.hydrology.grid.DemGrid;

public final class SeaCells {
    private SeaCells() {
    }

    public static boolean[] atOrBelow(DemGrid filled, int seaLevel) {
        boolean[] sea = new boolean[filled.values().length];
        for (int cell = 0; cell < sea.length; cell++) {
            sea[cell] = filled.values()[cell] <= seaLevel;
        }
        return sea;
    }
}
