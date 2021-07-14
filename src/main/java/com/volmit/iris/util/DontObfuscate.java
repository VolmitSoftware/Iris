package com.volmit.iris.util;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({FIELD, TYPE, CONSTRUCTOR, METHOD})
public @interface DontObfuscate {

}
