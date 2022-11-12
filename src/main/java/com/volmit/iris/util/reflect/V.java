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

public class V {
    private final Object o;
    private boolean local;
    private boolean suppress = false;

    public V(Class<?> c, Object... parameters) {
        this.o = Violator.construct(c, parameters);
        this.local = true;
    }

    public V(Object o) {
        this.o = o;
        this.local = true;
    }

    public V(Object o, boolean local, boolean suppress) {
        this(o);
        this.local = local;
        this.suppress = suppress;
    }

    public V(Object o, boolean local) {
        this(o);
        this.local = local;
    }

    public <T extends Annotation> T get(Class<? extends T> t) {
        try {
            return local ? Violator.getDeclaredAnnotation(o.getClass(), t) : Violator.getAnnotation(o.getClass(), t);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public <T extends Annotation> T get(Class<? extends T> t, String mn, Class<?>... pars) {
        try {
            return local ? Violator.getDeclaredAnnotation(Violator.getDeclaredMethod(o.getClass(), mn, pars), t) : Violator.getAnnotation(Violator.getMethod(o.getClass(), mn, pars), t);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public <T extends Annotation> T get(Class<? extends T> t, String mn) {
        try {
            return local ? Violator.getDeclaredAnnotation(Violator.getDeclaredField(o.getClass(), mn), t) : Violator.getAnnotation(Violator.getField(o.getClass(), mn), t);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String field) {
        try {
            return (T) (local ? Violator.getDeclaredField(o.getClass(), field) : Violator.getField(o.getClass(), field)).get(o);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public Object getSelf() {
        return o;
    }

    public Object invoke(String method, Object... parameters) {
        KList<Class<?>> par = new KList<>();

        for (Object i : parameters) {
            par.add(i.getClass());
        }

        try {
            return (local ? Violator.getDeclaredMethod(o.getClass(), method, par.toArray(new Class<?>[0])) : Violator.getMethod(o.getClass(), method, par.toArray(new Class<?>[0]))).invoke(o, parameters);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public void set(String field, Object value) {
        try {
            // https://github.com/VolmitSoftware/Mortar/issues/5
            (local ? Violator.getDeclaredField(o.getClass(), field) : Violator.getField(o.getClass(), field)).set(o, value);
        } catch (Throwable e) {
            Iris.reportError(e);
            if (!suppress) {
                e.printStackTrace();
            }
        }
    }
}
