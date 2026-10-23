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

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.floating.MergeFloatingChildrenSettings;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import com.volmit.noise.NoiseFactory;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the floating islands of one world are made from: the world's hosts, noise and seeds, the floating children each
 * biome declares, and the {@link ResolvedFloatingChild} of every child met, one per decoded entry. One per world ({@link #of}).
 */
public final class FloatingWorld {
    private static final long BASE_SEED_SALT = 0x5EED_F107_00F1B10CL;

    private final FloatingHost host;
    private final ObjectHost objects;
    private final NoiseFactory noise;
    private final long baseSeed;
    private final long carveSeed;
    private final int minHeight;
    private final List<CarvingEntry> dimensionCarving;
    private final Map<FloatingChildBiome, ResolvedFloatingChild> resolved = new ConcurrentHashMap<>();

    FloatingWorld(FloatingHost host, ObjectHost objects, NoiseFactory noise, long terrainSeed, long carveSeed, int minHeight,
                  List<CarvingEntry> dimensionCarving) {
        this.host = host;
        this.objects = objects;
        this.noise = noise;
        this.baseSeed = terrainSeed ^ BASE_SEED_SALT;
        this.carveSeed = carveSeed;
        this.minHeight = minHeight;
        this.dimensionCarving = List.copyOf(dimensionCarving);
    }

    /** The floating islands of {@code world}. */
    public static FloatingWorld of(PackWorld world) {
        return world.state(FloatingWorld.class, FloatingWorld::create);
    }

    private static FloatingWorld create(PackWorld world) {
        return new FloatingWorld(FloatingHost.of(world), ObjectHost.of(world), world.resources().noise(), IrisSeed.TERRAIN.of(world),
                IrisSeed.CARVE.of(world), world.minHeight(), dimensionCarvingOf(world));
    }

    private static List<CarvingEntry> dimensionCarvingOf(PackWorld world) {
        return world.settings().declared(CarvingSettings.class).dimension()
                .map(CarvingSettings::entries)
                .orElse(List.of());
    }

    public FloatingHost host() {
        return host;
    }

    public ObjectHost objects() {
        return objects;
    }

    public NoiseFactory noise() {
        return noise;
    }

    /** The seed every island's noise and column random derive from. */
    public long baseSeed() {
        return baseSeed;
    }

    long carveSeed() {
        return carveSeed;
    }

    /** The platform's Y of the dimension's bottom. */
    public int minHeight() {
        return minHeight;
    }

    /** The floating children {@code biome} declares, in the order it lists them; none when it declares none. */
    public List<FloatingChildBiome> declaredBy(ResourceId biome) {
        return objects.biomeDeclaration(biome, FloatingChildSettings.class).map(FloatingChildSettings::children).orElse(List.of());
    }

    /** Whether {@code biome} samples every floating child on its own and unions their islands instead of picking one per column. */
    public boolean merges(ResourceId biome) {
        return objects.biomeDeclaration(biome, MergeFloatingChildrenSettings.class).map(MergeFloatingChildrenSettings::merge).orElse(false);
    }

    public ResolvedFloatingChild resolve(FloatingChildBiome settings) {
        return resolved.computeIfAbsent(settings, declared -> new ResolvedFloatingChild(declared, this));
    }

    public List<ResolvedFloatingChild> resolve(List<FloatingChildBiome> settings) {
        return settings.stream().map(this::resolve).toList();
    }

    /** The surface object placements {@code biome} declares. */
    public static List<ObjectPlacement> surfaceObjects(ObjectHost objects, ResourceId biome) {
        return objects.biomeDeclaration(biome, ObjectSettings.class).map(ObjectSettings::objects).orElse(List.of()).stream()
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
        return objects.biomeDeclaration(biome, CaveProfile.class).orElse(null);
    }
}
