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

package com.volmit.iris.core.tools;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.common.HeadlessWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.platform.HeadlessGenerator;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.exceptions.MissingDimensionException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import lombok.Data;
import lombok.experimental.Accessors;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private long seed = RNG.r.nextLong();

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
     * Create the IrisAccess (contains the world)
     *
     * @return the IrisAccess
     * @throws IrisException shit happens
     */
    public World create() throws IrisException {
        if(Bukkit.isPrimaryThread())
        {
            throw new IrisException("You cannot invoke create() on the main thread.");
        }

        IrisDimension d = IrisToolbelt.getDimension(dimension());

        if(d == null)
        {
            throw new IrisException("Dimension cannot be found null for id " + dimension());
        }

        if(!studio())
        {
            Iris.proj.installIntoWorld(sender, d.getLoadKey(), new File(name()));
        }

        PlatformChunkGenerator access = null;
        AtomicReference<World> world = new AtomicReference<>();
        AtomicDouble pp = new AtomicDouble(0);
        O<Boolean> done = new O<>();
        done.set(false);
        WorldCreator wc = new IrisWorldCreator()
                .dimension(dimension)
                .name(name)
                .seed(seed)
                .studio(studio)
                .create();

        access = (PlatformChunkGenerator) wc.generator();
        PlatformChunkGenerator finalAccess1 = access;

        J.a(() ->
        {
            int req = 441;

            while (finalAccess1.getEngine().getGenerated() < req) {
                double v = (double) finalAccess1.getEngine().getGenerated() / (double) req;

                if (sender.isPlayer()) {
                    sender.sendProgress(v, "Generating");
                    J.sleep(16);
                } else {
                    sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - finalAccess1.getEngine().getGenerated()) + " Left)")));
                    J.sleep(1000);
                }
            }
        });


        try {
            J.sfut(() -> {
                world.set(wc.createWorld());
            }).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (access == null) {
            throw new IrisException("Access is null. Something bad happened.");
        }

        done.set(true);

        if(sender.isPlayer())
        {
            J.s(() -> {
                sender.player().teleport(new Location(world.get(), 0, world.get().getHighestBlockYAt(0, 0), 0));
            });
        }

        if(studio)
        {
            J.s(() -> {
                Iris.linkMultiverseCore.removeFromConfig(world.get());

                if (IrisSettings.get().getStudio().isDisableTimeAndWeather()) {
                    world.get().setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    world.get().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    world.get().setTime(6000);
                }
            });
        }

        if (pregen != null) {
            CompletableFuture<Boolean> ff = new CompletableFuture<>();

            IrisToolbelt.pregenerate(pregen, access)
                    .onProgress(pp::set)
                    .whenDone(() -> ff.complete(true));

            try {
                AtomicBoolean dx = new AtomicBoolean(false);

                J.a(() -> {
                    while(!dx.get())
                    {
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

        return world.get();
    }
}
