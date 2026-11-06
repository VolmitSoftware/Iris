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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.modules.objects.ObjectPool;
import art.arcane.iris.modules.objects.ObjectSeparation;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.object.PlaceableObject;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Places the objects the dimension's regions and biomes declare, one chunk at a time, into the mantle of a world: the worker
 * of {@link ObjectModule}, one per world. It also works out which objects a chunk would get, without placing them.
 */
public final class ObjectPlacing {
    private static final int CHUNK_CENTER_OFFSET_BLOCKS = 8;
    private static final int CHUNK_SHIFT = 4;
    private static final double CHANCE_JITTER = 0.005;
    /** A guess draws from the biome's objects before the region's, as placing does. */
    private static final Level[] GUESSED_LEVELS = {Level.BIOME, Level.REGION};

    private final PackWorld world;
    private final ObjectPool objects;
    private final ObjectSeparation separation;
    private final ModuleLog log;

    private ObjectPlacing(PackWorld world) {
        this.world = world;
        this.objects = ObjectPool.of(world);
        this.separation = ObjectSeparation.of(world);
        this.log = world.log();
    }

    /** The object placing of {@code world}. */
    public static ObjectPlacing of(PackWorld world) {
        return world.state(ObjectPlacing.class, ObjectPlacing::new);
    }

    /** Places what originates in the chunk {@code context} generates. */
    void place(MantleStageContext context) {
        new ChunkObjectPlacing(objects, log, context).place();
    }

    /** The keys of the objects the chunk would get, as far as the chunk's surface objects go. */
    public Set<String> guess(int chunkX, int chunkZ) {
        RNG rng = objects.chunkRandom(chunkX, chunkZ);
        int centerX = (chunkX << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        int centerZ = (chunkZ << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        FeatureSettings worldSettings = world.running().orElseThrow().settings();
        SettingsStack<ObjectSettings> declared = worldSettings.at(ObjectSettings.class, centerX, centerZ);
        Set<String> keys = new HashSet<>();
        for (Level level : GUESSED_LEVELS) {
            declared.level(level).ifPresent(settings -> guessFrom(settings, worldSettings, rng, chunkX, chunkZ, keys));
        }
        return keys;
    }

    private void guessFrom(ObjectSettings settings, FeatureSettings worldSettings, RNG rng, int chunkX, int chunkZ, Set<String> keys) {
        int centerX = (chunkX << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        int centerZ = (chunkZ << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        for (ObjectPlacement placement : settings.objects()) {
            if (!placement.carvingSupport().supportsSurface()) {
                continue;
            }
            if (placement.isSeparated()) {
                guessSeparatedKey(chunkX, chunkZ, placement, worldSettings).ifPresent(keys::add);
            } else if (rng.chance(separation.chanceAt(placement, centerX, centerZ) + rng.d(-CHANCE_JITTER, CHANCE_JITTER))) {
                keys.addAll(guessPlacedKeys(rng, chunkX, chunkZ, placement));
            }
        }
    }

    private Optional<String> guessSeparatedKey(int chunkX, int chunkZ, ObjectPlacement placement, FeatureSettings worldSettings) {
        return separation.keptCandidate(chunkX, chunkZ, placement, worldSettings)
                .flatMap(candidate -> objects.pickScaled(placement, candidate.rng()))
                .map(PlaceableObject::name);
    }

    private Set<String> guessPlacedKeys(RNG rng, int x, int z, ObjectPlacement placement) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < objects.density(placement, x, z); i++) {
            Optional<PlaceableObject> picked = objects.pickScaled(placement, rng);
            picked.ifPresent(object -> keys.add(object.name()));
        }
        return keys;
    }
}
