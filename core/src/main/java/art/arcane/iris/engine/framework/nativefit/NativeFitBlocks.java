package art.arcane.iris.engine.framework.nativefit;

/**
 * The live blocks of the chunk the platform decorates, in the platform's own block states {@code S}, at world
 * coordinates, and the questions a structure fit asks about them.
 */
public interface NativeFitBlocks<S> {
    S get(int x, int y, int z);

    /** Replaces the block without updating neighbours, the way the platform's decoration writes. */
    void set(int x, int y, int z, S state);

    /** The state's key with its properties, such as {@code minecraft:oak_stairs[facing=east,half=bottom]}. */
    String key(S state);

    /** The state {@link #key} names. */
    S state(String key);

    boolean is(S state, NativeFitTrait trait);

    /** Whether the state is in the platform's block tag {@code tag}, such as {@code minecraft:logs}. */
    boolean inTag(S state, String tag);

    /** Whether the block at the position can carry a full block on its face named {@code face} ({@code UP} ...). */
    boolean sturdy(int x, int y, int z, String face);

    /** The block of the fluid the state holds; air when it holds none. */
    S fluid(S state);
}
