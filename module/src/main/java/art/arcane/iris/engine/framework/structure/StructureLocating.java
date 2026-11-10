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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.service.LocateResult;
import com.volmit.service.StructureLocator;
import com.volmit.geometry.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * How {@code iris:structure} lets Iris find its structures: the structures of the pack's {@code structures} folder,
 * the ones the world's placements assemble from jigsaw pools, and their nearest start, searched with
 * {@link IrisStructureLocator}. The keys it places also name the platform structures its native placements start and
 * the ones its structures stand in for; it locates those too. The platform's own locate of a natively started
 * structure goes through {@link #locateStart}, which searches the native placements' rings.
 * <p>
 * Iris's locator names structures by key: a pack structure by its pack key, a platform structure by its
 * {@code namespace:path}. A key with a namespace is that id; any other is the pack's.
 */
public final class StructureLocating implements StructureLocator {
    private static final char NAMESPACE_SEPARATOR = ':';

    @Override
    public Set<ResourceId> structures(PackResources pack) {
        Set<ResourceId> known = new LinkedHashSet<>();
        for (StructureAssembly provider : pack.services(StructureAssembly.class)) {
            known.addAll(provider.structures(pack));
        }
        return known;
    }

    @Override
    public Set<ResourceId> locatable(PackWorld world) {
        return idsOf(world.resources(), IrisStructureLocator.locatableEditableKeys(world));
    }

    @Override
    public Set<ResourceId> placed(PackWorld world) {
        return idsOf(world.resources(), IrisStructureLocator.placedKeysAndAliases(world));
    }

    @Override
    public LocateResult locate(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRadiusChunks) {
        String key = keyOf(world.resources(), IrisStructureLocator.placedKeysAndAliases(world), structure);
        return apiResult(world, IrisStructureLocator.locate(world, key, fromX, fromZ, maxRadiusChunks));
    }

    @Override
    public Set<ResourceId> startedStructures(PackWorld world) {
        return platformIds(IrisStructureLocator.nativeKeys(world));
    }

    @Override
    public Set<ResourceId> locatableStarts(PackWorld world) {
        return platformIds(IrisStructureLocator.locatableNativeKeys(world));
    }

    /** Searches the rings of the placements that start {@code structure} natively, as the platform's own locate does. */
    @Override
    public LocateResult locateStart(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRings,
                                    ChunkFilter accepts) {
        return apiResult(world, IrisStructureLocator.locateInPlacementRings(
                world, structure.toString(), fromX, fromZ, maxRings, accepts::accepts));
    }

    private static LocateResult apiResult(PackWorld world, IrisStructureLocator.LocateResult result) {
        return switch (result.status()) {
            case FOUND -> LocateResult.found(new BlockPosition(result.originX(), result.baseY() - world.minWorldY(), result.originZ()));
            case NOT_FOUND -> LocateResult.NOT_FOUND;
            case SEARCH_LIMIT_REACHED -> LocateResult.SEARCH_LIMIT_REACHED;
        };
    }

    private static Set<ResourceId> platformIds(Set<String> keys) {
        Set<ResourceId> ids = new LinkedHashSet<>();
        for (String key : keys) {
            ids.add(ResourceId.parse(key.toLowerCase(Locale.ROOT)));
        }
        return ids;
    }

    private static Set<ResourceId> idsOf(PackResources pack, Collection<String> keys) {
        Set<ResourceId> ids = new LinkedHashSet<>();
        for (String key : keys) {
            ids.add(idOf(pack, key));
        }
        return ids;
    }

    private static ResourceId idOf(PackResources pack, String key) {
        return key.indexOf(NAMESPACE_SEPARATOR) >= 0 ? ResourceId.parse(key.toLowerCase(Locale.ROOT)) : pack.idOf(key);
    }

    /** The locator's key of {@code structure}: the placed key with that id, else the id as written. */
    private static String keyOf(PackResources pack, Collection<String> placedKeys, ResourceId structure) {
        for (String key : placedKeys) {
            if (idOf(pack, key).equals(structure)) {
                return key;
            }
        }
        return structure.toString();
    }
}
