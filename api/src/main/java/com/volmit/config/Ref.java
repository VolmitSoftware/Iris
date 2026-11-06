package com.volmit.config;

import java.lang.annotation.*;

/** Says that an option's text names an entry of a platform registry, {@code @Ref(Reference.BLOCK) String block}, so
 * studio offers the registry's entries. Allowed on {@code String} and {@link com.volmit.resource.ResourceId} options
 * and on lists, sets and arrays of them, where it applies to every element. A resource of the pack is named with
 * {@link ResourceRef} instead; an option with both, or with either on any other type, fails the pack load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Ref {
    Reference value();
}
