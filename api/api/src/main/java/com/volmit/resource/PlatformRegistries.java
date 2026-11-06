package com.volmit.resource;

import com.volmit.config.Reference;

import java.util.Set;

/** The ids the running platform has registered, as the server has them, data packs and mods included: the entries a
 * {@link com.volmit.config.Ref} option names. Read during validation; ids stay valid until the platform reloads them.
 */
public interface PlatformRegistries {
    /** Every id the platform registers under {@code registry}, such as {@code minecraft:village_plains} for
     * {@link Reference#STRUCTURE}; empty for a reference that is no platform registry ({@link Reference#STRUCTURE_PREFIX},
     * {@link Reference#SPECIAL_ENTITY}, {@link Reference#NOISE_STYLE}) or one the platform lacks.
     */
    Set<ResourceId> ids(Reference registry);
}
