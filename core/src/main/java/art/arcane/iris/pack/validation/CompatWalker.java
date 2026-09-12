/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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
package art.arcane.iris.pack.validation;

import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.structure.object.IrisObjectLoot;
import art.arcane.iris.structure.object.IrisObjectVanillaLoot;
import art.arcane.iris.structure.object.IrisObjectMarker;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.iris.pack.schema.annotation.RegistryListBiome;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.RegistryListEnchantment;
import art.arcane.iris.pack.schema.annotation.RegistryListEntityType;
import art.arcane.iris.pack.schema.annotation.RegistryListItemType;
import art.arcane.iris.pack.schema.annotation.RegistryListPotionEffect;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.RegistryListVanillaStructure;
import art.arcane.iris.pack.schema.annotation.RegistryMapBlockState;
import art.arcane.iris.spi.IrisLogging;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One evaluation of one registrant against the policy table: walks the registrant graph through a cached per-class
 * field plan, checks every annotated key, resolves every {@link IrisBlockData} through the gate chain, drops entries
 * the table says to drop, and records findings on the gate's report.
 * <p>
 * Boundaries: {@link IrisObjectPlacement} and {@link IrisStaticObject} are never descended (placements and static
 * objects evaluate their own pools, so their edit palettes exclude only themselves), match-side block lists are
 * ignored (a missing block that is only matched is harmless), registrant references ({@code @RegistryListResource})
 * belong to the pool cascade, and identity cycles are cut.
 */
final class CompatWalker {
    private static final String IRIS_PACKAGE = "art.arcane.iris.";
    private static final int MAX_BACKUP_DEPTH = 8;
    private static final Map<Class<?>, ClassPlan> PLANS = new ConcurrentHashMap<>();
    // Annotated fields whose key defines the registrant itself: missing means the registrant is excluded.
    private static final Set<String> EXCLUSION_SITES = Set.of(
            IrisEntity.class.getName() + "#type",
            IrisBiome.class.getName() + "#derivative");
    // Block lists that only select which existing blocks to act on; a missing key there matches nothing and is harmless.
    private static final Set<String> MATCH_ONLY = Set.of(
            IrisObjectReplace.class.getName() + "#find",
            IrisObjectLoot.class.getName() + "#filter",
            IrisObjectVanillaLoot.class.getName() + "#filter",
            IrisObjectMarker.class.getName() + "#mark",
            IrisBlockDrops.class.getName() + "#blocks",
            IrisDecorator.class.getName() + "#whitelist",
            IrisDecorator.class.getName() + "#blacklist");

    private final ContentGate gate;
    private final IrisRegistrant registrant;
    private final String subjectType;
    private final String subjectKey;
    private final boolean customLookup;
    private final List<CompatFinding> reasons = new ArrayList<>();
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean excluded;

    CompatWalker(ContentGate gate, IrisRegistrant registrant) {
        this.gate = gate;
        this.registrant = registrant;
        // Lowercase so walker findings read like the pool-cascade findings ("excluded biome x", "dropped region y").
        this.subjectType = registrant.getTypeName().toLowerCase(Locale.ROOT);
        this.subjectKey = registrant.getLoadKey();
        // A blocks/ registrant must not re-enter its own loader cache while it is being loaded.
        this.customLookup = !(registrant instanceof IrisBlockData);
    }

    CompatStatus run() {
        if (registrant instanceof IrisBlockData block) {
            resolveEntry(block, "block");
        } else {
            walkObject(registrant, "");
        }
        if (excluded) {
            return CompatStatus.excludedBy(reasons);
        }
        return reasons.isEmpty() ? CompatStatus.OK : new CompatStatus(false, reasons);
    }

