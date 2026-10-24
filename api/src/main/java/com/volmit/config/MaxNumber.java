package com.volmit.config;

import java.lang.annotation.*;

/** Inclusive numeric upper bound. Enforcement belongs to the configuration loader. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MaxNumber {
    double value();
}
