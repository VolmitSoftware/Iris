/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.ResourceRef;
import com.volmit.host.ResourceKindRegistration;
import com.volmit.resource.ResourceKind;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The resources of the pack's module resource kinds that a pack export has to carry: what the settings of the
 * exported dimension, regions and biomes (and anything else handed to {@link #follow}) name by {@link ResourceRef} or
 * {@link RegistryListResource}, and what those resources name in turn. Core does not know the kinds: it follows the
 * references the modules declare on their classes.
 */
public final class ModuleResourceClosure {
    private static final String JAVA_PACKAGE_PREFIX = "java.";
    private static final String JSON_EXTENSION = ".json";
    private static final int PRETTY_INDENT = 4;

    private final IrisData data;
    private final Map<Class<?>, ResourceKind<?>> kindsByType = new HashMap<>();
    private final Map<ResourceKind<?>, Map<String, Object>> found = new LinkedHashMap<>();
    private final Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Class<?>, Set<String>> exportedElsewhere = new HashMap<>();

    public ModuleResourceClosure(IrisData data) {
        this.data = data;
        for (ResourceKindRegistration registration : data.getModules().pack().catalog().resourceKinds()) {
            kindsByType.put(registration.kind().type(), registration.kind());
        }
    }

    /**
     * Leaves the resources that read into {@code type} to another exporter, such as Iris's structure closure that
     * copies structures with their pools, pieces and loot: {@link #keysOf} names the ones referenced, but they are
     * not loaded, followed or written. Call before following anything.
     */
    public ModuleResourceClosure exportingElsewhere(Class<?> type) {
        exportedElsewhere.putIfAbsent(type, new TreeSet<>());
        return this;
    }

    /** Adds the resources that the module settings of {@code owner} reference, and those they reference. */
    public void followSettingsOf(IrisRegistrant owner) {
        owner.getModuleSettings().values().forEach(this::follow);
    }

    /** Adds the resources that {@code holder} references, and those they reference. */
    public void follow(Object holder) {
        if (holder == null || !visited.add(holder)) {
            return;
        }
        if (holder instanceof Collection<?> collection) {
            collection.forEach(this::follow);
            return;
        }
        if (holder instanceof Map<?, ?> map) {
            map.values().forEach(this::follow);
            return;
        }
        if (holder instanceof Object[] array) {
            for (Object element : array) {
                follow(element);
            }
            return;
        }
        if (isLeaf(holder.getClass())) {
            return;
        }
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                followField(holder, field);
            }
        }
    }

    /** Whether nothing was found. */
    public boolean isEmpty() {
        return found.isEmpty();
    }

    /** The keys of the resources found of the kind that reads into {@code type}, in key order. */
    public List<String> keysOf(Class<?> type) {
        if (exportedElsewhere.containsKey(type)) {
            return new ArrayList<>(exportedElsewhere.get(type));
        }
        ResourceKind<?> kind = kindsByType.get(type);
        return kind == null || !found.containsKey(kind) ? List.of() : new ArrayList<>(new TreeMap<>(found.get(kind)).keySet());
    }

    /**
     * Writes every resource found as {@code <folder of its kind>/<key>.json} below {@code targetRoot}, kinds in folder
     * order and keys in key order.
     *
     * @return the hashes of the files written, to put into the package's hash
     */
    public String writeTo(File targetRoot, boolean minify) throws IOException {
        StringBuilder hashes = new StringBuilder();
        List<Map.Entry<ResourceKind<?>, Map<String, Object>>> kinds = new ArrayList<>(found.entrySet());
        kinds.sort(Comparator.comparing(entry -> entry.getKey().folder()));
        for (Map.Entry<ResourceKind<?>, Map<String, Object>> kind : kinds) {
            for (Map.Entry<String, Object> resource : new TreeMap<>(kind.getValue()).entrySet()) {
                String json = new JSONObject(new Gson().toJson(resource.getValue())).toString(minify ? 0 : PRETTY_INDENT);
                IO.writeAll(new File(targetRoot, kind.getKey().folder() + "/" + resource.getKey() + JSON_EXTENSION), json);
                hashes.append(IO.hash(json));
            }
        }
        return hashes.toString();
    }

    private void followField(Object holder, Field field) {
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.getType().isPrimitive()) {
            return;
        }
        Object value = valueOf(holder, field);
        if (value == null) {
            return;
        }
        Class<?> referenced = referencedType(field);
        if (referenced == null) {
            follow(value);
            return;
        }
        for (String key : keysIn(value)) {
            add(referenced, key);
        }
    }

    /** The class of the resources the field names by key, or null when it names none. */
    private static Class<?> referencedType(Field field) {
        ResourceRef reference = field.getAnnotation(ResourceRef.class);
        if (reference != null) {
            return reference.value();
        }
        RegistryListResource registry = field.getAnnotation(RegistryListResource.class);
        return registry == null ? null : registry.value();
    }

    private static List<String> keysIn(Object value) {
        List<String> keys = new ArrayList<>();
        if (value instanceof String key) {
            keys.add(key);
        } else if (value instanceof Collection<?> collection) {
            collection.stream().filter(String.class::isInstance).map(String.class::cast).forEach(keys::add);
        }
        return keys;
    }

    private void add(Class<?> type, String key) {
        ResourceKind<?> kind = kindsByType.get(type);
        if (kind == null || key == null || key.isBlank()) {
            return;
        }
        Set<String> elsewhere = exportedElsewhere.get(type);
        if (elsewhere != null) {
            elsewhere.add(key);
            return;
        }
        Map<String, Object> ofKind = found.computeIfAbsent(kind, ignored -> new LinkedHashMap<>());
        if (ofKind.containsKey(key)) {
            return;
        }
        Object resource = lookup(kind, key);
        if (resource == null) {
            return;
        }
        ofKind.put(key, resource);
        follow(resource);
    }

    private Object lookup(ResourceKind<?> kind, String key) {
        IrisPackResources resources = data.getPackResources();
        try {
            return resources.find(kind.key(resources.idOf(key))).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    private static boolean isLeaf(Class<?> type) {
        return type.isEnum() || type.getName().startsWith(JAVA_PACKAGE_PREFIX);
    }

    private static Object valueOf(Object holder, Field field) {
        try {
            field.setAccessible(true);
            return field.get(holder);
        } catch (ReflectiveOperationException | RuntimeException inaccessible) {
            return null;
        }
    }
}
