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

package art.arcane.iris.modded.api;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedBlockResolution;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link ModdedDataProvider} instances and static block-data aliases, and the resolution path Iris
 * itself calls into.
 * <p>
 * Mods should go through {@link IrisModdedAPI#registerProvider(ModdedDataProvider)} and
 * {@link IrisModdedAPI#registerCustomBlockData(String, String, String)} rather than calling this class directly;
 * the resolution methods here are Iris internals and are public only because the adapter's generation code lives in
 * another package.
 * <p>
 * <b>Threading.</b> Mutation ({@link #register(ModdedDataProvider)},
 * {@link #registerCustomBlockData(String, String, String)}, {@link #discover()}) is serialized on the class
 * monitor. Resolution ({@link #resolveBlock(String)}, {@link #spawnMob}, {@link #processBlockPlacement}) is lock
 * free over a copy-on-write provider list and a concurrent alias map, so it runs on generation threads. Every
 * resolution method catches provider throwables, logs them against the provider's mod id, and continues with the
 * next provider.
 */
public final class ModdedCustomContentRegistry {
    private static final List<ModdedDataProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static final Map<String, BlockState> CUSTOM_BLOCKS = new ConcurrentHashMap<>();
    private static volatile boolean scanned = false;
    private static DiscoveryBatch discoveryBatch;

    private ModdedCustomContentRegistry() {
    }

    /**
     * Registers a static {@code namespace:key} to block-state alias. Invalid identifiers and unparseable states are
     * logged and dropped; null arguments are ignored. See
     * {@link IrisModdedAPI#registerCustomBlockData(String, String, String)}.
     */
    public static synchronized void registerCustomBlockData(String namespace, String key, String state) {
        if (namespace == null || key == null || state == null) {
            return;
        }
        Identifier identifier = Identifier.tryParse(namespace + ":" + key);
        if (identifier == null) {
            ModdedIrisLog.warn("Iris custom block data registration rejected invalid id {}:{}", namespace, key);
            return;
        }
        BlockState parsed;
        try {
            parsed = ModdedBlockResolution.strictParse(state).handle();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris custom block data '{}:{}' has unparseable state '{}'", namespace, key, state, error);
            return;
        }
        DiscoveryBatch activeBatch = discoveryBatch;
        if (activeBatch == null) {
            CUSTOM_BLOCKS.put(identifier.toString(), parsed);
        } else {
            activeBatch.customBlocks.put(identifier.toString(), parsed);
        }
        ModdedIrisLog.info("Iris registered custom block data {}:{} -> {}", namespace, key, state);
    }

    /**
     * Registers a provider, rejecting a duplicate {@link ModdedDataProvider#modId()} with a warning and ignoring
     * null. {@link ModdedDataProvider#init()} runs here; a throwable it raises is logged, not propagated. See
     * {@link IrisModdedAPI#registerProvider(ModdedDataProvider)}.
     */
    public static synchronized void register(ModdedDataProvider provider) {
        if (provider == null) {
            return;
        }
        DiscoveryBatch activeBatch = discoveryBatch;
        if (activeBatch != null) {
            activeBatch.add(provider);
            return;
        }
        for (ModdedDataProvider existing : PROVIDERS) {
            if (existing.modId().equals(provider.modId())) {
                ModdedIrisLog.warn("Iris custom content provider for '{}' already registered; ignoring duplicate", provider.modId());
                return;
            }
        }
        PROVIDERS.add(provider);
        try {
            provider.init();
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris custom content provider '{}' failed to initialize", provider.modId(), error);
        }
        ModdedIrisLog.info("Iris registered custom content provider '{}'", provider.modId());
    }

    /**
     * Runs {@link ServiceLoader} discovery for {@link ModdedDataProvider} against Iris's own class loader, once per
     * process. Called during Iris mod initialization; a second call is a no-op that returns an inert handle.
     * <p>
     * All-or-nothing: a provider whose {@link ModdedDataProvider#init()} throws aborts the pass, restores the
     * previous provider and alias state, logs the failing provider's identity, and rethrows.
     *
     * @return a handle whose {@link Discovery#rollback()} undoes this pass, used by the bootstrap's rollback chain
     */
    public static synchronized Discovery discover() {
        if (scanned) {
            return Discovery.unchanged();
        }
        return discover(ServiceLoader.load(
                ModdedDataProvider.class, ModdedCustomContentRegistry.class.getClassLoader()));
    }

    static synchronized Discovery discover(Iterable<ModdedDataProvider> discoveredProviders) {
        List<ModdedDataProvider> previousProviders = List.copyOf(PROVIDERS);
        Map<String, BlockState> previousCustomBlocks = new LinkedHashMap<>(CUSTOM_BLOCKS);
        boolean previousDiscoveryComplete = scanned;
        DiscoveryBatch batch = new DiscoveryBatch(previousProviders, previousCustomBlocks);
        discoveryBatch = batch;
        ModdedDataProvider failingProvider = null;
        try {
            for (ModdedDataProvider provider : discoveredProviders) {
                failingProvider = provider;
                batch.add(provider);
                failingProvider = null;
            }
            PROVIDERS.addAll(batch.additions);
            CUSTOM_BLOCKS.putAll(batch.customBlocks);
            scanned = true;
            for (ModdedDataProvider provider : batch.additions) {
                ModdedIrisLog.info("Iris registered custom content provider '{}'", provider.modId());
            }
            return new Discovery(previousProviders, previousCustomBlocks,
                    previousDiscoveryComplete, true);
        } catch (Throwable failure) {
            try {
                restore(previousProviders, previousCustomBlocks, previousDiscoveryComplete);
            } catch (Throwable rollbackFailure) {
                if (rollbackFailure != failure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            ModdedIrisLog.warn("Iris custom content provider discovery failed at {}",
                    providerIdentity(failingProvider), failure);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error fatalError) {
                throw fatalError;
            }
            throw new IllegalStateException("Iris custom content provider discovery failed", failure);
        } finally {
            discoveryBatch = null;
        }
    }

    private static String providerIdentity(ModdedDataProvider provider) {
        if (provider == null) {
            return "the provider service loader";
        }
        String className = provider.getClass().getName();
        try {
            String modId = provider.modId();
            return modId == null || modId.isBlank() ? className : "provider '" + modId + "' (" + className + ")";
        } catch (Throwable identityFailure) {
            return className;
        }
    }

    static synchronized boolean hasProvider(String modId) {
        for (ModdedDataProvider provider : PROVIDERS) {
            if (Objects.equals(provider.modId(), modId)) {
                return true;
            }
        }
        return false;
    }

    static boolean discoveryComplete() {
        return scanned;
    }

    /**
     * Whether any provider or alias is registered. Iris checks this to skip custom resolution entirely on a server
     * with no integrating mods.
     */
    public static boolean hasProviders() {
        return !PROVIDERS.isEmpty() || !CUSTOM_BLOCKS.isEmpty();
    }

    /**
     * Every statically registered {@code namespace:key} block alias. Read-only snapshot for pack tooling
     * (schema completion, key validation); not on the resolution path.
     */
    public static List<String> aliasBlockKeys() {
        return List.copyOf(CUSTOM_BLOCKS.keySet());
    }

    /**
     * Every key claimed by a registered provider for {@code type}, in registration order, deduplicated. Read-only
     * snapshot for pack tooling; a provider that throws is logged against its mod id and skipped.
     */
    public static List<String> providerKeys(ModdedDataType type) {
        if (type == null || PROVIDERS.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ModdedDataProvider provider : PROVIDERS) {
            Collection<Identifier> types;
            try {
                types = provider.getTypes(type);
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris custom content provider '{}' failed listing {} types", provider.modId(), type, error);
                continue;
            }
            if (types == null) {
                continue;
            }
            for (Identifier identifier : types) {
                if (identifier != null && seen.add(identifier.toString())) {
                    keys.add(identifier.toString());
                }
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Resolves a pack block key against aliases first, then each ready provider that claims it, in registration
     * order. {@code key} may carry {@code [prop=value]} properties, which are parsed and passed along. Returns null
     * when nothing claims it, which lets the caller fall back to air. Called from generation threads.
     */
    public static ModdedBlockData resolveBlock(String key) {
        if (key == null || (PROVIDERS.isEmpty() && CUSTOM_BLOCKS.isEmpty())) {
            return null;
        }
        Identifier base = parseIdentifier(key);
        if (base == null) {
            return null;
        }
        BlockState custom = CUSTOM_BLOCKS.get(base.toString());
        if (custom != null) {
            return ModdedBlockData.direct(custom);
        }
        if (PROVIDERS.isEmpty()) {
            return null;
        }
        Map<String, String> state = parseState(key);
        for (ModdedDataProvider provider : PROVIDERS) {
            if (!provider.isReady() || !provider.isValidProvider(base, ModdedDataType.BLOCK)) {
                continue;
            }
            try {
                ModdedBlockData resolved = provider.getBlockData(base, state);
                if (resolved != null) {
                    return resolved;
                }
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris custom content provider '{}' failed resolving block {}", provider.modId(), key, error);
            }
        }
        return null;
    }

    /**
     * Delivers a deferred placement to the first ready provider claiming {@code key}; later providers are not
     * consulted for that position. An unparseable key or no matching provider is logged and skipped. Called on the
     * server thread with the chunk loaded.
     */
    public static void processBlockPlacement(Engine engine, ServerLevel level, BlockPos position, String key) {
        Identifier base = parseIdentifier(key);
        if (base == null) {
            ModdedIrisLog.warn("Iris deferred custom block placement rejected invalid id {}", key);
            return;
        }
        Map<String, String> state = parseState(key);
        for (ModdedDataProvider provider : PROVIDERS) {
            if (!provider.isReady() || !provider.isValidProvider(base, ModdedDataType.BLOCK)) {
                continue;
            }
            try {
                provider.processBlockPlacement(new ModdedBlockPlacementContext(
                        engine, level, position.immutable(), base, state, level.getBlockState(position)));
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris custom content provider '{}' failed post-placement for {} at {}", provider.modId(), key, position, error);
            }
            return;
        }
        ModdedIrisLog.warn("Iris deferred custom block placement has no provider for {}", key);
    }

    /**
     * Asks each ready provider claiming {@code key} to spawn a custom entity, returning the first non-null result.
     * Null when no provider claims it or every attempt declined. Called on the server thread.
     */
    public static Entity spawnMob(ServerLevel level, double x, double y, double z, String key) {
        if (PROVIDERS.isEmpty() || level == null || key == null) {
            return null;
        }
        Identifier base = parseIdentifier(key);
        if (base == null) {
            return null;
        }
        for (ModdedDataProvider provider : PROVIDERS) {
            if (!provider.isReady() || !provider.isValidProvider(base, ModdedDataType.ENTITY)) {
                continue;
            }
            try {
                Entity entity = provider.spawnMob(level, x, y, z, base);
                if (entity != null) {
                    return entity;
                }
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris custom content provider '{}' failed spawning mob {}", provider.modId(), key, error);
            }
        }
        return null;
    }

    private static Identifier parseIdentifier(String key) {
        String trimmed = key.trim();
        int bracket = trimmed.indexOf('[');
        String head = bracket < 0 ? trimmed : trimmed.substring(0, bracket);
        return Identifier.tryParse(head);
    }

    private static Map<String, String> parseState(String key) {
        Map<String, String> state = new LinkedHashMap<>();
        int open = key.indexOf('[');
        int close = key.indexOf(']');
        if (open < 0 || close < open) {
            return state;
        }
        String body = key.substring(open + 1, close);
        if (body.isEmpty()) {
            return state;
        }
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            state.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return state;
    }

    private static synchronized void restore(List<ModdedDataProvider> providers,
                                             Map<String, BlockState> customBlocks,
                                             boolean discoveryComplete) {
        PROVIDERS.clear();
        PROVIDERS.addAll(providers);
        CUSTOM_BLOCKS.clear();
        CUSTOM_BLOCKS.putAll(customBlocks);
        scanned = discoveryComplete;
    }

    /**
     * Undo handle for one {@link #discover()} pass, so a failure later in Iris's bootstrap can restore the registry
     * to its pre-discovery state.
     */
    public static final class Discovery {
        private final List<ModdedDataProvider> providers;
        private final Map<String, BlockState> customBlocks;
        private final boolean discoveryComplete;
        private boolean active;

        private Discovery(List<ModdedDataProvider> providers, Map<String, BlockState> customBlocks,
                          boolean discoveryComplete, boolean active) {
            this.providers = providers;
            this.customBlocks = customBlocks;
            this.discoveryComplete = discoveryComplete;
            this.active = active;
        }

        private static Discovery unchanged() {
            return new Discovery(List.of(), Map.of(), true, false);
        }

        /**
         * Restores the providers and aliases captured before the pass. Idempotent; a no-op on a handle from a
         * discovery that did not run.
         */
        public synchronized void rollback() {
            if (!active) {
                return;
            }
            restore(providers, customBlocks, discoveryComplete);
            active = false;
        }
    }

    private static final class DiscoveryBatch {
        private final List<ModdedDataProvider> providers;
        private final List<ModdedDataProvider> additions = new ArrayList<>();
        private final Map<String, BlockState> customBlocks;

        private DiscoveryBatch(List<ModdedDataProvider> providers, Map<String, BlockState> customBlocks) {
            this.providers = new ArrayList<>(providers);
            this.customBlocks = new LinkedHashMap<>(customBlocks);
        }

        private void add(ModdedDataProvider provider) {
            ModdedDataProvider candidate = Objects.requireNonNull(provider,
                    "Iris discovered a null custom content provider");
            String modId = Objects.requireNonNull(candidate.modId(),
                    "Iris custom content provider returned a null mod id");
            for (ModdedDataProvider existing : providers) {
                if (modId.equals(existing.modId())) {
                    ModdedIrisLog.warn("Iris custom content provider for '{}' already registered; ignoring duplicate", modId);
                    return;
                }
            }
            providers.add(candidate);
            additions.add(candidate);
            candidate.init();
        }
    }
}
