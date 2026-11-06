package com.volmit.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Orders a class's own fields as its source declares them. {@link Class#getDeclaredFields()} promises
 * no order, but the class file lists fields in declaration order, so this reads it.
 */
final class DeclaredFieldOrder {
    private static final String CLASS_FILE_SUFFIX = ".class";

    private DeclaredFieldOrder() {}

    static List<Field> of(Class<?> type) {
        var names = fieldNames(type);
        return Arrays.stream(type.getDeclaredFields())
                .sorted(Comparator.comparingInt((Field field) -> position(names, field, type)))
                .toList();
    }

    private static int position(List<String> names, Field field, Class<?> type) {
        int position = names.indexOf(field.getName());
        if (position < 0) {
            throw new IllegalArgumentException("Field " + field.getName() + " of " + type.getName()
                    + " is not in its class file, so its declaration order is unknown");
        }
        return position;
    }

    private static List<String> fieldNames(Class<?> type) {
        try (var classFile = openClassFile(type)) {
            return ClassFile.of().parse(classFile.readAllBytes()).fields().stream()
                    .map(field -> field.fieldName().stringValue())
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read the class file of " + type.getName(), failure);
        }
    }

    private static InputStream openClassFile(Class<?> type) {
        var resource = "/" + type.getName().replace('.', '/') + CLASS_FILE_SUFFIX;
        var stream = type.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("Cannot read the class file of " + type.getName()
                    + " to order its options as declared; settings classes must come from a jar or a class directory");
        }
        return stream;
    }
}
