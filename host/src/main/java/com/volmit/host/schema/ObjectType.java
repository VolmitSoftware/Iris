package com.volmit.host.schema;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A settings class, described once per pack however many options use it.
 *
 * @param name the class's binary name, such as {@code com.example.moss.MossSettings}; unique in a pack
 * @param description the class's {@code @Desc}, or empty
 * @param snippet the class's {@code @Snippet} name: a pack may then write the object in its own file
 *     under {@code snippet/<name>/} and refer to it by path
 * @param options the class's fields, including inherited ones, in declaration order, a superclass's first
 */
public record ObjectType(String name, String description, Optional<String> snippet, List<SchemaOption> options) {
    public ObjectType {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(snippet, "snippet");
        options = List.copyOf(options);
    }
}
