package com.volmit.host.schema;

import com.volmit.config.Reference;
import com.volmit.resource.ResourceId;
import java.util.List;
import java.util.Objects;

/** The JSON an option takes. Every settings field maps to exactly one kind; a field of any other type
 * fails the pack load, so the host never sees an untyped option.
 */
public sealed interface ValueKind {
    /** {@code true} or {@code false}: {@code boolean} and {@code Boolean} fields. */
    record BooleanKind() implements ValueKind {}

    /** A whole number: {@code byte}, {@code short}, {@code int} and {@code long} fields and their boxes. */
    record IntegerKind() implements ValueKind {}

    /** Any number: {@code float} and {@code double} fields and their boxes. */
    record DecimalKind() implements ValueKind {}

    /** A string in {@code format} that names nothing in particular. */
    record TextKind(TextFormat format) implements ValueKind {
        public TextKind {
            Objects.requireNonNull(format, "format");
        }
    }

    /** A string in {@code format} that names an entry of {@code reference}, such as a block. */
    record ReferenceKind(Reference reference, TextFormat format) implements ValueKind {
        public ReferenceKind {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(format, "format");
        }
    }

    /** A string naming a resource of the module resource kind {@code kind} by its key in the pack, such as
     * {@code castle/start}; see {@link PackSchema#resource}.
     */
    record ResourceReferenceKind(ResourceId kind) implements ValueKind {
        public ResourceReferenceKind {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** One of the constant names of the enum class named {@code type}, in declaration order. */
    record EnumKind(String type, List<EnumConstant> constants) implements ValueKind {
        public EnumKind {
            Objects.requireNonNull(type, "type");
            constants = List.copyOf(constants);
        }
    }

    /** A JSON array of {@code element} values: lists, arrays and, with {@code unique}, sets. */
    record ListKind(ValueKind element, boolean unique) implements ValueKind {
        public ListKind {
            Objects.requireNonNull(element, "element");
        }
    }

    /** A JSON object with any string keys and {@code value} values: {@code Map<String, V>} fields. */
    record MapKind(ValueKind value) implements ValueKind {
        public MapKind {
            Objects.requireNonNull(value, "value");
        }
    }

    /** A JSON object described by the {@link ObjectType} named {@code type} in {@link PackSchema#types()}.
     * Types are referenced by name, so a type that contains itself is described once.
     */
    record ObjectKind(String type) implements ValueKind {
        public ObjectKind {
            Objects.requireNonNull(type, "type");
        }
    }

    /** A value of the class named {@code type}, which the host describes itself, such as one of Iris's
     * palettes or noise styles. Only built-in modules' settings use these (see
     * {@link com.volmit.host.ModuleHost#describingHostTypesIn}); the host renders its own description.
     */
    record HostKind(String type) implements ValueKind {
        public HostKind {
            Objects.requireNonNull(type, "type");
        }
    }

    /** An enum constant and its {@code @Desc}, or an empty description. */
    record EnumConstant(String name, String description) {
        public EnumConstant {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
        }
    }
}
