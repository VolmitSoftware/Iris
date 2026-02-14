package art.arcane.iris.engine.object.annotations;

import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, TYPE, FIELD})
public @interface RegistryListFunction {
    Class<? extends ListFunction<KList<String>>> value();
}
