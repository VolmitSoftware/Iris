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

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.headless.HeadlessWorld;
import com.volmit.iris.engine.object.IrisDimension;
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
import org.bukkit.WorldCreator;

import java.util.concurrent.CompletableFuture;
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
     * Headless mode allows Iris to generate / query engine information
     * without needing an actual world loaded. This is normally only used
     * for pregeneration purposes but it could be used for mapping.
     */
    private boolean headless = false;

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
    public IrisAccess create() throws IrisException {
        IrisDimension d = IrisToolbelt.getDimension(dimension());
        IrisAccess access = null;
        Consumer<Double> prog = (pxx) -> {
            double px = pxx;

            if (pregen != null && !headless) {
                px = (px / 2) + 0.5;
            }

            if (sender != null) {
                if (sender.isPlayer()) {
                    sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generating " + Form.pc(px)));
                } else {
                    sender.sendMessage("Generating " + Form.f(px, 0));
                }
            }
        };

        if (d == null) {
            throw new MissingDimensionException("Cannot find dimension '" + dimension() + "'");
        }

        if (headless) {
            HeadlessWorld w = new HeadlessWorld(name, d, seed, studio);
            access = w.generate().getGenerator();
        } else {
            O<Boolean> done = new O<>();
            done.set(false);
            WorldCreator wc = new IrisWorldCreator()
                    .dimension(dimension)
                    .name(name)
                    .seed(seed)
                    .studio(studio)
                    .create();
            access = (IrisAccess) wc.generator();
            IrisAccess finalAccess1 = access;

            J.a(() ->
            {
                int req = 400;

                while (finalAccess1.getGenerated() < req && !done.get()) {
                    double v = (double) finalAccess1.getGenerated() / (double) req;

                    if (pregen != null) {
                        v /= 2;
                    }

                    if (sender.isPlayer()) {
                        sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - finalAccess1.getGenerated()) + " Left)"))));
                        J.sleep(50);
                    } else {
                        sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - finalAccess1.getGenerated()) + " Left)")));
                        J.sleep(1000);
                    }

                    if (finalAccess1.isFailing()) {

                        sender.sendMessage("Generation Failed!");
                        break;
                    }
                }

                sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generation Complete"));
            });

            try {
                J.sfut(wc::createWorld).get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        if (access == null) {
            throw new IrisException("Access is null. Something bad happened.");
        }

        CompletableFuture<Boolean> ff = new CompletableFuture<>();

        if (pregen != null) {
            IrisToolbelt.pregenerate(pregen, access)
                    .onProgress(prog)
                    .whenDone(() -> ff.complete(true));

            try {
                ff.get();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        try {

            IrisAccess finalAccess = access;
            J.sfut(() -> {
                if (headless) {
                    O<Boolean> done = new O<>();
                    done.set(false);

                    J.a(() ->
                    {
                        int req = 400;

                        while (finalAccess.getGenerated() < req && !done.get()) {
                            double v = (double) finalAccess.getGenerated() / (double) req;
                            v = (v / 2) + 0.5;

                            if (sender.isPlayer()) {
                                sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - finalAccess.getGenerated()) + " Left)"))));
                                J.sleep(50);
                            } else {
                                sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - finalAccess.getGenerated()) + " Left)")));
                                J.sleep(1000);
                            }

                            if (finalAccess.isFailing()) {

                                sender.sendMessage("Generation Failed!");
                                break;
                            }
                        }

                        sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generation Complete"));
                    });

                    finalAccess.getHeadlessGenerator().getWorld().load();
                    done.set(true);
                }
            }).get();

        } catch (Throwable e) {
            e.printStackTrace();
        }

        return access;
    }
}
