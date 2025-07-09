package com.volmit.iris.engine.mantle;

import com.volmit.iris.util.mantle.MantleFlag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ComponentFlag {
    MantleFlag value();
}
