package com.volmit;

import com.volmit.config.PackSettings;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;

/** What a module does when the host loads a pack and starts or stops a world with it. Every hook does nothing by
 * default. Modules register it with {@link FeatureRegistry#registerLifecycle}; a module class may implement it and
 * register itself.
 *
 * <p>Order: {@link #packLoaded} for a pack snapshot before the first world using it starts, then
 * {@link #worldStarted} before that world generates anything, and {@link #worldStopped} when it stops. A hotload, or a
 * reload of the pack's resources while worlds run, stops each world of the old snapshot, loads the new snapshot and
 * starts its worlds again, with empty state (see {@link PackWorld}). The host calls the hooks of a pack's modules in
 * module id order and stops them in reverse, one hook at a time, on the thread that starts or stops the world.
 *
 * <p>Hooks keep what they build in the world's state ({@link PackWorld#state}), never in fields: a module instance may
 * serve several packs and worlds at once.
 */
public interface ModuleLifecycle {
    /** The pack's modules are installed and its resources can be read. {@code settings} holds every entry of every
     * dimension, region and biome of the pack, as validators see them. A hook that throws stops the pack's worlds from
     * starting, naming its module.
     */
    default void packLoaded(PackResources resources, PackSettings settings) {}

    /** {@code world} starts, before it generates anything: the place to build state eagerly, such as caches a world
     * would otherwise build on its first chunk. A hook that throws stops the world from starting, naming its module; the
     * modules that had started it already get {@link #worldStopped}.
     */
    default void worldStarted(PackWorld world) {}

    /** {@code world} stops: it generates nothing more with this pack snapshot. Its state is still readable here and is
     * closed afterwards. The host runs every module's hook even when one throws, then reports the failures.
     */
    default void worldStopped(PackWorld world) {}
}
