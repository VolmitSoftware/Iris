package com.volmit.config;

import java.lang.annotation.*;

/** Human-readable documentation for a settings type or field. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Desc {
    String value();
}
