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

package com.volmit.iris.util.reflect;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentSkipListMap;

public class Violator {
    protected static final ConcurrentSkipListMap<String, Object> nodes = new ConcurrentSkipListMap<>();

    private static String id(Object o, Object h) {
        if (o instanceof Field) {
            return id(((Field) o).getDeclaringClass(), null) + "." + ((Field) o).getName();
        }

        if (o instanceof String) {
            return (String) o;
        }

        if (o instanceof Class<?>) {
            return ((Class<?>) o).getCanonicalName();
        }

        if (o instanceof Constructor<?> co) {

            StringBuilder mx = new StringBuilder();

            for (Class<?> i : co.getParameterTypes()) {
                mx.append(",").append(i.getCanonicalName());
            }

            mx = new StringBuilder(mx.length() >= 1 ? mx.substring(1) : mx.toString());

            return id(co.getDeclaringClass(), null) + "(" + mx + ")";
        }

        if (o instanceof Method) {
            StringBuilder mx = new StringBuilder();

            for (Class<?> i : ((Method) o).getParameterTypes()) {
                mx.append(",").append(i.getCanonicalName());
            }

            mx = new StringBuilder(mx.length() >= 1 ? mx.substring(1) : mx.toString());

            return id(((Method) o).getDeclaringClass(), null) + "." + ((Method) o).getName() + "(" + mx + ")";
        }

        if (o instanceof Annotation a) {
            return "@" + a.annotationType().getCanonicalName() + "[" + id(h, null) + "]";
        }

        return o.hashCode() + o.toString();
    }

    private static void p(String n, Object o) {
        nodes.put(n, o);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean h(String n) {
        return nodes.containsKey(n);
    }

    private static Object g(String n) {
        return nodes.get(n);
    }

    public static Constructor<?> getConstructor(Class<?> c, Class<?>... params) throws NoSuchMethodException, SecurityException {
        StringBuilder mx = new StringBuilder();

        for (Class<?> i : params) {
            mx.append(",").append(i.getCanonicalName());
        }

        mx = new StringBuilder(mx.length() >= 1 ? mx.substring(1) : mx.toString());

        if (!h(id(c, null) + "(" + mx + ")")) {
            Constructor<?> co = c.getConstructor(params);
            co.setAccessible(true);
            p(id(co, null), co);
        }

        return (Constructor<?>) g(id(c, null) + "(" + mx + ")");
    }

    @SuppressWarnings("rawtypes")
    public static Field getField(Class<?> c, String name) throws Throwable {
        if (!h(id(c, null) + "." + name)) {
            try {
                Field f = c.getField(name);
                f.setAccessible(true);
                p(id(c, null) + "." + name, f);
            } catch (NoSuchFieldException e) {
                Iris.reportError(e);
                Class s = c.getSuperclass();
                if (null == s) {
                    throw e;
                }
                Field f = s.getField(name);
                f.setAccessible(true);
                p(id(c, null) + "." + name, f);
            }
        }

        return (Field) g(id(c, null) + "." + name);
    }

    @SuppressWarnings("rawtypes")
    public static Field getDeclaredField(Class<?> c, String name) throws Throwable {
        if (!h(id(c, null) + "." + name)) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                p(id(c, null) + "." + name, f);
            } catch (NoSuchFieldException e) {
                Iris.reportError(e);
                Class s = c.getSuperclass();
                if (null == s) {
                    throw e;
                }
                Field f = s.getDeclaredField(name);
                f.setAccessible(true);
                p(id(c, null) + "." + name, f);
            }
        }

        return (Field) g(id(c, null) + "." + name);
    }

    public static Method getMethod(Class<?> c, String name, Class<?>... pars) throws Throwable {
        String iv = "";
        StringBuilder mx = new StringBuilder();

        for (Class<?> i : pars) {
            mx.append(",").append(i.getCanonicalName());
        }

        mx = new StringBuilder(mx.length() >= 1 ? mx.substring(1) : mx.toString());
        iv = id(c, null) + "." + name + "(" + mx + ")";

        if (!h(iv)) {
            Method f = c.getMethod(name, pars);
            f.setAccessible(true);
            p(iv, f);
        }

        return (Method) g(iv);
    }

    @SuppressWarnings("unchecked")
    public static <T> T construct(Class<?> c, Object... parameters) {
        KList<Class<?>> cv = new KList<>();

        for (Object i : parameters) {
            cv.add(i.getClass());
        }

        try {
            Constructor<?> co = getConstructor(c, cv.toArray(new Class<?>[0]));
            return (T) co.newInstance(parameters);
        } catch (Exception e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    public static Method getDeclaredMethod(Class<?> c, String name, Class<?>... pars) throws Throwable {
        String iv = "";
        StringBuilder mx = new StringBuilder();

        for (Class<?> i : pars) {
            mx.append(",").append(i.getCanonicalName());
        }

        mx = new StringBuilder(mx.length() >= 1 ? mx.substring(1) : mx.toString());
        iv = id(c, null) + "." + name + "(" + mx + ")";

        if (!h(iv)) {
            Method f = c.getDeclaredMethod(name, pars);
            f.setAccessible(true);
            p(iv, f);
        }

        return (Method) g(iv);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getAnnotation(Class<?> c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]")) {
            T f = c.getAnnotation(a);
            p(id(f, c), f);
        }

        return (T) g("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getDeclaredAnnotation(Class<?> c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]")) {
            T f = c.getDeclaredAnnotation(a);
            p(id(f, c), f);
        }

        return (T) g("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getAnnotation(Field c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]")) {
            T f = c.getAnnotation(a);
            p(id(f, c), f);
        }

        return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getDeclaredAnnotation(Field c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]")) {
            T f = c.getDeclaredAnnotation(a);
            p(id(f, c), f);
        }

        return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getAnnotation(Method c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]")) {
            T f = c.getAnnotation(a);
            p(id(f, c), f);
        }

        return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T getDeclaredAnnotation(Method c, Class<? extends T> a) {
        if (!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]")) {
            T f = c.getDeclaredAnnotation(a);
            p(id(f, c), f);

            Iris.debug("Set as " + id(f, c) + " as " + ("@" + a.getCanonicalName() + "[" + id(c, null) + "]"));
        }

        return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
    }
}
