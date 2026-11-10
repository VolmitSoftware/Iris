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

package art.arcane.iris.core.studio.scene;

import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.IrisPlatformRegistryIds;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.config.Reference;
import com.volmit.resource.ResourceId;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

/** What the studio offers while an author types a setting that names something: the platform's entries of a reference, or the pack's resources of a kind. */
public final class StudioSuggestions {
    private StudioSuggestions() {
    }

    /**
     * The ids of the platform's registry {@code referenceName} names, sorted.
     *
     * @throws NoSuchElementException when there is no such reference
     */
    public static List<String> ofReference(String referenceName) {
        Reference reference;
        try {
            reference = Reference.valueOf(referenceName);
        } catch (IllegalArgumentException unknown) {
            throw new NoSuchElementException("No reference named " + referenceName);
        }
        return IrisPlatformRegistryIds.ofBoundPlatform()
                .map(registries -> sortedKeys(registries.ids(reference), null))
                .orElse(List.of());
    }

    /**
     * The keys of the pack's resources of the kind {@code kindId} names, as the pack writes them, sorted.
     *
     * @throws NoSuchElementException when the pack has no such kind
     */
    public static List<String> ofResource(Engine engine, String kindId) {
        IrisPackResources resources = engine.getData().getPackResources();
        try {
            return sortedKeys(resources.ids(ResourceId.parse(kindId)), resources.packId().namespace());
        } catch (IllegalArgumentException unknown) {
            throw new NoSuchElementException(unknown.getMessage());
        }
    }

    /** {@code ids} as written, sorted: a resource of the pack's own namespace by its path, any other id in full. */
    static List<String> sortedKeys(Collection<ResourceId> ids, String packNamespace) {
        return ids.stream()
                .map(id -> id.namespace().equals(packNamespace) ? id.path() : id.toString())
                .distinct()
                .sorted()
                .toList();
    }
}
