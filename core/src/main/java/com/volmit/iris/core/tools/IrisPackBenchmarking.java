package com.volmit.iris.core.tools;


import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.Form;
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
    private final PrecisionStopwatch stopwatch = new PrecisionStopwatch();
    private final IrisDimension dimension;
    private final int radius;
    private final boolean gui;

    public IrisPackBenchmarking(IrisDimension dimension, int radius, boolean gui) {
        instance = this;
        this.dimension = dimension;
        this.radius = radius;
        this.gui = gui;
        runBenchmark();
    }

    private void runBenchmark() {
        Thread.ofVirtual()
                .name("PackBenchmarking")
                .start(() -> {
                    Iris.info("Setting up benchmark environment ");
                    benchmarkInProgress = true;
                    File file = new File("benchmark");
                    if (file.exists()) {
                        deleteDirectory(file.toPath());
                    }
                    createBenchmark();
                    while (!IrisToolbelt.isIrisWorld(Bukkit.getWorld("benchmark"))) {
                        J.sleep(1000);
                        Iris.debug("Iris PackBenchmark: Waiting...");
                    }
                    Iris.info("Starting Benchmark!");
                    stopwatch.begin();
                    startBenchmark();
                });

    }

    public boolean getBenchmarkInProgress() {
        return benchmarkInProgress;
    }

    public void finishedBenchmark(KList<Integer> cps) {
        try {
            String time = Form.duration(stopwatch.getMillis());
            Engine engine = IrisToolbelt.access(Bukkit.getWorld("benchmark")).getEngine();
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

            File results = new File(profilers, dimension.getName() + " " + LocalDateTime.now(Clock.systemDefaultZone()).toString().replace(':', '-') + ".txt");
            results.getParentFile().mkdirs();
            KMap<String, Double> metrics = engine.getMetrics().pull();
            try (FileWriter writer = new FileWriter(results)) {
                writer.write("-----------------\n");
                writer.write("Results:\n");
                writer.write("Dimension: " + dimension.getName() + "\n");
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

            J.s(() -> {
                var world = Bukkit.getWorld("benchmark");
                if (world == null) return;
                IrisToolbelt.evacuate(world);
                Bukkit.unloadWorld(world, true);
            });

            stopwatch.end();
        } catch (Exception e) {
            Iris.error("Something has gone wrong!");
            e.printStackTrace();
        }
    }

    private void createBenchmark() {
        try {
            IrisToolbelt.createWorld()
                    .dimension(dimension.getLoadKey())
                    .name("benchmark")
                    .seed(1337)
                    .studio(false)
                    .benchmark(true)
                    .create();
        } catch (IrisException e) {
            throw new RuntimeException(e);
        }
    }

    private void startBenchmark() {
        IrisToolbelt.pregenerate(PregenTask
                .builder()
                .gui(gui)
                .width(radius)
                .height(radius)
                .build(), Bukkit.getWorld("benchmark")
        );
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
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
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