package com.volmit.config;

import java.lang.annotation.*;

/** Says what an option's text names: an entry of a platform registry, {@code @Ref(Reference.BLOCK) String block}, or
 * a resource of the pack by its key, {@code @Ref(kind = LootTable.class) String table}, where {@code kind} is the type
 * of a {@link com.volmit.resource.ResourceKind}: a host kind, the constants of
 * {@link com.volmit.resource.PackResources}, or a kind some module of the pack registers. Studio then offers the
 * registry's entries or the pack's keys of that kind. Give exactly one of the two. Allowed on {@code String} and
 * {@link com.volmit.resource.ResourceId} options and on lists, sets and arrays of them, where it applies to every
 * element; a {@code ResourceId} option naming a pack resource holds the id the pack gives the key it writes.
 * Anything else fails the pack load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Ref {
    /** The platform registry the text names; give one, or none when {@link #kind} is given. */
    Reference[] value() default {};

    /** The type of the resource kind the text names by key; {@code void.class}, the default, when {@link #value} is given. */
    Class<?> kind() default void.class;
}
