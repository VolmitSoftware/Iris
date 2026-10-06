package art.arcane.iris.engine.object.annotations;

import art.arcane.iris.engine.framework.ListFunction;
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
