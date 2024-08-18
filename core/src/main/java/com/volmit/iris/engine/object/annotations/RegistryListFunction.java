package com.volmit.iris.engine.object.annotations;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.ListFunction;
import com.volmit.iris.util.collection.KList;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, TYPE, FIELD})
public @interface RegistryListFunction {
    Class<? extends ListFunction<IrisData, KList<String>>> value();
}
