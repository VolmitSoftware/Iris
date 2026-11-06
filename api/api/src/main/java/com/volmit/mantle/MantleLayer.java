package com.volmit.mantle;

import com.volmit.geometry.BlockBox;

/** A registered layer bounded by its owning Mantle view and sharing its lifetime.
 * Values must be non-null and treated as immutable. Wrong value types are rejected with
 * IllegalArgumentException; null arguments with NullPointerException.
 */
public interface MantleLayer<T> {
    /** Returns null on absence, without allocating a storage section. */
    T get(int x, int y, int z);

    void set(int x, int y, int z, T value);

    /** Removing an absent entry is a no-op. */
    void remove(int x, int y, int z);

    /** Visits stored entries only, in ascending x, then y, then z order.
     * Area must be fully within bounds (no silent clipping). Callbacks must not mutate
     * this layer directly. An edit may buffer changes while visiting its parent layer.
     * Callback exceptions propagate and stop traversal. No snapshot allocation is required.
     */
    void visit(BlockBox area, MantleVisitor<T> visitor);

    /** Removes all entries in the fully bounded area; other layers are untouched. */
    void clear(BlockBox area);
}
