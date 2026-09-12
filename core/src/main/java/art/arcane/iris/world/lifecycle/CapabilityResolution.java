package art.arcane.iris.world.lifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

final class CapabilityResolution {
    private CapabilityResolution() {
    }

    static Method resolveCreateLevelMethod(Class<?> owner) throws NoSuchMethodException {
        Method current = resolveMethod(owner, "createLevel", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 3
                    && "LevelStem".equals(params[0].getSimpleName())
                    && "WorldLoadingInfoAndData".equals(params[1].getSimpleName())
                    && "WorldDataAndGenSettings".equals(params[2].getSimpleName());
        });
        if (current != null) {
            return current;
        }

        Method legacy = resolveMethod(owner, "createLevel", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 4
                    && "LevelStem".equals(params[0].getSimpleName())
                    && "WorldLoadingInfo".equals(params[1].getSimpleName())
                    && "LevelStorageAccess".equals(params[2].getSimpleName())
                    && "PrimaryLevelData".equals(params[3].getSimpleName());
        });
        if (legacy != null) {
            return legacy;
        }

        throw new NoSuchMethodException(owner.getName() + "#createLevel");
    }

    static Method resolveLevelStorageAccessMethod(Class<?> owner) throws NoSuchMethodException {
        Method exactValidate = resolveMethod(owner, "validateAndCreateAccess", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 2
                    && String.class.equals(params[0])
                    && "ResourceKey".equals(params[1].getSimpleName())
                    && "LevelStorageAccess".equals(method.getReturnType().getSimpleName());
        });
        if (exactValidate != null) {
            return exactValidate;
        }

        Method oneArgValidate = resolveMethod(owner, "validateAndCreateAccess", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1
                    && String.class.equals(params[0])
                    && "LevelStorageAccess".equals(method.getReturnType().getSimpleName());
        });
        if (oneArgValidate != null) {
            return oneArgValidate;
        }

        Method exactCreate = resolveMethod(owner, "createAccess", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 2
                    && String.class.equals(params[0])
                    && "ResourceKey".equals(params[1].getSimpleName())
                    && "LevelStorageAccess".equals(method.getReturnType().getSimpleName());
        });
        if (exactCreate != null) {
            return exactCreate;
        }

        Method oneArgCreate = resolveMethod(owner, "createAccess", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1
                    && String.class.equals(params[0])
                    && "LevelStorageAccess".equals(method.getReturnType().getSimpleName());
        });
        if (oneArgCreate != null) {
            return oneArgCreate;
        }

        throw new NoSuchMethodException(owner.getName() + "#validateAndCreateAccess/createAccess");
    }

    static Method resolvePaperWorldDataMethod(Class<?> owner) throws NoSuchMethodException {
        Method current = resolveMethod(owner, "loadWorldData", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 3
                    && "MinecraftServer".equals(params[0].getSimpleName())
                    && "ResourceKey".equals(params[1].getSimpleName())
                    && String.class.equals(params[2])
                    && "LoadedWorldData".equals(method.getReturnType().getSimpleName());
        });
        if (current != null) {
            return current;
        }

        Method legacy = resolveMethod(owner, "getLevelData", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && "LevelStorageAccess".equals(params[0].getSimpleName());
        });
        if (legacy != null) {
            return legacy;
        }

        throw new NoSuchMethodException(owner.getName() + "#loadWorldData/getLevelData");
    }

    static Constructor<?> resolveWorldLoadingInfoConstructor(Class<?> owner) throws NoSuchMethodException {
        Constructor<?> current = resolveConstructor(owner, constructor -> {
            Class<?>[] params = constructor.getParameterTypes();
            return params.length == 4
                    && "Environment".equals(params[0].getSimpleName())
                    && "ResourceKey".equals(params[1].getSimpleName())
                    && "ResourceKey".equals(params[2].getSimpleName())
                    && boolean.class.equals(params[3]);
        });
        if (current != null) {
            return current;
        }

        Constructor<?> legacy = resolveConstructor(owner, constructor -> {
            Class<?>[] params = constructor.getParameterTypes();
            return params.length == 5
                    && int.class.equals(params[0])
                    && String.class.equals(params[1])
                    && String.class.equals(params[2])
                    && "ResourceKey".equals(params[3].getSimpleName())
                    && boolean.class.equals(params[4]);
        });
        if (legacy != null) {
            return legacy;
        }

        throw new NoSuchMethodException(owner.getName() + "#<init>");
    }

    static Constructor<?> resolveWorldLoadingInfoAndDataConstructor(Class<?> owner) throws NoSuchMethodException {
        Constructor<?> constructor = resolveConstructor(owner, candidate -> {
            Class<?>[] params = candidate.getParameterTypes();
            return params.length == 2
                    && "WorldLoadingInfo".equals(params[0].getSimpleName())
                    && "LoadedWorldData".equals(params[1].getSimpleName());
        });
        if (constructor == null) {
            throw new NoSuchMethodException(owner.getName() + "#<init>");
        }
        return constructor;
    }

    static Method resolveCreateNewWorldDataMethod(Class<?> owner) throws NoSuchMethodException {
        Method method = resolveMethod(owner, "createNewWorldData", candidate -> {
            Class<?>[] params = candidate.getParameterTypes();
            return params.length == 5
                    && "DedicatedServerSettings".equals(params[0].getSimpleName())
                    && "DataLoadContext".equals(params[1].getSimpleName())
                    && "Registry".equals(params[2].getSimpleName())
                    && boolean.class.equals(params[3])
                    && boolean.class.equals(params[4]);
        });
        if (method == null) {
            throw new NoSuchMethodException(owner.getName() + "#createNewWorldData");
        }
        return method;
    }

    static Method resolveServerRegistryAccessMethod(Class<?> owner) throws NoSuchMethodException {
        Method method = resolveMethod(owner, "registryAccess", candidate -> candidate.getParameterCount() == 0
                && !void.class.equals(candidate.getReturnType()));
        if (method == null) {
            throw new NoSuchMethodException(owner.getName() + "#registryAccess");
        }
        return method;
    }

    static Method resolveMethod(Class<?> owner, String name, Predicate<Method> predicate) {
        Method selected = scanMethods(owner.getMethods(), name, predicate);
        if (selected != null) {
            return selected;
        }

        Class<?> current = owner;
        while (current != null) {
            selected = scanMethods(current.getDeclaredMethods(), name, predicate);
            if (selected != null) {
                selected.setAccessible(true);
                return selected;
            }
            current = current.getSuperclass();
        }

        return null;
    }

    static Field resolveField(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static Method scanMethods(Method[] methods, String name, Predicate<Method> predicate) {
        for (Method method : methods) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (predicate.test(method)) {
                return method;
            }
        }
        return null;
    }

    private static Constructor<?> resolveConstructor(Class<?> owner, Predicate<Constructor<?>> predicate) {
        for (Constructor<?> constructor : owner.getConstructors()) {
            if (predicate.test(constructor)) {
                return constructor;
            }
        }
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            if (predicate.test(constructor)) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        return null;
    }
}
