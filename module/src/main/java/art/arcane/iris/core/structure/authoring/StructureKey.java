/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.structure.authoring;

import java.util.Objects;
import java.util.regex.Pattern;

public record StructureKey(String namespace, String path) implements Comparable<StructureKey> {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern PATH_PATTERN = Pattern.compile("[a-z0-9._/-]+");

    public StructureKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid structure namespace: " + namespace);
        }
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid structure path: " + path);
        }
        validatePathSegments(path);
    }

    public static StructureKey parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || separator != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Structure key must use namespace:path: " + value);
        }
        return new StructureKey(value.substring(0, separator), value.substring(separator + 1));
    }

    public static StructureKey parse(String value, String defaultNamespace) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(defaultNamespace, "defaultNamespace");
        if (value.indexOf(':') < 0) {
            return new StructureKey(defaultNamespace, value);
        }
        return parse(value);
    }

    public String value() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(StructureKey other) {
        int namespaceComparison = namespace.compareTo(other.namespace);
        if (namespaceComparison != 0) {
            return namespaceComparison;
        }
        return path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return value();
    }

    private static void validatePathSegments(String path) {
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException("Invalid structure path: " + path);
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid structure path: " + path);
            }
        }
    }
}
