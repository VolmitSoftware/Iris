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

package art.arcane.iris.generation.cave;

import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * The numbers a cave writes outside what their keys allow, read from the {@link MinNumber} and {@link MaxNumber} of
 * each key. A {@link CaveValue} is not a plain number, so the schema cannot bound its ends: this does.
 */
public final class CaveBounds {
    private static final String CAVE_PACKAGE = IrisCave.class.getPackageName();

    private CaveBounds() {
    }

    /** Each value of the cave outside the numbers its key allows, such as {@code fade is 200; it must be at most 128}. */
    public static List<String> valuesOutOfBounds(IrisCave cave) {
        List<String> problems = new ArrayList<>();
        collect(cave, "", problems);
        return problems;
    }

    private static void collect(Object holder, String path, List<String> problems) {
        for (Field field : holder.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            Object value = valueOf(field, holder);
            if (value == null) {
                continue;
            }
            String fieldPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
            if (value instanceof CaveValue caveValue) {
                requireWithin(field, caveValue, fieldPath, problems);
            } else if (value instanceof Number number) {
                requireWithin(field, number.doubleValue(), fieldPath, problems);
            } else if (isCaveSetting(value)) {
                collect(value, fieldPath, problems);
            }
        }
    }

    private static boolean isCaveSetting(Object value) {
        return value.getClass().getPackageName().equals(CAVE_PACKAGE) && !(value instanceof IrisCave);
    }

    private static Object valueOf(Field field, Object holder) {
        try {
            field.setAccessible(true);
            return field.get(holder);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Cannot read " + holder.getClass().getSimpleName() + "." + field.getName(), failure);
        }
    }

    private static void requireWithin(Field field, CaveValue value, String path, List<String> problems) {
        if (!value.varies()) {
            requireWithin(field, value.min(), path, problems);
            return;
        }
        requireWithin(field, value.min(), path + ".min", problems);
        requireWithin(field, value.max(), path + ".max", problems);
    }

    private static void requireWithin(Field field, double value, String path, List<String> problems) {
        MinNumber least = field.getAnnotation(MinNumber.class);
        MaxNumber most = field.getAnnotation(MaxNumber.class);
        if (least != null && value < least.value()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at least " + shortNumber(least.value()));
        } else if (most != null && value > most.value()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at most " + shortNumber(most.value()));
        }
    }

    private static String shortNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
