package com.volmit.config;

import java.lang.annotation.*;

/** Says what an option's text names, such as {@code @Ref(Reference.BLOCK) String block}. Allowed on
 * {@code String} and {@link com.volmit.resource.ResourceId} options and on lists, sets and arrays of
 * them, where it applies to every element. Anywhere else it fails the pack load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Ref {
    Reference value();
}
