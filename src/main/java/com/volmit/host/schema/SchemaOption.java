package com.volmit.host.schema;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** One option of an {@link ObjectType}: a field of a settings class.
 *
 * <p>{@code defaultValue} is the field's value in an instance made by the class's no-argument
 * constructor, as plain JSON-shaped Java values: {@code Boolean}, {@code Long} for integer kinds,
 * {@code Double} for decimal kinds, {@code String} for text, references and enum constants,
 * {@code List<Object>} for list kinds (sets sorted) and {@code Map<String, Object>} with sorted keys
 * for maps and objects, where an object holds its options that have defaults. It is empty when the
 * field is null, when a float or double is not finite, when a list or map holds a value without a
 * default and when a set holds objects, lists or maps. An object inside itself, such as a field
 * holding {@code this}, is left out of the outer object.
 */
public record SchemaOption(
        String name,
        ValueKind kind,
        String description,
        boolean required,
        OptionalDouble minimum,
        OptionalDouble maximum,
        Optional<Object> defaultValue) {
    public SchemaOption {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(defaultValue, "defaultValue");
    }
}
