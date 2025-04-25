package com.volmit.iris.util.reflect;

import com.volmit.iris.Iris;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NMSRef {
    public static Object getFor(Class<?> type, Object source) {
        Object o = fieldFor(type, source);

        if (o != null) {
            return o;
        }

        return invokeFor(type, source);
    }

    public static  Object invokeFor(Class<?> returns, Object in) {
        for (Method i : in.getClass().getMethods()) {
            if (i.getReturnType().equals(returns)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returns.getSimpleName() + " in " + in.getClass().getSimpleName() + "." + i.getName() + "()");
                    return i.invoke(in);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    public static  Object fieldFor(Class<?> returns, Object in) {
        return fieldForClass(returns, in.getClass(), in);
    }

    @SuppressWarnings("unchecked")
    public static  <T> T fieldForClass(Class<T> returnType, Class<?> sourceType, Object in) {
        for (Field i : sourceType.getDeclaredFields()) {
            if (i.getType().equals(returnType)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returnType.getSimpleName() + " in " + sourceType.getSimpleName() + "." + i.getName());
                    return (T) i.get(in);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static  Class<?> getClassType(Class<?> type, int ordinal) {
        return type.getDeclaredClasses()[ordinal];
    }

    public static Field getField(Class<?> clazz, Class<?> fieldType) throws NoSuchFieldException {
        try {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType().equals(fieldType))
                    return f;
            }
            throw new NoSuchFieldException(fieldType.getName());
        } catch (NoSuchFieldException var4) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw var4;
            } else {
                return getField(superClass, fieldType);
            }
        }
    }
}
