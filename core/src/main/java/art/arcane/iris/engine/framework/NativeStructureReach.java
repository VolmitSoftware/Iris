package art.arcane.iris.engine.framework;

/**
 * How far around a start's pieces a module changes the terrain: the area the platform's own terrain adaptation of the
 * structure covers ({@link #platform()}), or a number of blocks around the pieces. The platform code hands the start
 * to every chunk any module's reach covers.
 */
public record NativeStructureReach(boolean platform, int blocks) {
    public static final NativeStructureReach PLATFORM = new NativeStructureReach(true, 0);
}
