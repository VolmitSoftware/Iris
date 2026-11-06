package com.volmit.config;

import java.lang.annotation.*;

/** Says that an option's text names a resource of the pack by its key, {@code @ResourceRef(LootTable.class) String table},
 * where the value is the type of a {@link com.volmit.resource.ResourceKind}: a host kind, the constants of
 * {@link com.volmit.resource.PackResources}, or a kind some module of the pack registers. Studio then offers the pack's
 * keys of that kind. Allowed on {@code String} and {@link com.volmit.resource.ResourceId} options and on lists, sets and
 * arrays of them, where it applies to every element; a {@code ResourceId} option holds the id the pack gives the key it
 * writes. An option with both this and {@link Ref}, or with either on any other type, fails the pack load.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ResourceRef {
    Class<?> value();
}
