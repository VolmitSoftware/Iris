package art.arcane.iris.structure.object;

/**
 * Registers work during runtime construction. Functions are shared by concurrent placements:
 * implementations must receive placement state at execution time, never capture it at installation.
 */
public interface PlacementModule {
    void install(PlacementLifecycleBuilder lifecycle);
}
