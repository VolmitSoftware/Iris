/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.engine.platform.HeadlessGenerator;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.File;

@Data
@SuppressWarnings("ResultOfMethodCallIgnored")
public class HeadlessWorld {
    private final IrisDimension dimension;
    private final String worldName;
    private final IrisWorld world;
    private boolean studio;

    public HeadlessWorld(String worldName, IrisDimension dimension, long seed) {
        this(worldName, dimension, seed, false);
    }

    public HeadlessWorld(String worldName, IrisDimension dimension, long seed, boolean studio) {
        this.worldName = worldName;
        this.dimension = dimension;
        this.studio = studio;
        world = IrisWorld.builder()
                .environment(dimension.getEnvironment())
                .worldFolder(new File(worldName))
                .seed(seed)
                .maxHeight(256)
                .minHeight(0)
                .name(worldName)
                .build();
        world.worldFolder().mkdirs();
        new File(world.worldFolder(), "region").mkdirs();

        if (!studio && !new File(world.worldFolder(), "iris/pack").exists()) {
            Iris.service(StudioSVC.class).installIntoWorld(new VolmitSender(Bukkit.getConsoleSender(), Iris.instance.getTag("Headless")), dimension.getLoadKey(), world.worldFolder());
        }
    }

    @SuppressWarnings("ConstantConditions")
    public HeadlessGenerator generate() {
        Engine e = null;

        if (getWorld().tryGetRealWorld()) {
            if (IrisToolbelt.isIrisWorld(getWorld().realWorld())) {
                e = IrisToolbelt.access(getWorld().realWorld()).getEngine();
            }
        }

        if (e != null) {
            Iris.info("Using Existing Engine " + getWorld().name() + " for Headless Pregeneration.");
        }

        return e != null ? new HeadlessGenerator(this, e) : new HeadlessGenerator(this);
    }

    public World load() {
        World w = new WorldCreator(worldName)
                .environment(dimension.getEnvironment())
                .seed(world.seed())
                .generator(new BukkitChunkGenerator(world, studio, dimension.getLoader().getDataFolder(),
                        dimension.getLoadKey()))
                .createWorld();
        world.realWorld(w);
        return w;
    }

    public static HeadlessWorld from(World world) {
        return new HeadlessWorld(world.getName(), IrisToolbelt.access(world)
                .getEngine().getTarget().getDimension(), world.getSeed());
    }

    public static HeadlessWorld from(String name, String dimension, long seed) {
        return new HeadlessWorld(name, IrisData.loadAnyDimension(dimension), seed);
    }
}
