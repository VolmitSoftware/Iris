package com.volmit.iris.util.reflect;

import com.volmit.iris.core.nms.container.Pair;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Reflect {

    public static <T> T newInstance(Class<T> type, Object... initArgs) throws NoSuchMethodException, InvocationTargetException {
        var list = Arrays.stream(initArgs)
                .map(arg -> new Pair<Class<?>, Object>(arg.getClass(), arg))
                .toList();
        return newInstance(type, list);
    }

    public static <T> T newInstance(Class<T> type, List<Pair<Class<?>, Object>> initArgs) throws NoSuchMethodException, InvocationTargetException{
        constructors:
        for (var c : type.getDeclaredConstructors()) {
            var types = c.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (!types[i].isAssignableFrom(initArgs.get(i).getA()))
                    continue constructors;
            }

            c.setAccessible(true);
            try {
                return (T) c.newInstance(initArgs);
            } catch (InstantiationException | IllegalAccessException e) {
                throw new InvocationTargetException(e);
            }
        }

        var constructors = Arrays.stream(type.getDeclaredConstructors())
                .map(Constructor::toGenericString)
                .collect(Collectors.joining("\n"));
        throw new NoSuchMethodException("No matching constructor found in:\n" + constructors);
    }
}
