package com.volmit.host;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;
import com.volmit.config.ResourceRef;
import com.volmit.config.Snippet;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** For the host: the options a settings class declares with their annotations, independent of JSON libraries, as
 * studio descriptions and settings decoders read them. Does not decode or validate values.
 */
public record OptionsMetadata(String description, String snippet, List<Option> options) {
    public OptionsMetadata {
        options = List.copyOf(options);
    }

    /** One field of a settings type. {@code minimum}, {@code maximum}, {@code minItems}, {@code reference} and
     * {@code resource} are null when not declared; {@code resource} is the class of {@link ResourceRef}.
     */
    public record Option(Field field, String description, boolean required,
                         Double minimum, Double maximum, Integer minItems, Reference reference, Class<?> resource) {
        public String name() {
            return field.getName();
        }

        public Type type() {
            return field.getGenericType();
        }
    }

    /** Reads private and inherited fields without constructing settings or accessing values. Options are
     * in declaration order, a superclass's before its subclass's. Nested object types are preserved for
     * the schema builder to inspect recursively.
     */
    public static OptionsMetadata inspect(Class<?> type) {
        var options = new ArrayList<Option>();
        var names = new HashSet<String>();
        for (var current : hierarchy(type)) {
            for (var field : DeclaredFieldOrder.of(current)) {
                int modifiers = field.getModifiers();
                if (field.isSynthetic() || Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                if (!names.add(field.getName())) {
                    throw new IllegalArgumentException("Duplicate option: " + field.getName());
                }
                var desc = field.getAnnotation(Desc.class);
                var min = field.getAnnotation(MinNumber.class);
                var max = field.getAnnotation(MaxNumber.class);
                var minItems = field.getAnnotation(MinItems.class);
                var ref = field.getAnnotation(Ref.class);
                var resourceRef = field.getAnnotation(ResourceRef.class);
                if ((min != null || max != null) && !isNumeric(field.getType())) {
                    throw new IllegalArgumentException("Numeric bounds on nonnumeric option: " + field.getName());
                }
                if ((min != null && !Double.isFinite(min.value()))
                        || (max != null && !Double.isFinite(max.value()))
                        || (min != null && max != null && min.value() > max.value())) {
                    throw new IllegalArgumentException("Invalid bounds for option: " + field.getName());
                }
                if (minItems != null && (!isCollection(field.getType()) || minItems.value() < 0)) {
                    throw new IllegalArgumentException("Invalid @MinItems on option: " + field.getName()
                            + "; it fits lists, sets and arrays, and its value is not negative");
                }
                options.add(new Option(field, desc == null ? "" : desc.value(), field.isAnnotationPresent(Required.class),
                        min == null ? null : min.value(), max == null ? null : max.value(),
                        minItems == null ? null : minItems.value(),
                        ref == null ? null : ref.value(), resourceRef == null ? null : resourceRef.value()));
            }
        }
        var desc = type.getAnnotation(Desc.class);
        var snippet = type.getAnnotation(Snippet.class);
        return new OptionsMetadata(desc == null ? "" : desc.value(),
                snippet == null ? "" : snippet.value(), options);
    }

    /** {@code type} and its superclasses below Object, the topmost first. */
    private static List<Class<?>> hierarchy(Class<?> type) {
        var classes = new ArrayList<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            classes.addFirst(current);
        }
        return classes;
    }

    private static boolean isCollection(Class<?> type) {
        return List.class.isAssignableFrom(type) || java.util.Set.class.isAssignableFrom(type) || type.isArray();
    }

    private static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(type) || type == byte.class || type == short.class
                || type == int.class || type == long.class || type == float.class || type == double.class;
    }
}
