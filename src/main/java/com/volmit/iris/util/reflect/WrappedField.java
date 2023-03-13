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
        } catch(NoSuchFieldException e) {
            Iris.error("Failed to created WrappedField %s#%s: %s%s", origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
        }
        this.field = f;
    }

    public T get() {
        return get(null);
    }

    public T get(C instance) {
        if(field == null) {
            return null;
        }

        try {
            return (T)field.get(instance);
        } catch(IllegalAccessException e) {
            Iris.error("Failed to get WrappedField %s#%s: %s%s", field.getDeclaringClass().getSimpleName(), field.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
            return null;
        }
    }

    public boolean hasFailed() {
        return field == null;
    }
}
