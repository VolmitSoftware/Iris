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

package art.arcane.iris.core.structure;

import art.arcane.iris.spi.IrisLogging;
import com.volmit.resource.ResourceId;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Reads the server's registries and resource manager by reflection, because they are version-bound server internals
 * that Paper's api does not reach. Every lookup finds its members by name, so a server version that renamed one fails the
 * read with the member's name instead of failing to link.
 */
final class ServerReflection {
    private ServerReflection() {
    }

    /** The server's {@code MinecraftServer}. */
    static Object minecraftServer() throws Exception {
        Object craftServer = Bukkit.getServer();
        Object dedicated = invoke(craftServer, "getHandle");
        return invoke(dedicated, "getServer");
    }

    static Object resolveRegistryAccess(Object server) {
        try {
            Class<?> frozen = Class.forName("net.minecraft.core.RegistryAccess$Frozen");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && frozen.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
            Class<?> ra = Class.forName("net.minecraft.core.RegistryAccess");
            for (Method m : server.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && ra.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    Object o = m.invoke(server);
                    if (o != null) {
                        return o;
                    }
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        return null;
    }

    static Object lookupRegistry(Object registryAccess, String registryName) throws Exception {
        Class<?> registries = Class.forName("net.minecraft.core.registries.Registries");
        Object resourceKey = registries.getField(registryName).get(null);
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        Method registryOverload = null;
        for (Method m : registryAccess.getClass().getMethods()) {
            if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].getName().endsWith("ResourceKey")
                    && registryClass.isAssignableFrom(m.getReturnType())) {
                registryOverload = m;
                break;
            }
        }
        if (registryOverload == null) {
            for (Method m : registryAccess.getClass().getMethods()) {
                if (m.getName().equals("lookupOrThrow") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].getName().endsWith("ResourceKey")) {
                    registryOverload = m;
                    break;
                }
            }
        }
        if (registryOverload == null) {
            throw new NoSuchMethodException("lookupOrThrow(ResourceKey) on " + registryAccess.getClass().getName());
        }
        registryOverload.setAccessible(true);
        return registryOverload.invoke(registryAccess, resourceKey);
    }

    static Object registryGet(Object registry, ResourceId key) throws Exception {
        Object id = identifierOf(key);
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getValue") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return m.invoke(registry, id);
            }
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getOptional") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().endsWith("Identifier")) {
                m.setAccessible(true);
                return unwrapOptional(m.invoke(registry, id));
            }
        }
        return null;
    }

    static String registryKeyOf(Object registry, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        for (Method m : registry.getClass().getMethods()) {
            if (m.getName().equals("getKey") && m.getParameterCount() == 1) {
                m.setAccessible(true);
                Object id = m.invoke(registry, value);
                String s = identifierString(id);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    static Object identifierOf(ResourceId key) throws Exception {
        Class<?> identifier = Class.forName("net.minecraft.resources.Identifier");
        try {
            Method fromNamespaceAndPath = identifier.getMethod("fromNamespaceAndPath", String.class, String.class);
            return fromNamespaceAndPath.invoke(null, key.namespace(), key.path());
        } catch (NoSuchMethodException e) {
            Method withDefaultNamespace = identifier.getMethod("parse", String.class);
            return withDefaultNamespace.invoke(null, key.toString());
        }
    }

    static String identifierString(Object id) {
        if (id == null) {
            return null;
        }
        try {
            Method getNamespace = id.getClass().getMethod("getNamespace");
            Method getPath = id.getClass().getMethod("getPath");
            getNamespace.setAccessible(true);
            getPath.setAccessible(true);
            return getNamespace.invoke(id) + ":" + getPath.invoke(id);
        } catch (Throwable e) {
            return id.toString();
        }
    }

    static Object unwrapHolder(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Method value = findMethod(holder.getClass(), "value");
            if (value != null) {
                value.setAccessible(true);
                return value.invoke(holder);
            }
        } catch (Throwable ignored) {
        }
        return holder;
    }

    static Object unwrapOptional(Object opt) {
        if (opt == null) {
            return null;
        }
        if (opt instanceof Optional<?> o) {
            return o.orElse(null);
        }
        return opt;
    }

    static int readIntMember(Object value, String memberName) throws Exception {
        Class<?> type = value.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(memberName);
                field.setAccessible(true);
                return coerceInt(field.get(value), memberName, value);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        Method method = findMethod(value.getClass(), memberName);
        if (method != null) {
            method.setAccessible(true);
            return coerceInt(method.invoke(value), memberName, value);
        }
        throw new NoSuchFieldException(memberName + " on " + value.getClass().getName());
    }

    /**
     * Members that are plain numbers on one server build are wrapper objects on another
     * (JigsawStructure.maxDistanceFromCenter became a MaxDistance{horizontal, vertical} record).
     * Numbers pass through; a wrapper contributes its horizontal component, else its largest
     * integral component, so a distance bound is never under-read.
     */
    private static int coerceInt(Object member, String memberName, Object owner) throws Exception {
        if (member instanceof Number n) {
            return n.intValue();
        }
        if (member == null) {
            throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName() + " is null");
        }
        Method horizontal = findMethod(member.getClass(), "horizontal");
        if (horizontal != null && Number.class.isAssignableFrom(boxedType(horizontal.getReturnType()))) {
            horizontal.setAccessible(true);
            return ((Number) horizontal.invoke(member)).intValue();
        }
        Integer widest = null;
        for (Field component : member.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(component.getModifiers())
                    || !Number.class.isAssignableFrom(boxedType(component.getType()))) {
                continue;
            }
            component.setAccessible(true);
            Object componentValue = component.get(member);
            if (componentValue instanceof Number n && (widest == null || n.intValue() > widest)) {
                widest = n.intValue();
            }
        }
        if (widest != null) {
            return widest;
        }
        throw new NoSuchFieldException(memberName + " on " + owner.getClass().getName()
                + " is a " + member.getClass().getName() + " with no integral component");
    }

    private static Class<?> boxedType(Class<?> type) {
        return type == int.class ? Integer.class : type;
    }

    static int readInt(Object o, String method) throws Exception {
        Method m = o.getClass().getMethod(method);
        m.setAccessible(true);
        return ((Number) m.invoke(o)).intValue();
    }

    static Object staticField(String className, String fieldName) throws Exception {
        Class<?> c = Class.forName(className);
        Field f = c.getField(fieldName);
        return f.get(null);
    }

    static Object invoke(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method);
        if (m == null) {
            throw new NoSuchMethodException(method + " on " + target.getClass().getName());
        }
        m.setAccessible(true);
        return m.invoke(target);
    }

    static Method findMethod(Class<?> type, String name) {
        return findMethod(type, name, 0);
    }

    static Method findMethod(Class<?> type, String name, int parameterCount) {
        Class<?> c = type;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == parameterCount) {
                return m;
            }
        }
        return null;
    }

    static Object freshRandomSource(long seed) throws Exception {
        Class<?> randomSource = Class.forName("net.minecraft.util.RandomSource");
        Method create = randomSource.getMethod("create", long.class);
        return create.invoke(null, seed);
    }

    /** The message a failed read shows: the failure's message, else its class name. */
    static String failureDetail(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
