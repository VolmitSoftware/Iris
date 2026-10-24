package com.volmit.config;

import java.lang.annotation.*;

/** Says that an option names a resource of the module resource kind read into {@code value}, by its key in the
 * pack, such as {@code @ResourceRef(Pool.class) String startPool = "castle/start"}. Studio then offers the keys
 * of that kind's files. Allowed on {@code String} options and lists, sets and arrays of them; the class must be
 * the type of a resource kind some module of the pack registers. Anywhere else it fails the pack load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ResourceRef {
    Class<?> value();
}
