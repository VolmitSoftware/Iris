package com.volmit.config;

import java.lang.annotation.*;

/** Identifies a reusable settings snippet. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Snippet {
    String value();
}
