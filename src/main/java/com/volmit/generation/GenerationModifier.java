package com.volmit.generation;

import com.volmit.hunk.Hunk;

/** Bound to one configured feature instance.
 * Calls may occur concurrently for different volumes. Keep settings read-only and temporary
 * state local to modify(). Exceptions propagate: the host must not publish partial output.
 */
public abstract class GenerationModifier<T> {
    /** Stable, nonblank namespaced modifier identity, independent of feature identity.
     * Configured instances of the same modifier share this ID and dependency declaration.
     * Different modifier implementations must not claim the same ID.
     */
    public abstract String id();

    /** Stable requirements read at pack load, not per invocation. Constructors must not
     * depend on prerequisite modifiers having run. Ordering does not guarantee that
     * a prerequisite changed blocks at the current location.
     */
    public Dependencies depends() {
        return Dependencies.none();
    }

    /** Nonnegative horizontal reach in blocks beyond the output footprint; zero by default.
     * Read once per configured instance at pack load. The host must prepare this area and
     * required predecessor work before invocation, bound vertical access to the world, and
     * serialize overlapping writes. It must reject negative or unsupported reach, not clip it.
     */
    public int mantleRadius() {
        return 0;
    }

    public abstract void modify(GenerationContext context, Hunk<T> output);
}
