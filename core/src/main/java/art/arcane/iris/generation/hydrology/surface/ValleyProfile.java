package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;

public record ValleyProfile(
        int[] head,
        int[] crossMin,
        int[] crossMax,
        int[] centerNatural,
        int exposedStations,
        HydrologyCandidateRejection rejection,
        int rejectionDetail
) {
    public boolean accepted() {
        return rejection == null;
    }

    public static ValleyProfile fromHeads(int[] head, int exposedStations) {
        return new ValleyProfile(head, new int[0], new int[0], new int[0], exposedStations, null, 0);
    }

    /** {@code detail} names the measure that failed: exposed stations, required cut, or missing head. */
    public static ValleyProfile rejected(HydrologyCandidateRejection rejection, int detail) {
        return new ValleyProfile(new int[0], new int[0], new int[0], new int[0], 0, rejection, detail);
    }
}
