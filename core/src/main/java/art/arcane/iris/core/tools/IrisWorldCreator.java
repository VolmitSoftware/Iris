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

package art.arcane.iris.core.tools;

import art.arcane.iris.core.IrisWorldStorage;
import art.arcane.iris.core.WorldCreatorCompat;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.platform.BukkitChunkGenerator;
import art.arcane.iris.platform.bukkit.BukkitEnvironment;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;

public class IrisWorldCreator {
    private String name;
    private boolean studio = false;
    private String dimensionName = null;
    private IrisDimension dimension;
    private long seed = 1337;
    private boolean persistent;

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
        ChunkGenerator g = new BukkitChunkGenerator(w, studio, studio
                ? dim.getLoader().getDataFolder() :
                new File(w.worldFolder(), "iris/pack"), dimensionName);

        return creator.environment(environment)
                .generateStructures(true)
                .generator(g).seed(seed);
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
