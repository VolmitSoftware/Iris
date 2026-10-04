package art.arcane.iris.engine.object.placement;

/**
 * Registers work during runtime construction. Functions are shared by concurrent placements:
 * implementations must receive placement state at execution time, never capture it at installation.
 */
public interface PlacementModule {
    void install(PlacementLifecycleBuilder lifecycle);
}
