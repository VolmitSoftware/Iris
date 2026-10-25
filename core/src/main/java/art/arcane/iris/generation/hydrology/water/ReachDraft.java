package art.arcane.iris.generation.hydrology.water;

final class ReachDraft {
    final ReachTracer.Chain chain;
    final long id;
    final HydrologyTier tier;
    final float catchment;
    final int endLevel;
    final float[] bank;
    float[] xs;
    float[] zs;
    float[] catchments;
    boolean[] ocean;
    boolean[] outletZone;
    int[] target;
    int[] grade;
    float[] halfWidth;
    float[] depth;
    ReachStyle[] style;
    int floor;
    int carry = Integer.MAX_VALUE;
    boolean outlet;
    int downstream = -1;
    int[] incoming = new int[0];
    float maxHalfWidth;

    ReachDraft(ReachTracer.Chain chain, long id, HydrologyTier tier, float catchment, int endLevel, ReachFitter.Fitted fitted) {
        this.chain = chain;
        this.id = id;
        this.tier = tier;
        this.catchment = catchment;
        this.endLevel = endLevel;
        this.bank = fitted.bank();
        this.xs = fitted.xs();
        this.zs = fitted.zs();
        this.catchments = fitted.catchment();
        this.ocean = fitted.ocean();
        this.ocean[xs.length - 1] |= chain.end() == ReachTracer.End.OCEAN;
        this.target = new int[xs.length];
        for (int point = 0; point < xs.length; point++) {
            target[point] = (int) Math.floor(fitted.ground()[point]) - tier.inset();
        }
    }

    int last() {
        return grade[grade.length - 1];
    }
}
