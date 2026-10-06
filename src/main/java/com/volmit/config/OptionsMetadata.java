package com.volmit.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Annotation metadata, independent of JSON libraries. Does not decode or validate values. */
public record OptionsMetadata(String description, String snippet, List<Option> options) {
    public OptionsMetadata {
        options = List.copyOf(options);
    }

    /** One field of a settings type. {@code minimum}, {@code maximum} and {@code reference} are null when not declared. */
    public record Option(Field field, String description, boolean required,
                         Double minimum, Double maximum, Reference reference) {
        public String name() {
            return field.getName();
        }

        public Type type() {
            return field.getGenericType();
        }
    }

    /** Reads private and inherited fields without constructing settings or accessing values.
     * Nested object types are preserved for the schema builder to inspect recursively.
     */
    public static OptionsMetadata inspect(Class<?> type) {
        var options = new ArrayList<Option>();
        var names = new HashSet<String>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) {
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
                var ref = field.getAnnotation(Ref.class);
                if ((min != null || max != null) && !isNumeric(field.getType())) {
                    throw new IllegalArgumentException("Numeric bounds on nonnumeric option: " + field.getName());
                }
                if ((min != null && !Double.isFinite(min.value()))
                        || (max != null && !Double.isFinite(max.value()))
                        || (min != null && max != null && min.value() > max.value())) {
                    throw new IllegalArgumentException("Invalid bounds for option: " + field.getName());
                }
                options.add(new Option(field, desc == null ? "" : desc.value(), field.isAnnotationPresent(Required.class),
                        min == null ? null : min.value(), max == null ? null : max.value(),
                        ref == null ? null : ref.value()));
            }
        }
        var desc = type.getAnnotation(Desc.class);
        var snippet = type.getAnnotation(Snippet.class);
        return new OptionsMetadata(desc == null ? "" : desc.value(),
                snippet == null ? "" : snippet.value(), options);
    }

    private static boolean isNumeric(Class<?> type) {
        return Number.class.isAssignableFrom(type) || type == byte.class || type == short.class
                || type == int.class || type == long.class || type == float.class || type == double.class;
    }
}
