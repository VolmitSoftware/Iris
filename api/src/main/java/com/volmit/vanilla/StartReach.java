package com.volmit.vanilla;

/** How far around its pieces a module changes the terrain for a start, so the platform hands the start to every chunk
 * the change reaches: {@link #PLATFORM} for the area the platform's own terrain adaptation of the structure covers, or
 * a number of blocks around the pieces. The host covers the reaches of every provider together.
 */
public record StartReach(boolean platform, int blocks) {
    /** The area the platform's own terrain adaptation of the structure covers (its pieces when it adapts none). */
    public static final StartReach PLATFORM = new StartReach(true, 0);

    public StartReach {
        if (blocks < 0) {
            throw new IllegalArgumentException("A start's reach cannot be negative: " + blocks);
        }
        if (platform && blocks != 0) {
            throw new IllegalArgumentException("The platform's reach has no block count");
        }
    }

    /** {@code blocks} around the start's pieces, horizontally; 0 for the pieces alone. */
    public static StartReach blocks(int blocks) {
        return new StartReach(false, blocks);
    }
}
