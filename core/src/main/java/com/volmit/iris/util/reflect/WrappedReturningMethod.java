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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class WrappedReturningMethod<C, R> {

    private final Method method;

    public WrappedReturningMethod(Class<C> origin, String methodName, Class<?>... paramTypes) {
        Method m = null;
        try {
            m = origin.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Iris.error("Failed to created WrappedMethod %s#%s: %s%s", origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
        }
        this.method = m;
    }

    public R invoke(Object... args) {
        return invoke(null, args);
    }

    public R invoke(C instance, Object... args) {
        if (method == null) {
            return null;
        }

        try {
            return (R) method.invoke(instance, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            Iris.error("Failed to invoke WrappedMethod %s#%s: %s%s", method.getDeclaringClass().getSimpleName(), method.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
            return null;
        }
    }
}
