package com.volmit.config;

import java.lang.annotation.*;

/** Declares that a field must be supplied in configuration. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Required {}
