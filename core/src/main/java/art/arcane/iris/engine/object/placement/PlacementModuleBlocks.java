package art.arcane.iris.engine.object.placement;

/** Owns block-pass execution and the object read lock. */
final class PlacementModuleBlocks implements PlacementModule {
    static final PlacementLifecycleDependency COMPLETE = new PlacementLifecycleDependency("blocks.complete");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(COMPLETE, PlacementStage.BLOCKS, (context, state, plan) -> {
            context.self.readLock.lock();
            try {
                new ObjectBlockPass(context, state, plan, context.listener).process();
            } finally {
                context.self.readLock.unlock();
            }
        }, PlacementModuleBore.BORE);
    }
}
