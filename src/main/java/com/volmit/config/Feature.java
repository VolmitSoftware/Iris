package com.volmit.config;

import java.lang.annotation.*;

/** Identity and JSON attachment for an IrisFeature configuration class. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Feature {
    String id();
    String name();
    String field();
    com.volmit.FeatureAttachment.Target[] targets();
}