    /** Walks one object; true when its owner must drop it (an annotated key on it is missing and it is not the registrant). */
    private boolean walkObject(Object node, String path) {
        if (!visited.add(node)) {
            return false;
        }
        ClassPlan plan = plan(node.getClass());
        boolean drop = false;
        for (KeyField key : plan.keys()) {
            Object value = read(key.field(), node);
            if (value == null) {
                continue;
            }
            String fieldPath = child(path, key.field().getName());
            if (key.list()) {
                if (value instanceof Collection<?> values) {
                    walkKeyList(key, values, fieldPath);
                }
                continue;
            }
            if (!(value instanceof String raw) || raw.isBlank()) {
                continue;
            }
            if (key.registry() == CompatRegistry.BLOCK) {
                resolveBlockKey(raw, fieldPath);
                continue;
            }
            if (check(key.registry(), raw) != KeyStatus.MISSING) {
                continue;
            }
            String missing = normalizedKey(raw);
            if (node != registrant) {
                dropped(key.registry(), missing, fieldPath);
                drop = true;
            } else if (key.exclusionSite()) {
                exclude(key.registry(), missing, fieldPath);
            } else {
                dropped(key.registry(), missing, fieldPath);
                write(key.field(), node, plan.defaultOf(key.field()));
            }
        }
        for (Field field : plan.descents()) {
            Object value = read(field, node);
            if (value == null) {
                continue;
            }
            String fieldPath = child(path, field.getName());
            if (value instanceof IrisBlockData block) {
                resolveEntry(block, fieldPath);
            } else if (value instanceof Collection<?> values) {
                walkCollection(values, fieldPath);
            } else if (value instanceof Map<?, ?> map) {
                walkMap(map, fieldPath);
            } else if (isIrisObject(value.getClass()) && walkObject(value, fieldPath)) {
                write(field, node, null);
            }
        }
        return drop;
    }

