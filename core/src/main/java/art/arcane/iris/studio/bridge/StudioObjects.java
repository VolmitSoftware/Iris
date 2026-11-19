/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.studio.map.ObjectSource;
import art.arcane.iris.studio.map.PackObjectSource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** The studio's view of the pack's object files, as block scenes. */
final class StudioObjects implements StudioProfiles.ProfileView {
    static final String OBJECTS = "iris:objects";

    private final String id;
    private final String label;
    private final String description;
    private final ObjectSource source;

    private StudioObjects(String id, String label, String description, ObjectSource source) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.source = source;
    }

    static StudioObjects packObjects() {
        return new StudioObjects(OBJECTS, "Objects", "Every object in the pack in 3D, with the blocks it is made of", new PackObjectSource());
    }

    /** One timed bake of a procedural entry, as the studio's bake meter reads it. */
    record BakeTiming(int variantCount, List<Stage> stages) {
        record Stage(String label, long nanos) {
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public StudioProfiles.Sizes sizes() {
        return null;
    }

    @Override
    public boolean hasForm() {
        return false;
    }

    @Override
    public List<StudioProfiles.Entry> entries(Engine engine) {
        IrisData data = engine.getData();
        return source.keys(data).stream()
                .sorted(Comparator.naturalOrder())
                .map(key -> new StudioProfiles.Entry(key, nameOf(key), folderOf(key), Math.max(1, source.variants(data, key).size())))
                .toList();
    }

    @Override
    public ProfileForm.Form form(Engine engine, String key, Map<String, String> overrides) {
        throw new NoSuchElementException(id + " has no settings form");
    }

    @Override
    public SceneBlocks scene(Engine engine, String key, int variant, int size, Map<String, String> overrides) {
        IrisData data = engine.getData();
        source.keys(data);
        List<IrisObject> variants = source.variants(data, key);
        if (variants.isEmpty()) {
            throw new NoSuchElementException("No object " + key);
        }
        return StudioProfiles.sceneOf(variants.get(Math.floorMod(variant, variants.size())));
    }

    private static String nameOf(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    private static String folderOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? "" : key.substring(0, slash);
    }
}
