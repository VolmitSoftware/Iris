package art.arcane.iris.engine.object.terrain.floating;

/** Builds {@link FloatingIslandSample}s for tests outside the floating package. */
public final class FloatingIslandSamples {
    private FloatingIslandSamples() {
    }

    public static FloatingIslandSample of(int islandBaseY, int thickness, int topIdx, int solidCount, boolean[] solidMask) {
        return FloatingIslandSample.constructForTest(islandBaseY, thickness, topIdx, solidCount, solidMask);
    }

    public static FloatingIslandSample of(ResolvedFloatingChild entry, int islandBaseY, int thickness, int topIdx, int solidCount,
                                          boolean[] solidMask, ResolvedFloatingChild[] entryMask) {
        return FloatingIslandSample.constructForTest(entry, islandBaseY, thickness, topIdx, solidCount, solidMask, entryMask);
    }
}
