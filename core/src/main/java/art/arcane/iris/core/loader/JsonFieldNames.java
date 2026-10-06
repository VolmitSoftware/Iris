/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.loader;

import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The JSON keys Gson reads for a class: every non-static, non-transient field, inherited ones included,
 * under its {@link SerializedName} and alternates when it has one.
 */
public final class JsonFieldNames {
    private static final ConcurrentHashMap<Class<?>, Set<String>> CACHE = new ConcurrentHashMap<>();

    private JsonFieldNames() {
    }

    public static Set<String> of(Class<?> type) {
        return CACHE.computeIfAbsent(type, JsonFieldNames::collect);
    }

    private static Set<String> collect(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (isSerialized(field)) {
                    addNames(field, names);
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static boolean isSerialized(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !field.isSynthetic();
    }

    private static void addNames(Field field, Set<String> names) {
        SerializedName serialized = field.getAnnotation(SerializedName.class);
        if (serialized == null) {
            names.add(field.getName());
            return;
        }
        names.add(serialized.value());
        Collections.addAll(names, serialized.alternate());
    }
}
