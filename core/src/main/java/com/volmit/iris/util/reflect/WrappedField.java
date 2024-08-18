/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.reflect;

import com.volmit.iris.Iris;

import java.lang.reflect.Field;

public class WrappedField<C, T> {

    private final Field field;

    public WrappedField(Class<C> origin, String methodName) {
        Field f = null;
        try {
            f = origin.getDeclaredField(methodName);
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Iris.error("Failed to created WrappedField %s#%s: %s%s", origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
        }
        this.field = f;
    }

    public T get() {
        return get(null);
    }

    public T get(C instance) {
        if (field == null) {
            return null;
        }

        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            Iris.error("Failed to get WrappedField %s#%s: %s%s", field.getDeclaringClass().getSimpleName(), field.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
            return null;
        }
    }

    public void set(T value) throws IllegalAccessException {
        set(null, value);
    }

    public void set(C instance, T value) throws IllegalAccessException {
        if (field == null) {
            return;
        }

        field.set(instance, value);
    }

    public boolean hasFailed() {
        return field == null;
    }
}
