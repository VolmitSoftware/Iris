package com.volmit.vanilla;

import com.volmit.resource.ResourceId;

/** A module's say over the platform's own structures. Modules register it with
 * {@link com.volmit.FeatureRegistry#registerVanillaStructureHook}; the host asks every hook of the pack before the
 * platform starts one of its structures in a world, and when it locates one.
 *
 * <p>The host asks once per world, structure and pack snapshot and keeps the answer, so a hook decides from the
 * pack (its settings and resources) and never from where a start would be. Rules of several modules combine with
 * {@link VanillaStructureRule#and}: any module may block the platform's starts.
 */
@FunctionalInterface
public interface VanillaStructureHook {
    /** What this module wants for the platform structure {@code structure}, such as {@code minecraft:village_plains},
     * in the world {@code context} describes. {@link VanillaStructureRule#GENERATE} when the module has no part in it.
     */
    VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context);
}
