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

package art.arcane.iris.world;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitEnvironment;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class IrisWorldCreator {
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private IrisDimension dimension;
    private long seed = 1337;
    private boolean persistent;
    private File studioPackSource;

    public IrisWorldCreator() {

    }

    public IrisWorldCreator dimension(String loadKey) {
        this.dimensionName = loadKey;
        this.dimension = null;
        return this;
    }

    public IrisWorldCreator dimension(IrisDimension dimension) {
        this.dimension = dimension;
        this.dimensionName = dimension.getLoadKey();
        return this;
    }

    public IrisWorldCreator name(String name) {
        this.name = name;
        return this;
    }

    public IrisWorldCreator seed(long seed) {
        this.seed = seed;
        return this;
    }

    public IrisWorldCreator studioMode() {
        this.studio = true;
        return this;
    }

    public IrisWorldCreator productionMode() {
        this.studio = false;
        return this;
    }

    public IrisWorldCreator persistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    public IrisWorldCreator studioPackSource(File source) {
        this.studioPackSource = Objects.requireNonNull(source, "Studio authoring source");
        return this;
    }

    public WorldCreator create() {
        IrisDimension dim = dimension == null ? IrisData.loadAnyDimension(dimensionName, null) : dimension;
        NamespacedKey worldKey = IrisWorldStorage.keyFromName(name);
        World.Environment environment = findEnvironment();
        WorldCreator creator = persistent
                ? WorldCreatorCompat.ofPersistentKey(worldKey)
                : WorldCreatorCompat.ofKey(worldKey);
        File worldFolder = persistent
                ? WorldCreatorCompat.persistentDimensionRoot(worldKey)
                : IrisWorldStorage.dimensionRoot(worldKey);

        IrisWorld w = IrisWorld.builder()
                .platformIdentity(worldKey.toString())
                .name(creator.name())
                .minHeight(dim.getMinHeight())
                .maxHeight(dim.getMaxHeight())
                .seed(seed)
                .worldFolder(worldFolder)
                .build();
        GenerationHistory generationHistory = persistent || studio
                ? requireGenerationHistory(w.worldFolder(), seed)
                : null;
        File packRoot = studio
                ? Objects.requireNonNull(studioPackSource, "Studio authoring source")
                : generationHistory != null
                ? requireActivePack(generationHistory)
                : new File(w.worldFolder(), "iris/pack");
        ChunkGenerator g = new BukkitChunkGenerator(
                w,
                studio,
                packRoot,
                dimensionName,
                generationHistory);

        return creator.environment(environment)
                .generateStructures(true)
                .generator(g).seed(seed);
    }

    private static GenerationHistory requireGenerationHistory(File dimensionRoot, long seed) {
        try {
            return GenerationHistory.open(dimensionRoot.toPath(), seed);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris generation history is unusable at " + dimensionRoot + ".", failure);
        }
    }

    private static File requireActivePack(GenerationHistory history) {
        try {
            return history.activePackRoot().toFile();
        } catch (IOException failure) {
            throw new IllegalStateException("Iris active generation pack is unusable.", failure);
        }
    }

    private World.Environment findEnvironment() {
        IrisDimension dim = dimension == null ? IrisData.loadAnyDimension(dimensionName, null) : dimension;
        return BukkitEnvironment.from(dim == null ? null : dim.getEnvironment());
    }

    public IrisWorldCreator studio(boolean studio) {
        this.studio = studio;
        return this;
    }
}
