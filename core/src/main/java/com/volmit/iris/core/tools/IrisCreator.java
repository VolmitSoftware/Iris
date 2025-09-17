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

package com.volmit.iris.core.tools;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import static com.volmit.iris.util.misc.ServerProperties.BUKKIT_YML;

/**
 * Makes it a lot easier to setup an engine, world, studio or whatever
 */
@Data
@Accessors(fluent = true, chain = true)
public class IrisCreator {
    /**
     * Specify an area to pregenerate during creation
     */
    private PregenTask pregen;
    /**
     * Specify a sender to get updates & progress info + tp when world is created.
     */
    private VolmitSender sender;
    /**
     * The seed to use for this generator
     */
    private long seed = 1337;
    /**
     * The dimension to use. This can be any online dimension, or a dimension in the
     * packs folder
     */
    private String dimension = IrisSettings.get().getGenerator().getDefaultWorldType();
    /**
     * The name of this world.
     */
    private String name = "irisworld";
    /**
     * Studio mode makes the engine hotloadable and uses the dimension in
     * your Iris/packs folder instead of copying the dimension files into
     * the world itself. Studio worlds are deleted when they are unloaded.
     */
    private boolean studio = false;
    /**
     * Benchmark mode
     */
    private boolean benchmark = false;

    public static boolean removeFromBukkitYml(String name) throws IOException {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        ConfigurationSection section = yml.getConfigurationSection("worlds");
        if (section == null) {
            return false;
        }
        section.set(name, null);
        if (section.getValues(false).keySet().stream().noneMatch(k -> section.get(k) != null)) {
            yml.set("worlds", null);
        }
        yml.save(BUKKIT_YML);
        return true;
    }
    public static boolean worldLoaded(){
        return true;
    }

    /**
     * Create the IrisAccess (contains the world)
     *
     * @return the IrisAccess
     * @throws IrisException shit happens
     */

    public World create() throws IrisException {
        if (Bukkit.isPrimaryThread()) {
            throw new IrisException("You cannot invoke create() on the main thread.");
        }

        IrisDimension d = IrisToolbelt.getDimension(dimension());

        if (d == null) {
            throw new IrisException("Dimension cannot be found null for id " + dimension());
        }

        if (sender == null)
            sender = Iris.getSender();

        if (!studio() || benchmark) {
            Iris.service(StudioSVC.class).installIntoWorld(sender, d.getLoadKey(), new File(Bukkit.getWorldContainer(), name()));
        }

        AtomicDouble pp = new AtomicDouble(0);
        O<Boolean> done = new O<>();
        done.set(false);
        WorldCreator wc = new IrisWorldCreator()
                .dimension(dimension)
                .name(name)
                .seed(seed)
                .studio(studio)
                .create();
        if (ServerConfigurator.installDataPacks(true)) {
            throw new IrisException("Datapacks were missing!");
        }

        PlatformChunkGenerator access = (PlatformChunkGenerator) wc.generator();
        if (access == null) throw new IrisException("Access is null. Something bad happened.");

        J.a(() -> {
            IntSupplier g = () -> {
                if (access.getEngine() == null) {
                    return 0;
                }
                return access.getEngine().getGenerated();
            };
            if(!benchmark) {
                int req = access.getSpawnChunks().join();
                for (int c = 0; c < req && !done.get(); c = g.getAsInt()) {
                    double v = (double) c / req;
                    if (sender.isPlayer()) {
                        sender.sendProgress(v, "Generating");
                        J.sleep(16);
                    } else {
                        sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - c) + " Left)")));
                        J.sleep(1000);
                    }
                }
            }
        });


        World world;
        try {
            world = J.sfut(() -> INMS.get().createWorld(wc)).get();
        } catch (Throwable e) {
            done.set(true);
            throw new IrisException("Failed to create world!", e);
        }

        done.set(true);

        if (sender.isPlayer() && !benchmark) {
            J.s(() -> sender.player().teleport(new Location(world, 0, world.getHighestBlockYAt(0, 0) + 1, 0)));
        }

        if (studio || benchmark) {
            J.s(() -> {
                Iris.linkMultiverseCore.removeFromConfig(world);

                if (IrisSettings.get().getStudio().isDisableTimeAndWeather()) {
                    world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.setTime(6000);
                }
            });
        } else {
            addToBukkitYml();
            J.s(() -> Iris.linkMultiverseCore.updateWorld(world, dimension));
        }

        if (pregen != null) {
            CompletableFuture<Boolean> ff = new CompletableFuture<>();

            IrisToolbelt.pregenerate(pregen, access)
                    .onProgress(pp::set)
                    .whenDone(() -> ff.complete(true));

            try {
                AtomicBoolean dx = new AtomicBoolean(false);

                J.a(() -> {
                    while (!dx.get()) {
                        if (sender.isPlayer()) {
                            sender.sendProgress(pp.get(), "Pregenerating");
                            J.sleep(16);
                        } else {
                            sender.sendMessage(C.WHITE + "Pregenerating " + Form.pc(pp.get()));
                            J.sleep(1000);
                        }
                    }
                });

                ff.get();
                dx.set(true);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return world;
    }

    private void addToBukkitYml() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(BUKKIT_YML);
        String gen = "Iris:" + dimension;
        ConfigurationSection section = yml.contains("worlds") ? yml.getConfigurationSection("worlds") : yml.createSection("worlds");
        if (!section.contains(name)) {
            section.createSection(name).set("generator", gen);
            try {
                yml.save(BUKKIT_YML);
                Iris.info("Registered \"" + name + "\" in bukkit.yml");
            } catch (IOException e) {
                Iris.error("Failed to update bukkit.yml!");
                e.printStackTrace();
            }
        }
    }
}
