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

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Answers "does this pack key exist on the running server, and if not what do we generate instead" for one pack
 * instance, and records every decision in the pack's {@link PackCompatReport}.
 * <p>
 * Existence is checked against the live {@link PlatformRegistries} only - never a version number - so the same pack
 * gates identically on Bukkit and on the modded loaders for the same Minecraft version, and mods that add or remove
 * content are covered for free. {@link KeyStatus#UNKNOWN} (registry not bound or not ready) never produces a finding.
 * <p>
 * Block resolution chain: strict registry lookup, legacy rename table (not reported), dimension {@code blockFallbacks}
 * (reported), then {@code null} (missing). The per-entry {@code backup} of an {@code IrisBlockData} is applied by that
 * class and by the walker around this chain. Resolutions are cached per normalized state.
 */
public final class ContentGate {
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final String BLOCK_FALLBACKS_FIELD = "blockFallbacks";
    private static final Object MISSING = new Object();
    /** Legacy Bukkit potion effect enum names (lowercase) to the current key path; the Bukkit-typed
     *  {@code PotionEffectTypes} reads this table so it stays unloaded on the modded loaders. */
    public static final Map<String, String> POTION_EFFECT_ALIASES = Map.of(
            "slow", "slowness",
            "fast_digging", "haste",
            "slow_digging", "mining_fatigue",
            "increase_damage", "strength",
            "heal", "instant_health",
            "harm", "instant_damage",
            "jump", "jump_boost",
            "confusion", "nausea",
            "damage_resistance", "resistance");

    private final PlatformRegistries registries;
    private final Supplier<Map<String, String>> blockFallbacks;
    private final PackCompatReport report;
    private final Function<String, IrisBlockData> customBlocks;
    private final Map<CompatRegistry, Set<String>> keySets = new ConcurrentHashMap<>();
    private final Map<String, Object> resolutions = new ConcurrentHashMap<>();
    private final Map<String, MissingBlockState> placeholders = new ConcurrentHashMap<>();
    // Keys with no blocks/ registrant: the loader lists the folder on every miss, and every walked entry asks.
    private final Set<String> customMisses = ConcurrentHashMap.newKeySet();
    private volatile boolean readyKnown;
    private volatile Map<String, String> fallbackCache;

    public ContentGate(PlatformRegistries registries, Supplier<Map<String, String>> blockFallbacks, PackCompatReport report) {
        this(registries, blockFallbacks, report, null);
    }

    public ContentGate(PlatformRegistries registries, Map<String, String> blockFallbacks, PackCompatReport report) {
        this(registries, () -> blockFallbacks == null ? Map.of() : blockFallbacks, report, null);
    }

    /**
     * @param customBlocks resolves a pack block key to its {@code blocks/} registrant, or null; consulted before the
     *                     registry the way {@code IrisBlockData.getBlockData} does. May be null.
     */
    public ContentGate(PlatformRegistries registries, Supplier<Map<String, String>> blockFallbacks, PackCompatReport report,
                       Function<String, IrisBlockData> customBlocks) {
        this.registries = registries;
        this.blockFallbacks = blockFallbacks == null ? Map::of : blockFallbacks;
        this.report = report == null ? new PackCompatReport() : report;
        this.customBlocks = customBlocks;
    }

    /** Gate for a pack folder: live platform registries, fallbacks read from the pack's dimension JSON. */
    public static ContentGate forData(IrisData data) {
        PlatformRegistries registries = IrisPlatforms.isBound() ? IrisPlatforms.get().registries() : null;
        File folder = data == null ? null : data.getDataFolder();
        PackCompatReport report = data == null ? new PackCompatReport() : data.getCompatReport();
        Function<String, IrisBlockData> customBlocks = data == null ? null : key -> {
            ResourceLoader<IrisBlockData> loader = data.getBlockLoader();
            return loader == null ? null : loader.load(key, false);
        };
        return new ContentGate(registries, () -> readBlockFallbacks(folder), report, customBlocks);
    }

    public PackCompatReport report() {
        return report;
    }

    /** True when the registry can be consulted; false means every status is {@link KeyStatus#UNKNOWN}. */
    public boolean ready() {
        if (readyKnown) {
            return true;
        }
        if (registries == null) {
            return false;
        }
        try {
            List<String> blocks = registries.blockKeys();
            boolean ready = blocks != null && !blocks.isEmpty();
            if (ready) {
                readyKnown = true;
            }
            return ready;
        } catch (Throwable e) {
            return false;
        }
    }

    public KeyStatus block(String state) {
        String normalized = normalizeState(state);
        if (normalized == null) {
            return KeyStatus.MISSING;
        }
        if (!ready()) {
            return KeyStatus.UNKNOWN;
        }
        return lookup(normalized) != null ? KeyStatus.PRESENT : KeyStatus.MISSING;
    }

    public KeyStatus item(String key) {
        return statusOf(CompatRegistry.ITEM, key);
    }

    public KeyStatus entity(String key) {
        return statusOf(CompatRegistry.ENTITY, key);
    }

    public KeyStatus biome(String key) {
        return statusOf(CompatRegistry.BIOME, key);
    }

    public KeyStatus structure(String key) {
        return statusOf(CompatRegistry.STRUCTURE, key);
    }

    public KeyStatus enchantment(String key) {
        return statusOf(CompatRegistry.ENCHANTMENT, key);
    }

    /** Potion effects match on the key path only and accept the legacy Bukkit enum names, as {@code IrisEffect} does. */
    public KeyStatus potionEffect(String key) {
        String normalized = normalizeState(key);
        if (normalized == null) {
            return KeyStatus.MISSING;
        }
        if (!ready()) {
            return KeyStatus.UNKNOWN;
        }
        Set<String> paths = keySet(CompatRegistry.POTION_EFFECT);
        if (paths == null) {
            return KeyStatus.UNKNOWN;
        }
        String path = pathOf(baseKey(normalized));
        return paths.contains(POTION_EFFECT_ALIASES.getOrDefault(path, path)) ? KeyStatus.PRESENT : KeyStatus.MISSING;
    }

    /** Status of {@code key} in {@code registry}; {@link CompatRegistry#BLOCK} goes through {@link #block(String)}. */
    public KeyStatus status(CompatRegistry registry, String key) {
        return switch (registry) {
            case BLOCK -> block(key);
            case ITEM -> item(key);
            case ENTITY -> entity(key);
            case BIOME -> biome(key);
            case STRUCTURE -> structure(key);
            case ENCHANTMENT -> enchantment(key);
            case POTION_EFFECT -> potionEffect(key);
        };
    }

    /**
     * Resolved block for a pack state string, or {@code null} when it is missing on this server after the whole chain
     * (or the registry is not ready). {@link BlockResolution#substituted()} is true only for a dimension fallback; a
     * legacy rename is visible through {@link BlockResolution#source()} and is never reported.
     */
    public BlockResolution resolveBlock(String state) {
        String normalized = normalizeState(state);
        if (normalized == null || !ready()) {
            return null;
        }
        Object cached = resolutions.get(normalized);
        if (cached == null) {
            BlockResolution resolved = resolveUncached(normalized);
            cached = resolved == null ? MISSING : resolved;
            resolutions.putIfAbsent(normalized, cached);
        }
        return cached == MISSING ? null : (BlockResolution) cached;
    }

    /**
     * {@link #resolveBlock(String)}, or a {@link MissingBlockState} carrying the key when the whole chain misses, so
     * object palettes and {@code edit} find lists keep the key an edit rule can still rewrite. Null only when the
     * registry is not ready (callers keep their plain lookup) or the state is blank.
     */
    public PlatformBlockState resolveBlockOrPlaceholder(String state) {
        String normalized = normalizeState(state);
        if (normalized == null || !ready()) {
            return null;
        }
        BlockResolution resolution = resolveBlock(normalized);
        if (resolution != null) {
            return resolution.state();
        }
        MissingBlockState placeholder = placeholders.get(normalized);
        if (placeholder == null) {
            PlatformBlockState air;
            try {
                air = registries.air();
            } catch (Throwable e) {
                air = null;
            }
            if (air == null) {
                return null;
            }
            placeholder = MissingBlockState.of(normalized, air);
            MissingBlockState raced = placeholders.putIfAbsent(normalized, placeholder);
            if (raced != null) {
                placeholder = raced;
            }
        }
        return placeholder;
    }

    private BlockResolution resolveUncached(String normalized) {
        PlatformBlockState direct = lookup(normalized);
        if (direct != null) {
            return new BlockResolution(direct, normalized, false, null, BlockResolution.Source.REGISTRY);
        }
        BlockResolution renamed = resolveRename(normalized);
        if (renamed != null) {
            return renamed;
        }
        String fallback = fallbacks().get(baseKey(normalized));
        if (fallback != null) {
            String normalizedFallback = normalizeState(fallback);
            PlatformBlockState substitute = normalizedFallback == null ? null : lookup(normalizedFallback);
            if (substitute != null) {
                return new BlockResolution(substitute, normalizedFallback, true, normalized, BlockResolution.Source.FALLBACK);
            }
        }
        return null;
    }

    private BlockResolution resolveRename(String normalized) {
        String candidate = normalized;
        for (int hop = 0; hop < LegacyBlockRenames.MAX_HOPS; hop++) {
            String supplement = normalizeState(LegacyBlockRenames.supplementFor(candidate));
            if (supplement == null) {
                return null;
            }
            PlatformBlockState state = lookup(supplement);
            if (state != null) {
                return new BlockResolution(state, supplement, false, null, BlockResolution.Source.RENAME);
            }
            candidate = supplement;
        }
        return null;
    }

    /** The {@code blocks/} registrant for a pack block key, or null when there is none or no loader is attached. */
    public IrisBlockData customBlock(String key) {
        if (customBlocks == null || key == null || key.isBlank() || customMisses.contains(key)) {
            return null;
        }
        try {
            IrisBlockData custom = customBlocks.apply(key);
            if (custom == null) {
                customMisses.add(key);
            }
            return custom;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    /** The dimension {@code blockFallbacks} map, base key to state, normalized. */
    public Map<String, String> fallbacks() {
        Map<String, String> cached = fallbackCache;
        if (cached == null) {
            Map<String, String> raw = blockFallbacks.get();
            Map<String, String> normalized = new LinkedHashMap<>();
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    String key = normalizeState(entry.getKey());
                    if (key != null && entry.getValue() != null) {
                        normalized.put(baseKey(key), entry.getValue());
                    }
                }
            }
            cached = Collections.unmodifiableMap(normalized);
            fallbackCache = cached;
        }
        return cached;
    }

    /** Forget cached fallbacks, key sets, resolutions, placeholders and the readiness latch (studio hotload). */
    public void invalidate() {
        fallbackCache = null;
        readyKnown = false;
        keySets.clear();
        resolutions.clear();
        placeholders.clear();
        customMisses.clear();
    }

    /**
     * Evaluates a freshly loaded registrant against the policy table, records findings, drops entries the table says
     * to drop, and returns the status the caller stores on the registrant. A registry that is not ready produces no
     * findings and no mutation; the report is marked incomplete instead.
     */
    public CompatStatus evaluate(IrisRegistrant registrant) {
        if (registrant == null) {
            return CompatStatus.OK;
        }
        if (!ready()) {
            report.markIncomplete("registry not ready");
            return CompatStatus.OK;
        }
        return new CompatWalker(this, registrant).run();
    }

    /** Reads {@code dimensions/*.json} {@code blockFallbacks} objects straight from disk, without the loader. */
    public static Map<String, String> readBlockFallbacks(File packFolder) {
        if (packFolder == null) {
            return Map.of();
        }
        File[] dimensions = new File(packFolder, DIMENSIONS_FOLDER).listFiles((dir, name) -> name.endsWith(".json"));
        if (dimensions == null || dimensions.length == 0) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>();
        for (File file : dimensions) {
            try {
                JSONObject json = new JSONObject(IO.readAll(file));
                if (!json.has(BLOCK_FALLBACKS_FIELD)) {
                    continue;
                }
                JSONObject fallbacks = json.getJSONObject(BLOCK_FALLBACKS_FIELD);
                for (String key : fallbacks.keySet()) {
                    Object value = fallbacks.get(key);
                    if (value instanceof String state && !state.isBlank()) {
                        merged.putIfAbsent(key, state);
                    }
                }
            } catch (Throwable e) {
                IrisLogging.warn("Could not read blockFallbacks from " + file.getName() + ": " + e.getMessage());
            }
        }
        return merged.isEmpty() ? Map.of() : Collections.unmodifiableMap(merged);
    }

    /** Normalizes block names and vanilla properties, preserving external property case; null for blank input. */
    public static String normalizeState(String state) {
        if (state == null) {
            return null;
        }
        String trimmed = state.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int props = trimmed.indexOf('[');
        String base = (props < 0 ? trimmed : trimmed.substring(0, props)).toLowerCase(Locale.ROOT);
        String rest = props < 0 ? "" : trimmed.substring(props);
        if (base.indexOf(':') < 0) {
            base = DEFAULT_NAMESPACE + ':' + base;
        }
        if (base.startsWith(DEFAULT_NAMESPACE + ':')) {
            rest = rest.toLowerCase(Locale.ROOT);
        }
        return base + rest;
    }

    /** Strips the {@code [props]} section. */
    public static String baseKey(String normalizedState) {
        if (normalizedState == null) {
            return null;
        }
        int props = normalizedState.indexOf('[');
        return props < 0 ? normalizedState : normalizedState.substring(0, props);
    }

    private static String pathOf(String baseKey) {
        int namespace = baseKey.indexOf(':');
        return namespace < 0 ? baseKey : baseKey.substring(namespace + 1);
    }

    private PlatformBlockState lookup(String normalized) {
        try {
            return registries.blockOrNull(normalized, false);
        } catch (Throwable e) {
            return null;
        }
    }

    private KeyStatus statusOf(CompatRegistry registry, String key) {
        String normalized = normalizeState(key);
        if (normalized == null) {
            return KeyStatus.MISSING;
        }
        if (!ready()) {
            return KeyStatus.UNKNOWN;
        }
        Set<String> keys = keySet(registry);
        if (keys == null) {
            return KeyStatus.UNKNOWN;
        }
        return keys.contains(baseKey(normalized)) ? KeyStatus.PRESENT : KeyStatus.MISSING;
    }

    /** Normalized keys of a registry (paths only for potion effects), or null while that registry is empty. */
    private Set<String> keySet(CompatRegistry registry) {
        Set<String> cached = keySets.get(registry);
        if (cached != null) {
            return cached;
        }
        List<String> keys;
        try {
            keys = switch (registry) {
                case BLOCK -> registries.blockKeys();
                case ITEM -> registries.itemKeys();
                case ENTITY -> registries.entityKeys();
                case BIOME -> registries.biomeKeys();
                case STRUCTURE -> registries.structureKeys();
                case ENCHANTMENT -> registries.enchantmentKeys();
                case POTION_EFFECT -> registries.potionEffectKeys();
            };
        } catch (Throwable e) {
            return null;
        }
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        Set<String> built = new HashSet<>(keys.size() * 2);
        for (String key : keys) {
            String normalized = normalizeState(key);
            if (normalized != null) {
                String base = baseKey(normalized);
                built.add(registry == CompatRegistry.POTION_EFFECT ? pathOf(base) : base);
            }
        }
        Set<String> frozen = Collections.unmodifiableSet(built);
        keySets.putIfAbsent(registry, frozen);
        return frozen;
    }

    /**
     * @param state           the state to generate
     * @param resolvedKey     the normalized key that produced {@code state}
     * @param substituted     true when a dimension fallback produced {@code state} (reported)
     * @param substitutedFrom the missing key the fallback replaced, or null
     * @param source          which step of the chain answered
     */
    public record BlockResolution(PlatformBlockState state, String resolvedKey, boolean substituted, String substitutedFrom,
                                  Source source) {
        public enum Source {
            REGISTRY,
            RENAME,
            FALLBACK
        }

        public BlockResolution {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(source, "source");
        }

        public BlockResolution(PlatformBlockState state, String resolvedKey, boolean substituted, String substitutedFrom) {
            this(state, resolvedKey, substituted, substitutedFrom, substituted ? Source.FALLBACK : Source.REGISTRY);
        }
    }
}