    private void walkKeyList(KeyField key, Collection<?> values, String path) {
        Iterator<?> iterator = values.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Object element = iterator.next();
            String elementPath = index(path, index++);
            if (!(element instanceof String raw) || raw.isBlank()) {
                continue;
            }
            if (key.registry() == CompatRegistry.BLOCK) {
                resolveBlockKey(raw, elementPath);
                continue;
            }
            if (check(key.registry(), raw) == KeyStatus.MISSING) {
                dropped(key.registry(), normalizedKey(raw), elementPath);
                iterator.remove();
            }
        }
    }

    private void walkCollection(Collection<?> values, String path) {
        Iterator<?> iterator = values.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            Object element = iterator.next();
            if (walkValue(element, index(path, index++))) {
                iterator.remove();
            }
        }
    }

    private void walkMap(Map<?, ?> map, String path) {
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (walkValue(entry.getValue(), path + '[' + entry.getKey() + ']')) {
                iterator.remove();
            }
        }
    }

    private boolean walkValue(Object value, String path) {
        if (value == null || selfGated(value.getClass())) {
            return false;
        }
        if (value instanceof IrisBlockData block) {
            resolveEntry(block, path);
            return false;
        }
        if (value instanceof Collection<?> values) {
            walkCollection(values, path);
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            walkMap(map, path);
            return false;
        }
        return isIrisObject(value.getClass()) && walkObject(value, path);
    }

    /** An annotated block key that generates (procedural trees, formations, ...): the chain without a backup. */
    private void resolveBlockKey(String raw, String path) {
        ContentGate.BlockResolution resolution = gate.resolveBlock(raw);
        String key = normalizedKey(raw);
        if (resolution == null) {
            exclude(CompatRegistry.BLOCK, key, path);
        } else if (resolution.source() == ContentGate.BlockResolution.Source.FALLBACK) {
            substituted(key, path, "fallback " + resolution.resolvedKey());
        }
    }

    /** A block entry the way {@code IrisBlockData.getBlockData} resolves it: custom block, chain, then backups. */
    private void resolveEntry(IrisBlockData entry, String path) {
        if (!visited.add(entry)) {
            return;
        }
        String block = entry.getBlock();
        if (block == null || block.isBlank()) {
            return;
        }
        IrisBlockData custom = customBlock(block);
        if (custom != null) {
            if (custom.isCompatExcluded()) {
                exclude(CompatRegistry.BLOCK, causeKey(custom, block), path);
            }
            return;
        }
        String state = entry.stateKey();
        ContentGate.BlockResolution resolution = gate.resolveBlock(state);
        String key = normalizedKey(state);
        if (resolution != null) {
            if (resolution.source() == ContentGate.BlockResolution.Source.FALLBACK) {
                substituted(key, path, "fallback " + resolution.resolvedKey());
            }
            return;
        }
        String revived = resolveBackup(entry.getBackup(), 0);
        if (revived != null) {
            substituted(key, path, "backup " + revived);
            return;
        }
        exclude(CompatRegistry.BLOCK, key, path);
    }

    /** The key a backup chain generates, or null when every link is missing. */
    private String resolveBackup(IrisBlockData backup, int depth) {
        if (backup == null || depth > MAX_BACKUP_DEPTH || !visited.add(backup)) {
            return null;
        }
        String block = backup.getBlock();
        if (block == null || block.isBlank()) {
            return null;
        }
        IrisBlockData custom = customBlock(block);
        if (custom != null) {
            return custom.isCompatExcluded() ? null : ContentGate.normalizeState(block);
        }
        ContentGate.BlockResolution resolution = gate.resolveBlock(backup.stateKey());
        if (resolution != null) {
            return resolution.resolvedKey();
        }
        return resolveBackup(backup.getBackup(), depth + 1);
    }

    private IrisBlockData customBlock(String block) {
        return customLookup ? gate.customBlock(block) : null;
    }

    private static String causeKey(IrisBlockData custom, String block) {
        CompatStatus status = custom.getCompat();
        if (status != null && !status.reasons().isEmpty()) {
            return status.reasons().getFirst().key();
        }
        return normalizedKey(block);
    }

    private KeyStatus check(CompatRegistry registry, String raw) {
        KeyStatus status = gate.status(registry, raw);
        if (status == KeyStatus.UNKNOWN) {
            gate.report().markIncomplete(registry.label() + " registry not ready");
        }
        return status;
    }

    private void exclude(CompatRegistry registry, String key, String detail) {
        excluded = true;
        record(new CompatFinding(registry, key, CompatAction.EXCLUDED, subjectType, subjectKey, detail));
    }

    private void dropped(CompatRegistry registry, String key, String detail) {
        record(new CompatFinding(registry, key, CompatAction.DROPPED, subjectType, subjectKey, detail));
    }

    private void substituted(String key, String path, String how) {
        record(new CompatFinding(CompatRegistry.BLOCK, key, CompatAction.SUBSTITUTED, subjectType, subjectKey, path + " (" + how + ")"));
    }

    private void record(CompatFinding finding) {
        reasons.add(finding);
        gate.report().record(finding);
    }

    private static String normalizedKey(String raw) {
        return ContentGate.baseKey(ContentGate.normalizeState(raw));
    }

    private static String child(String path, String name) {
        return path.isEmpty() ? name : path + '.' + name;
    }

    private static String index(String path, int index) {
        return path + '[' + index + ']';
    }

    private static Object read(Field field, Object node) {
        try {
            return field.get(node);
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static void write(Field field, Object node, Object value) {
        try {
            field.set(node, value);
        } catch (IllegalAccessException | RuntimeException e) {
            IrisLogging.debug("Compat gate could not reset " + field.getDeclaringClass().getSimpleName() + "." + field.getName() + ": " + e.getMessage());
        }
    }

    static ClassPlan plan(Class<?> type) {
        ClassPlan plan = PLANS.get(type);
        if (plan == null) {
            plan = ClassPlan.build(type);
            PLANS.putIfAbsent(type, plan);
        }
        return plan;
    }

    static boolean isIrisObject(Class<?> type) {
        return type.getName().startsWith(IRIS_PACKAGE)
                && !type.isEnum()
                && !type.isRecord()
                && !type.isAnnotation()
                && !selfGated(type);
    }

    /** Units that evaluate their own object pools and edit palettes; the walker stops at them. */
    private static boolean selfGated(Class<?> type) {
        return IrisObjectPlacement.class.isAssignableFrom(type) || IrisStaticObject.class.isAssignableFrom(type);
    }

    private static boolean descendable(Type type) {
        if (type instanceof Class<?> clazz) {
            return isIrisObject(clazz);
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Collection.class.isAssignableFrom(raw)) {
                return arguments.length == 1 && descendable(arguments[0]);
            }
            if (Map.class.isAssignableFrom(raw)) {
                return arguments.length == 2 && descendable(arguments[1]);
            }
            return isIrisObject(raw);
        }
        return false;
    }

    private static boolean isStringList(Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return false;
        }
        return field.getGenericType() instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] == String.class;
    }

    /**
     * The registry an annotated field is checked against, or null when the field carries no checkable key: registrant
     * references, prefix-matching vanilla structure filters, Iris structure resources ({@code @RegistryListStructure}),
     * and the structure-set and native jigsaw pool keys the platform SPI exposes no registry for.
     */
    private static CompatRegistry registryOf(Field field) {
        if (field.isAnnotationPresent(RegistryListBlockType.class)) {
            return CompatRegistry.BLOCK;
        }
        if (field.isAnnotationPresent(RegistryListEntityType.class)) {
            return CompatRegistry.ENTITY;
        }
        if (field.isAnnotationPresent(RegistryListItemType.class)) {
            return CompatRegistry.ITEM;
        }
        if (field.isAnnotationPresent(RegistryListEnchantment.class)) {
            return CompatRegistry.ENCHANTMENT;
        }
        if (field.isAnnotationPresent(RegistryListPotionEffect.class)) {
            return CompatRegistry.POTION_EFFECT;
        }
        if (field.isAnnotationPresent(RegistryListBiome.class)) {
            return CompatRegistry.BIOME;
        }
        RegistryListVanillaStructure vanilla = field.getAnnotation(RegistryListVanillaStructure.class);
        if (vanilla != null && !vanilla.prefixes()) {
            return CompatRegistry.STRUCTURE;
        }
        return null;
    }

    private static boolean open(Field field) {
        try {
            field.setAccessible(true);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    record KeyField(Field field, CompatRegistry registry, boolean list, boolean exclusionSite) {
    }

    /** Per-class field plan: annotated key fields and the fields worth descending into. Immutable once built. */
    static final class ClassPlan {
        private final Class<?> type;
        private final List<KeyField> keys;
        private final List<Field> descents;
        private volatile Object defaults;
        private volatile boolean defaultsBuilt;

        private ClassPlan(Class<?> type, List<KeyField> keys, List<Field> descents) {
            this.type = type;
            this.keys = keys;
            this.descents = descents;
        }

        static ClassPlan build(Class<?> type) {
            List<KeyField> keys = new ArrayList<>();
            List<Field> descents = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                        continue;
                    }
                    if (field.isAnnotationPresent(RegistryListResource.class) || field.isAnnotationPresent(RegistryMapBlockState.class)) {
                        continue;
                    }
                    String site = current.getName() + '#' + field.getName();
                    if (MATCH_ONLY.contains(site)) {
                        continue;
                    }
                    CompatRegistry registry = registryOf(field);
                    if (registry != null) {
                        boolean list = isStringList(field);
                        if ((field.getType() == String.class || list) && open(field)) {
                            keys.add(new KeyField(field, registry, list, EXCLUSION_SITES.contains(site)));
                        }
                        continue;
                    }
                    if (descendable(field.getGenericType()) && open(field)) {
                        descents.add(field);
                    }
                }
            }
            return new ClassPlan(type, List.copyOf(keys), List.copyOf(descents));
        }

        List<KeyField> keys() {
            return keys;
        }

        List<Field> descents() {
            return descents;
        }

        /** The field's value on a fresh instance (what a dropped top-level key resets to), or null. */
        Object defaultOf(Field field) {
            if (!defaultsBuilt) {
                defaults = newInstance();
                defaultsBuilt = true;
            }
            Object instance = defaults;
            return instance == null ? null : read(field, instance);
        }

        private Object newInstance() {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (Throwable e) {
                return null;
            }
        }
    }
}
