package art.arcane.iris.pack.schema.annotation;

import art.arcane.iris.generation.runtime.ListFunction;
import art.arcane.volmlib.util.collection.KList;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({PARAMETER, TYPE, FIELD})
public @interface RegistryListFunction {
    Class<? extends ListFunction<KList<String>>> value();
}
