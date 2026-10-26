package art.arcane.iris.structure.object;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.RNG;

/** Snow uses only successful object writes, and follows stilt or vacuum work. */
final class PlacementModuleSnow implements PlacementModule {
    static final PlacementLifecycleDependency COLLECT = new PlacementLifecycleDependency("snow.collect");
    static final PlacementLifecycleDependency FINISH = new PlacementLifecycleDependency("snow.finish");
    private final PlacementLifecycleDependency after;

    PlacementModuleSnow(PlacementLifecycleDependency after) {
        this.after = after;
    }

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(COLLECT, PlacementStage.BLOCK_WRITTEN, (context, state, block) -> {
            if (!(state.config.getSnow() > 0)) {
                return;
            }
            if (state.snowHeights == null) {
                state.snowHeights = new KMap<>();
            }
            Position2 position = new Position2(block.x(), block.z());
            Integer current = state.snowHeights.get(position);
            if (current == null || current < block.y()) {
                state.snowHeights.put(position, block.y());
            }
        });
        lifecycle.add(FINISH, PlacementStage.AFTER_BLOCKS, (context, state, ignored) -> {
            if (state.snowHeights == null) {
                return;
            }
            RNG rng = context.rng.nextParallelRNG(3468854);
            var columns = state.snowHeights.k();
            columns.sort(java.util.Comparator.comparingInt(Position2::getX).thenComparingInt(Position2::getZ));
            for (Position2 position : columns) {
                int height = rng.i(0, (int) (state.config.getSnow() * 7));
                state.placer.set(position.getX(), state.snowHeights.get(position) + 1, position.getZ(),
                        IrisObject.States.snowLayer(Math.max(Math.min(height, 7), 0)));
            }
        }, COLLECT, after);
    }
}
