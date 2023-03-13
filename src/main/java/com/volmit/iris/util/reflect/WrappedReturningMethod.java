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
        } catch(NoSuchMethodException e) {
            Iris.error("Failed to created WrappedMethod %s#%s: %s%s", origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
        }
        this.method = m;
    }

    public R invoke(Object... args) {
        return invoke(null, args);
    }

    public R invoke(C instance, Object... args) {
        if(method == null) {
            return null;
        }

        try {
            return (R)method.invoke(instance, args);
        } catch(InvocationTargetException | IllegalAccessException e) {
            Iris.error("Failed to invoke WrappedMethod %s#%s: %s%s", method.getDeclaringClass().getSimpleName(), method.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage());
            return null;
        }
    }
}
