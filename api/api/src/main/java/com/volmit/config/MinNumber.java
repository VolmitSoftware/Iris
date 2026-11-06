package com.volmit.config;

import java.lang.annotation.*;

/** Inclusive numeric lower bound. Enforcement belongs to the configuration loader. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MinNumber {
    double value();
}
