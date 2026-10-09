package com.volmit.config;

import java.lang.annotation.*;

/** Inclusive lower bound on the length of a list, set or array option, such as a palette that needs a block. Studio
 * shows it ({@code minItems}); like {@link MinNumber}, enforcement belongs to the configuration loader.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MinItems {
    int value();
}
