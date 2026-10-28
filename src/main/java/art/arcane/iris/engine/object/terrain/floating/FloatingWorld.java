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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.modules.carve.CarvingSettings;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.modules.floating.FloatingChildSettings;
import art.arcane.iris.modules.floating.MergeFloatingChildrenSettings;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackBiome;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the floating islands of one world are made from: the world's layout, noise and seeds, the floating children
 * each biome declares, and the {@link ResolvedFloatingChild} of every child met, one per decoded entry. One per world
 * ({@link #of}).
 */
public final class FloatingWorld {
    private static final long BASE_SEED_SALT = 0x5EED_F107_00F1B10CL;

    private final PackResources resources;
    private final RunningWorld running;
    private final long baseSeed;
    private final long carveSeed;
    private final int minWorldY;
    private final List<CarvingEntry> dimensionCarving;
    private final Map<FloatingChildBiome, ResolvedFloatingChild> resolved = new ConcurrentHashMap<>();

    FloatingWorld(PackResources resources, RunningWorld running, long terrainSeed,
                  long carveSeed, int minWorldY, List<CarvingEntry> dimensionCarving) {
        this.resources = resources;
        this.running = running;
        this.baseSeed = terrainSeed ^ BASE_SEED_SALT;
        this.carveSeed = carveSeed;
        this.minWorldY = minWorldY;
        this.dimensionCarving = List.copyOf(dimensionCarving);
    }

    /** The floating islands of {@code world}. */
    public static FloatingWorld of(PackWorld world) {
        return world.state(FloatingWorld.class, FloatingWorld::create);
    }

    private static FloatingWorld create(PackWorld world) {
        return new FloatingWorld(world.resources(), world.running().orElseThrow(), world.seed(IrisSeedPurposes.TERRAIN),
                world.seed(IrisSeedPurposes.CARVE), world.minWorldY(), dimensionCarvingOf(world));
    }

    private static List<CarvingEntry> dimensionCarvingOf(PackWorld world) {
        return world.settings().declared(CarvingSettings.class).dimension()
                .map(CarvingSettings::entries)
                .orElse(List.of());
    }

    /** The height the terrain generators of {@code biome} give the column when shaped with {@link #baseSeed}. */
    public double biomeHeight(ResourceId biome, int worldX, int worldZ) {
        return running.layout().biomeHeight(biome, worldX, worldZ, baseSeed);
    }

    public NoiseFactory noise() {
        return resources.noise();
    }

    /** The seed every island's noise and column random derive from. */
    public long baseSeed() {
        return baseSeed;
    }

    long carveSeed() {
        return carveSeed;
    }

    /** The Y in the world of the dimension's bottom block. */
    public int minWorldY() {
        return minWorldY;
    }

    /** The biome of the column's surface, the biome an island grows from; any column of the world. */
    public ResourceId surfaceBiome(int worldX, int worldZ) {
        return running.layout().surfaceBiome(worldX, worldZ);
    }

    /** The biome the pack names {@code key}; empty when the pack has none. */
    public Optional<ResourceId> biome(String key) {
        return resources.find(PackResources.BIOMES, resources.idOf(key)).map(PackBiome::id);
    }

    /** The floating children {@code biome} declares, in the order it lists them; none when it declares none. */
    public List<FloatingChildBiome> declaredBy(ResourceId biome) {
        return running.settings().declaredBy(biome, FloatingChildSettings.class).map(FloatingChildSettings::children).orElse(List.of());
    }

    /** Whether {@code biome} samples every floating child on its own and unions their islands instead of picking one per column. */
    public boolean merges(ResourceId biome) {
        return running.settings().declaredBy(biome, MergeFloatingChildrenSettings.class).map(MergeFloatingChildrenSettings::merge).orElse(false);
    }

    public ResolvedFloatingChild resolve(FloatingChildBiome settings) {
        return resolved.computeIfAbsent(settings, declared -> new ResolvedFloatingChild(declared, this));
    }

    public List<ResolvedFloatingChild> resolve(List<FloatingChildBiome> settings) {
        return settings.stream().map(this::resolve).toList();
    }

    /** The surface object placements {@code biome} declares. */
    public List<ObjectPlacement> surfaceObjects(ResourceId biome) {
        return surfaceObjects(running.settings().declaredBy(biome, ObjectSettings.class));
    }

    /** The surface object placements of the declared object settings; none when nothing is declared. */
    public static List<ObjectPlacement> surfaceObjects(Optional<ObjectSettings> declared) {
        return declared.map(ObjectSettings::objects).orElse(List.of()).stream()
                .filter(placement -> placement.carvingSupport().supportsSurface())
                .toList();
    }

    /** The dimension's carving entry with this id (the last of several with one id); empty when it declares none. */
    Optional<CarvingEntry> dimensionCarving(String id) {
        CarvingEntry found = null;
        for (CarvingEntry entry : dimensionCarving) {
            if (entry != null && !entry.id().isBlank() && entry.id().trim().equals(id)) {
                found = entry;
            }
        }
        return Optional.ofNullable(found);
    }

    /** The cave profile {@code biome} declares; null when it declares none. */
    CaveProfile caveProfile(ResourceId biome) {
        return running.settings().declaredBy(biome, CaveProfile.class).orElse(null);
    }
}
