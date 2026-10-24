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

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.PackWorld;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Places the objects the dimension's regions and biomes declare, one chunk at a time, into the mantle of a world: the worker
 * of {@link ObjectModule}, one per world. It also works out which objects a chunk would get, without placing them, and how far
 * the pack's objects reach.
 */
public final class ObjectPlacing {
    private static final int CHUNK_CENTER_OFFSET_BLOCKS = 8;
    private static final int CHUNK_SHIFT = 4;
    private static final double CHANCE_JITTER = 0.005;
    /** A guess draws from the biome's objects before the region's, as placing does. */
    private static final Level[] GUESSED_LEVELS = {Level.BIOME, Level.REGION};

    private final PackWorld world;
    private final ObjectHost host;
    private final ModuleLog log;

    private ObjectPlacing(PackWorld world) {
        this.world = world;
        this.host = world.extension(ObjectHost.class).orElseThrow();
        this.log = world.log();
    }

    /** The object placing of {@code world}. */
    public static ObjectPlacing of(PackWorld world) {
        return world.state(ObjectPlacing.class, ObjectPlacing::new);
    }

    /** Places what originates in the chunk {@code context} generates. */
    void place(MantleStageContext context) {
        new ChunkObjectPlacing(host, log, context).place();
    }

    /** The keys of the objects the chunk would get, as far as the chunk's surface objects go. */
    public Set<String> guess(int chunkX, int chunkZ) {
        RNG rng = host.chunkRandom(chunkX, chunkZ);
        int centerX = (chunkX << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        int centerZ = (chunkZ << CHUNK_SHIFT) + CHUNK_CENTER_OFFSET_BLOCKS;
        SettingsStack<ObjectSettings> declared = world.running().orElseThrow().settings().at(ObjectSettings.class, centerX, centerZ);
        Set<String> keys = new HashSet<>();
        for (Level level : GUESSED_LEVELS) {
            declared.level(level).ifPresent(settings -> guessFrom(settings, rng, chunkX, chunkZ, keys));
        }
        return keys;
    }

    private void guessFrom(ObjectSettings settings, RNG rng, int chunkX, int chunkZ, Set<String> keys) {
        for (ObjectPlacement placement : settings.objects()) {
            if (placement.carvingSupport().supportsSurface() && rng.chance(placement.chance() + rng.d(-CHANCE_JITTER, CHANCE_JITTER))) {
                keys.addAll(guessPlacedKeys(rng, chunkX, chunkZ, placement));
            }
        }
    }

    private Set<String> guessPlacedKeys(RNG rng, int x, int z, ObjectPlacement placement) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < host.density(placement, rng, x, z); i++) {
            Optional<PlaceableObject> picked = host.pickScaled(placement, rng);
            picked.ifPresent(object -> keys.add(object.key()));
        }
        return keys;
    }

    /** How far, in blocks along X and Z, placements may write beyond their chunk. */
    public int reach(PackSettings settings) {
        return new PlacementReach(host, log).ofPack(settings);
    }
}
