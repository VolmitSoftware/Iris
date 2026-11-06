package com.volmit.host;

import com.volmit.config.Feature;
import java.lang.annotation.IncompleteAnnotationException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** A settings class whose {@code @Feature} lacks an element the API requires, such as a class compiled before
 * {@code key} existed. The load names the module around it ({@link LoadedModules}).
 */
final class IncompleteFeatureException extends IllegalArgumentException {
    private static final String KEY = "key";

    private IncompleteFeatureException(String message, Throwable cause) {
        super(message, cause);
    }

    static IncompleteFeatureException of(Class<?> type, Feature feature, IncompleteAnnotationException missing) {
        if (!KEY.equals(missing.elementName())) {
            return new IncompleteFeatureException("The @Feature of settings class " + type.getName() + " has no "
                    + missing.elementName() + "; add " + missing.elementName() + " = ... to it and rebuild the jar", missing);
        }
        return new IncompleteFeatureException("The @Feature of settings class " + type.getName() + " has no key, the JSON"
                + " field packs write the settings under; add key = \"" + suggestedKey(feature) + "\" (or another plain field"
                + " name) to it and rebuild the jar", missing);
    }

    /** The feature id's path in camel case, such as {@code mossCover} for {@code example:moss/cover}. */
    private static String suggestedKey(Feature feature) {
        String id = feature.id();
        String path = id.substring(id.indexOf(':') + 1);
        String camel = Arrays.stream(path.split("[/_.-]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining());
        return camel.isEmpty() ? "settings" : Character.toLowerCase(camel.charAt(0)) + camel.substring(1);
    }
}
