/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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


import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.core.pregenerator.methods.HeadlessPregenMethod;
import com.volmit.iris.core.pregenerator.methods.HybridPregenMethod;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineTarget;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.server.pregen.CloudMethod;
import com.volmit.iris.server.pregen.CloudTask;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Getter;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collections;


public class IrisPackBenchmarking {
    @Getter
    public static IrisPackBenchmarking instance;
    public static boolean benchmarkInProgress = false;
    private final boolean headless;
    private final boolean gui;
    PrecisionStopwatch stopwatch;
    private IrisDimension IrisDimension;
    private int radius;
    private boolean finished = false;
    private Engine engine;
    private String address;

    public IrisPackBenchmarking(IrisDimension dimension, String address, int r, boolean headless, boolean gui) {
        instance = this;
        this.IrisDimension = dimension;
        this.address = address;
        this.radius = r;
        this.headless = headless;
        this.gui = gui;
    }

    public void runBenchmark() {
        this.stopwatch = new PrecisionStopwatch();
        new Thread(() -> {
            Iris.info("Setting up benchmark environment ");
            benchmarkInProgress = true;
            File file = new File(Bukkit.getWorldContainer(), "benchmark");
            if (file.exists()) {
                deleteDirectory(file.toPath());
            }
            engine = createBenchmark();
            while (!headless && !IrisToolbelt.isIrisWorld(Bukkit.getWorld("benchmark"))) {
                J.sleep(1000);
                Iris.debug("Iris PackBenchmark: Waiting...");
            }
            Iris.info("Starting Benchmark!");
            stopwatch.begin();
            try {
                if (address != null && !address.isBlank())
                    startCloudBenchmark();
                else startBenchmark();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, "PackBenchmarking").start();
    }

    public boolean getBenchmarkInProgress() {
        return benchmarkInProgress;
    }

    public void finishedBenchmark(KList<Integer> cps) {
        try {
            String time = Form.duration(stopwatch.getMillis());
            Iris.info("-----------------");
            Iris.info("Results:");
            Iris.info("- Total time: " + time);
            Iris.info("- Average CPS: " + calculateAverage(cps));
            Iris.info("  - Median CPS: " + calculateMedian(cps));
            Iris.info("  - Highest CPS: " + findHighest(cps));
            Iris.info("  - Lowest CPS: " + findLowest(cps));
            Iris.info("-----------------");
            Iris.info("Creating a report..");
            File profilers = new File("plugins" + File.separator + "Iris" + File.separator + "packbenchmarks");
            profilers.mkdir();

            File results = new File(profilers, IrisDimension.getName() + " " + LocalDateTime.now(Clock.systemDefaultZone()).toString().replace(':', '-') + ".txt");
            results.getParentFile().mkdirs();
            KMap<String, Double> metrics = engine.getMetrics().pull();
            try (FileWriter writer = new FileWriter(results)) {
                writer.write("-----------------\n");
                writer.write("Results:\n");
                writer.write("Dimension: " + IrisDimension.getName() + "\n");
                writer.write("- Date of Benchmark: " + LocalDateTime.now(Clock.systemDefaultZone()) + "\n");
                writer.write("\n");
                writer.write("Metrics");
                for (String m : metrics.k()) {
                    double i = metrics.get(m);
                    writer.write("- " + m + ": " + i);
                }
                writer.write("- " + metrics);
                writer.write("Benchmark: " + LocalDateTime.now(Clock.systemDefaultZone()) + "\n");
                writer.write("- Total time: " + time + "\n");
                writer.write("- Average CPS: " + calculateAverage(cps) + "\n");
                writer.write("  - Median CPS: " + calculateMedian(cps) + "\n");
                writer.write("  - Highest CPS: " + findHighest(cps) + "\n");
                writer.write("  - Lowest CPS: " + findLowest(cps) + "\n");
                writer.write("-----------------\n");
                Iris.info("Finished generating a report!");
            } catch (IOException e) {
                Iris.error("An error occurred writing to the file.");
                e.printStackTrace();
            }

            if (headless) engine.close();
            else J.s(() -> Bukkit.unloadWorld("benchmark", true));

            stopwatch.end();
        } catch (Exception e) {
            Iris.error("Something has gone wrong!");
            e.printStackTrace();
        }
    }

    private Engine createBenchmark() {
        try {
            if (headless) {
                Iris.info("Using headless benchmark!");
                IrisWorld world = IrisWorld.builder()
                        .name("benchmark")
                        .minHeight(IrisDimension.getMinHeight())
                        .maxHeight(IrisDimension.getMaxHeight())
                        .seed(1337)
                        .worldFolder(new File(Bukkit.getWorldContainer(), "benchmark"))
                        .environment(IrisDimension.getEnvironment())
                        .build();
                Iris.service(StudioSVC.class).installIntoWorld(
                        Iris.getSender(),
                        IrisDimension.getLoadKey(),
                        world.worldFolder());
                var data = IrisData.get(new File(world.worldFolder(), "iris/pack"));
                var dim = data.getDimensionLoader().load(IrisDimension.getLoadKey());
                return new IrisEngine(new EngineTarget(world, dim, data), false);
            }
            Iris.info("Using Standard benchmark!");
            return IrisToolbelt.access(IrisToolbelt.createWorld()
                    .dimension(IrisDimension.getLoadKey())
                    .name("benchmark")
                    .seed(1337)
                    .studio(false)
                    .benchmark(true)
                    .create()).getEngine();
        } catch (IrisException e) {
            throw new RuntimeException(e);
        }
    }

    private void startBenchmark() {
        int x = 0;
        int z = 0;
        IrisToolbelt.pregenerate(PregenTask
                .builder()
                .gui(gui)
                .center(new Position2(x, z))
                .width(radius)
                .height(radius)
                .build(), headless ? new HeadlessPregenMethod(engine) : new HybridPregenMethod(engine.getWorld().realWorld(),
                IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())), engine);
    }

    private void startCloudBenchmark() throws InterruptedException {
        int x = 0;
        int z = 0;
        IrisToolbelt.pregenerate(CloudTask
                .couldBuilder()
                .gui(gui)
                .center(new Position2(x, z))
                .width(radius)
                .height(radius)
                .distance(engine.getMantle().getRadius() * 2)
                .build(), new CloudMethod(address, engine), engine);
    }

    private double calculateAverage(KList<Integer> list) {
        double sum = 0;
        for (int num : list) {
            sum += num;
        }
        return sum / list.size();
    }

    private double calculateMedian(KList<Integer> list) {
        Collections.sort(list);
        int middle = list.size() / 2;

        if (list.size() % 2 == 1) {
            return list.get(middle);
        } else {
            return (list.get(middle - 1) + list.get(middle)) / 2.0;
        }
    }

    private int findLowest(KList<Integer> list) {
        return Collections.min(list);
    }

    private int findHighest(KList<Integer> list) {
        return Collections.max(list);
    }

    private boolean deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}