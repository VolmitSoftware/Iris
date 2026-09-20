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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityRuntime;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;

import java.util.Collection;
import java.util.Map;

/**
 * Extension point letting a mod resolve its own blocks, items and entities for Iris packs, so a pack can name
 * {@code yourmod:something} and have it placed.
 * <p>
 * Discovered through {@link java.util.ServiceLoader} at Iris mod initialization, or registered imperatively with
 * {@link IrisModdedAPI#registerProvider(ModdedDataProvider)}. For ServiceLoader discovery, ship
 * {@code META-INF/services/art.arcane.iris.modded.api.ModdedDataProvider} listing the implementation's binary
 * name; the class needs a public no-argument constructor.
 * <p>
 * <b>Threading.</b> Implementations must be thread-safe.
 * {@link #getBlockData(String, Map)} is called from generation threads, potentially many at once, for every
 * unresolved key a pack names - it must be fast and must not touch world state.
 * {@link #processBlockPlacement(ModdedBlockPlacementContext)} and
 * {@link #spawnMob(NativeEntityRuntime.CustomSpawn)} are called on the server thread, where
 * touching the level is safe.
 * <p>
 * <b>Failure handling.</b> Iris catches throwables from every callback except {@link #init()} during
 * ServiceLoader discovery, logs them against {@link #modId()}, and carries on with the remaining providers - one
 * broken provider does not stop world generation. A throwable from {@link #init()} during discovery aborts
 * discovery and rolls back every provider registered in that pass.
 */
public interface ModdedDataProvider {
    /**
     * The owning mod's id. Used as the provider's identity: duplicates are rejected, and it labels every log line
     * Iris emits about this provider. Must be non-null and stable; returning null aborts discovery.
     */
    String modId();

    /**
     * Whether this provider can answer lookups yet. Iris skips a provider that reports false rather than treating
     * it as absent, so a provider whose registries populate late can gate itself instead of returning wrong
     * answers. Defaults to true.
     */
    default boolean isReady() {
        return true;
    }

    /**
     * Every identifier this provider can supply for {@code type}. Used for command suggestion and pack tooling, not
     * on the resolution path - {@link #isValidProvider(String, ModdedDataType)} decides that. Return an empty
     * collection rather than null.
     */
    Collection<String> getTypes(ModdedDataType type);

    /**
     * Whether this provider claims {@code id} for {@code type}. Called before every resolution callback, on
     * generation threads, so keep it to a set lookup. A cheap namespace check is usually enough.
     */
    boolean isValidProvider(String id, ModdedDataType type);

    /**
     * Resolves a claimed block identifier into a concrete block state.
     * <p>
     * {@code state} holds the {@code [prop=value]} pairs from the pack's key, already parsed and possibly empty;
     * never null. Return null to decline, in which case Iris tries the next provider and finally falls back to air.
     * Return {@link ModdedBlockData#deferred(ModdedBlockState)} when the real block
     * needs a loaded level - Iris then writes the placeholder state and calls
     * {@link #processBlockPlacement(ModdedBlockPlacementContext)} later. Called from generation threads.
     */
    default ModdedBlockData getBlockData(String blockId, Map<String, String> state) {
        return null;
    }

    /**
     * Finishes a deferred placement once the chunk is loaded: swap in the real block, attach a block entity, seed
     * NBT.
     * <p>
     * Called on the server thread, once per deferred position, by the first provider that claims the identifier -
     * later providers are not consulted for that position. Only fires for states returned as deferred.
     */
    default void processBlockPlacement(ModdedBlockPlacementContext context) {
    }

    /**
     * Spawns a claimed custom entity at the given position. Return null to decline and let the next provider try.
     * Called on the server thread from Iris's entity spawning.
     */
    default NativeSpawnedEntity spawnMob(NativeEntityRuntime.CustomSpawn request) {
        return null;
    }

    /**
     * One-time setup, called by Iris immediately after this provider is accepted. A throwable raised here aborts
     * ServiceLoader discovery; when registered imperatively it is logged and the provider stays registered.
     */
    default void init() {
    }
}
