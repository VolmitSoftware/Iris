package com.volmit.iris.core.tools;


import com.volmit.iris.Iris;
import com.volmit.iris.core.pregenerator.IrisPregenerator;
import com.volmit.iris.core.pregenerator.LazyPregenerator;
import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.Position2;

import com.volmit.iris.util.scheduling.J;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.volmit.iris.core.commands.CommandIris.BenchDimension;


public class IrisPackBenchmarking {
    public static boolean loaded = false;
    public static boolean benchmark = false;
    static boolean cancelled = false;
    static boolean pregenInProgress = false;
    static long startTime;
    static long totalChunks;
    static long generatedChunks;
    static double elapsedTimeNs;

    public static void runBenchmark() {
        // IrisPackBenchmarking IrisPackBenchmarking = new IrisPackBenchmarking();
        benchmark = true;
        Iris.info(C.BLUE + "Benchmarking Dimension: " + C.AQUA + BenchDimension);
        //progress();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            Iris.info(C.GOLD + "Setting everything up..");
            try {
                String BenchmarkFolder = "\\Benchmark";
                File folder = new File(BenchmarkFolder);
                if (folder.exists() && folder.isDirectory()) {
                    FileUtils.deleteDirectory(folder);
                    Iris.debug("Deleted old Benchmark");
                } else {
                    Iris.info(C.GOLD + "Old Benchmark not found!");
                    if(folder.exists()){
                        Iris.info(C.RED + "FAILED To remove old Benchmark!");
                        //cancelled = true;

                    }
                }
            } catch (Exception e) {
                throw new RuntimeException();
            }

        }).thenRun(() -> {
            Iris.info(C.GOLD + "Creating Benchmark Environment");
            createBenchmark();

        }).thenRun(() -> {
            Iris.info( C.BLUE + "Benchmark Started!");
            boolean done = false;
            startBenchmarkTimer();
            startBenchmark();
            basicScheduler();
        }).thenRun(() -> {

        });
       // cancelled = future.cancel(true);
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private static void results(){
        double averageCps = calculateAverageCPS();
        Iris.info("Benchmark Dimension: " + BenchDimension);
        Iris.info("Speeds");
        Iris.info("- Average CPS: " + roundToTwoDecimalPlaces(averageCps));
        Iris.info("Duration: " +  roundToTwoDecimalPlaces(elapsedTimeNs));

    }
    private static void basicScheduler() {
        while (true) {
            totalChunks = IrisPregenerator.getLongTotalChunks();
            generatedChunks = IrisPregenerator.getLongGeneratedChunks();
            if(totalChunks > 0) {
                if (generatedChunks >= totalChunks) {
                    Iris.info("Benchmark Completed!");
                    elapsedTimeNs = stopBenchmarkTimer();
                    results();
                    break;
                }
            }
            //J.sleep(100); test
        }
    }
     static void createBenchmark(){
        try {
            IrisToolbelt.createWorld()
                    .dimension(BenchDimension)
                    .name("Benchmark")
                    .seed(1337)
                    .studio(false)
                    .create();
        } catch (IrisException e) {
            throw new RuntimeException(e);
        }
    }
     static void startBenchmark(){
        int x = 0;
        int z = 0;
            IrisToolbelt.pregenerate(PregenTask
                    .builder()
                    .center(new Position2(x, z))
                    .width(5)
                    .height(5)
                    .build(), Bukkit.getWorld("Benchmark")
            );
    }
    static void startLazyBenchmark(){
        int x = 0;
        int z = 0;
        LazyPregenerator.LazyPregenJob pregenJob = LazyPregenerator.LazyPregenJob.builder()
                //.world("Benchmark")
                .healingPosition(0)
                .healing(false)
                .chunksPerMinute(3200)
                .radiusBlocks(5000)
                .position(0)
                .build();

        LazyPregenerator pregenerator = new LazyPregenerator(pregenJob, new File("plugins/Iris/lazygen.json"));
        pregenerator.start();
    }
    public static double calculateAverageCPS() {
        double elapsedTimeSec = elapsedTimeNs / 1_000_000_000.0;  // Convert to seconds
        return generatedChunks / elapsedTimeSec;
    }

    private static void startBenchmarkTimer() {
        startTime = System.nanoTime();
    }

    private static double stopBenchmarkTimer() {
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1_000_000_000.0;
    }

    public static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if(files != null) {
            for(File file: files) {
                if(file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
    private static double roundToTwoDecimalPlaces(double value) {
        return Double.parseDouble(String.format("%.2f", value));
    }
}