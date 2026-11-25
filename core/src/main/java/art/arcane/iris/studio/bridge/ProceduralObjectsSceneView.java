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
import art.arcane.iris.studio.map.BakeRun;
import art.arcane.iris.studio.map.ObjectSource;
import art.arcane.iris.studio.map.ProceduralObjectSource;
import art.arcane.iris.studio.scene.SceneBakeTiming;
import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.iris.studio.scene.SceneView;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalLong;

/** The trees, rock formations and more that the pack generates from settings, every variant in 3D. */
final class ProceduralObjectsSceneView implements SceneView {
    static final String ID = "iris:objects/procedural";
    private static final String BAKE_STAGE = "Bake";
    private static final char FOLDER_SEPARATOR = '/';

    private final ObjectSource source = new ProceduralObjectSource();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Procedural";
    }

    @Override
    public String description() {
        return "Trees, rock formations and more that Iris generates from settings, every variant in 3D";
    }

    @Override
    public boolean isBaked() {
        return true;
    }

    @Override
    public List<SceneEntry> entries(Engine engine) {
        IrisData data = engine.getData();
        return source.keys(data).stream()
                .sorted()
                .map(key -> entryOf(key, source.variants(data, key).size()))
                .toList();
    }

    @Override
    public SceneBlocks scene(Engine engine, SceneRequest request) {
        IrisData data = engine.getData();
        source.keys(data);
        List<IrisObject> variants = source.variants(data, request.key());
        if (variants.isEmpty()) {
            throw new NoSuchElementException("No procedural object " + request.key());
        }
        return SceneBlocks.of(variants.get(Math.floorMod(request.variant(), variants.size())));
    }

    @Override
    public SceneBakeTiming timeBake(Engine engine, String key) {
        IrisData data = engine.getData();
        source.keys(data);
        OptionalLong seed = source.seed(key);
        BakeRun run = seed.isEmpty() ? null : source.timeBake(data, key, seed.getAsLong());
        if (run == null) {
            throw new NoSuchElementException("No procedural object " + key);
        }
        return new SceneBakeTiming(run.variantCount(), List.of(new SceneBakeTiming.Stage(BAKE_STAGE, run.totalNanos())));
    }

    private static SceneEntry entryOf(String key, int variantCount) {
        int lastSeparator = key.lastIndexOf(FOLDER_SEPARATOR);
        String folder = lastSeparator < 0 ? "" : key.substring(0, lastSeparator);
        return new SceneEntry(key, key.substring(lastSeparator + 1), folder, Math.max(1, variantCount));
    }
}
